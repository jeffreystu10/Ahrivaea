package dev.ahrivaea.map;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * What the server tells each player's map, twice a second: where every player is (in any dimension) and the
 * creatures within {@link #CREATURE_RANGE} blocks of them, with each one's name tag (Ahrivaea creatures' tags give
 * their name, level and influence colour) and whether it's wild, yours or someone else's. Game rules
 * {@code ahrivaea_map:show_players} and {@code ahrivaea_map:show_creatures} turn either off.
 */
public final class Markers {
	private Markers() {}

	public static final int CREATURE_RANGE = 128;
	public static final int MAX_CREATURES = 300;
	public static final int WILD = 0, YOURS = 1, TAMED = 2;

	public static final GameRule<Boolean> SHOW_PLAYERS = GameRuleBuilder.forBoolean(true).category(GameRuleCategory.PLAYER)
			.buildAndRegister(Identifier.fromNamespaceAndPath("ahrivaea_map", "show_players"));
	public static final GameRule<Boolean> SHOW_CREATURES = GameRuleBuilder.forBoolean(true).category(GameRuleCategory.MOBS)
			.buildAndRegister(Identifier.fromNamespaceAndPath("ahrivaea_map", "show_creatures"));

	public record PlayerMark(UUID id, String name, String dimension, int x, int y, int z, float yaw) {}

	public record CreatureMark(int entity, String name, int color, int x, int z, int kind) {}

	public record Payload(List<PlayerMark> players, List<CreatureMark> creatures) implements CustomPacketPayload {
		public static final Type<Payload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("ahrivaea_map", "markers"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Payload> CODEC = StreamCodec.of((buf, p) -> {
			buf.writeVarInt(p.players.size());
			for (PlayerMark m : p.players) {
				buf.writeUUID(m.id);
				buf.writeUtf(m.name);
				buf.writeUtf(m.dimension);
				buf.writeVarInt(m.x);
				buf.writeVarInt(m.y);
				buf.writeVarInt(m.z);
				buf.writeFloat(m.yaw);
			}
			buf.writeVarInt(p.creatures.size());
			for (CreatureMark m : p.creatures) {
				buf.writeVarInt(m.entity);
				buf.writeUtf(m.name);
				buf.writeInt(m.color);
				buf.writeVarInt(m.x);
				buf.writeVarInt(m.z);
				buf.writeByte(m.kind);
			}
		}, buf -> {
			List<PlayerMark> players = new ArrayList<>();
			for (int i = buf.readVarInt(); i > 0; i--)
				players.add(new PlayerMark(buf.readUUID(), buf.readUtf(), buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readFloat()));
			List<CreatureMark> creatures = new ArrayList<>();
			for (int i = buf.readVarInt(); i > 0; i--)
				creatures.add(new CreatureMark(buf.readVarInt(), buf.readUtf(), buf.readInt(), buf.readVarInt(), buf.readVarInt(), buf.readByte()));
			return new Payload(players, creatures);
		});

		@Override
		public Type<Payload> type() {
			return TYPE;
		}
	}

	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(Payload.TYPE, Payload.CODEC);
		ServerTickEvents.END_SERVER_TICK.register(Markers::tick);
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % 10 != 0) return;
		List<ServerPlayer> all = server.getPlayerList().getPlayers();
		List<PlayerMark> players = new ArrayList<>();
		for (ServerPlayer p : all) players.add(mark(p));
		for (ServerPlayer p : all) {
			if (!ServerPlayNetworking.canSend(p, Payload.TYPE)) continue;
			ServerLevel level = p.level();
			List<PlayerMark> shown = new ArrayList<>();
			boolean everyone = level.getGameRules().get(SHOW_PLAYERS);
			for (int i = 0; i < all.size(); i++) {
				ServerPlayer o = all.get(i);
				// yourself always; others unless hidden (spectators and invisible players, unless you're a spectator too)
				if (o == p || everyone && (p.isSpectator() || !o.isSpectator() && !o.isInvisible())) shown.add(players.get(i));
			}
			List<CreatureMark> creatures = level.getGameRules().get(SHOW_CREATURES) ? creaturesNear(level, p) : List.of();
			ServerPlayNetworking.send(p, new Payload(shown, creatures));
		}
	}

	private static PlayerMark mark(ServerPlayer p) {
		return new PlayerMark(p.getUUID(), p.getScoreboardName(), p.level().dimension().identifier().toString(),
				p.getBlockX(), p.getBlockY(), p.getBlockZ(), p.getYRot());
	}

	/** The mobs within range of a player, nearest first. */
	public static List<CreatureMark> creaturesNear(ServerLevel level, ServerPlayer p) {
		AABB box = new AABB(p.getX() - CREATURE_RANGE, level.getMinY(), p.getZ() - CREATURE_RANGE,
				p.getX() + CREATURE_RANGE, level.getMaxY() + 1, p.getZ() + CREATURE_RANGE);
		List<Mob> mobs = level.getEntitiesOfClass(Mob.class, box, m -> m.isAlive() && !m.isInvisible());
		mobs.sort(Comparator.comparingDouble(m -> m.distanceToSqr(p)));
		List<CreatureMark> out = new ArrayList<>();
		for (Mob m : mobs) {
			if (out.size() >= MAX_CREATURES) break;
			Component name = m.hasCustomName() ? m.getCustomName() : m.getType().getDescription();
			TextColor tc = name.getStyle().getColor();
			int color = tc != null ? tc.getValue() : m instanceof Enemy ? 0xE05050 : 0xE8E8E8;
			int kind = m instanceof TamableAnimal t && t.isTame() ? t.isOwnedBy(p) ? YOURS : TAMED : WILD;
			out.add(new CreatureMark(m.getId(), name.getString(), color & 0xFFFFFF, m.getBlockX(), m.getBlockZ(), kind));
		}
		return out;
	}
}
