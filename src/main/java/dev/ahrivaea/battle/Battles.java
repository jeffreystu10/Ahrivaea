package dev.ahrivaea.battle;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import dev.ahrivaea.Ahrivaea;
import dev.ahrivaea.creature.CreatureEntity;
import dev.ahrivaea.creature.Species;
import dev.ahrivaea.party.CaughtCreature;
import dev.ahrivaea.party.Party;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Starts, runs and ends battles, keeps the world out of them, and talks to the battle screen on each
 * player's client (the whole battle state goes over as JSON).
 */
public final class Battles {
	private Battles() {}

	public static final int RADIUS = 10;

	/** Server to client: the battle as it is now (empty JSON object = no battle). */
	public record StatePayload(String json) implements CustomPacketPayload {
		public static final Type<StatePayload> TYPE = new Type<>(Ahrivaea.id("battle_state"));
		public static final StreamCodec<RegistryFriendlyByteBuf, StatePayload> CODEC = StreamCodec.composite(ByteBufCodecs.stringUtf8(1 << 20), StatePayload::json, StatePayload::new);

		@Override
		public Type<StatePayload> type() {
			return TYPE;
		}
	}

	/** Client to server: an order (see {@link Battle.Action}), or "recruit" / "leave" after the battle. */
	public record ActionPayload(String json) implements CustomPacketPayload {
		public static final Type<ActionPayload> TYPE = new Type<>(Ahrivaea.id("battle_action"));
		public static final StreamCodec<RegistryFriendlyByteBuf, ActionPayload> CODEC = StreamCodec.composite(ByteBufCodecs.stringUtf8(4096), ActionPayload::json, ActionPayload::new);

		@Override
		public Type<ActionPayload> type() {
			return TYPE;
		}
	}

	private static final Map<UUID, Battle> ACTIVE = new HashMap<>();
	/** Finished battles whose results screen is still open (for recruiting). */
	private static final Map<UUID, Battle> FINISHED = new HashMap<>();

	public static Battle of(Player p) {
		return ACTIVE.get(p.getUUID());
	}

	public static boolean inBattle(Entity e) {
		for (Battle b : ACTIVE.values()) for (Unit u : b.units) if (e.getUUID().equals(u.entity)) return true;
		return false;
	}

	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(StatePayload.TYPE, StatePayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ActionPayload.TYPE, ActionPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(ActionPayload.TYPE, (payload, ctx) -> handle(ctx.player(), payload.json()));

		// hitting a wild creature starts a battle instead of hurting it
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (!(player instanceof ServerPlayer sp) || !(entity instanceof CreatureEntity c) || c.isTame() || player.isSpectator()) return InteractionResult.PASS;
			if (of(player) != null || inBattle(c)) return InteractionResult.FAIL;
			start(sp, c);
			return InteractionResult.FAIL;
		});
		// nothing outside the battle can hurt its units, and nobody can dig up the battlefield
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !inBattle(entity));
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, be) -> {
			for (Battle b : ACTIVE.values()) {
				if (b.level == level && pos.getX() >= b.grid.originX && pos.getZ() >= b.grid.originZ
						&& pos.getX() < b.grid.originX + b.grid.size && pos.getZ() < b.grid.originZ + b.grid.size) return false;
			}
			return true;
		});
		ServerTickEvents.END_SERVER_TICK.register(Battles::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			Battle b = ACTIVE.get(handler.getPlayer().getUUID());
			if (b != null) {
				b.say(handler.getPlayer().getPlainTextName() + " left.");
				b.outcome = Battle.Outcome.FLED;
				b.phase = Battle.Phase.OVER;
				cleanUp(b);
			}
			FINISHED.remove(handler.getPlayer().getUUID());
		});
	}

	// ---- starting ----

	public static Battle start(ServerPlayer player, CreatureEntity wild) {
		if (ACTIVE.containsKey(player.getUUID())) return null;
		ServerLevel level = (ServerLevel) player.level();
		BlockPos center = BlockPos.containing(player.position().add(wild.position()).scale(0.5));
		BattleGrid grid = BattleGrid.read(level, center, RADIUS);
		List<CaughtCreature> party = Party.get(player);
		Battle b = new Battle(player, grid, !party.isEmpty());

		int px = Math.clamp(player.getBlockX() - grid.originX, 0, grid.size - 1), pz = Math.clamp(player.getBlockZ() - grid.originZ, 0, grid.size - 1);
		int[] pc = b.freeCellNear(px, pz, Unit.Side.PLAYER);
		b.addPlayer(player, pc[0], pc[1]);
		for (int i = 0; i < Math.min(Party.BATTLE_SIZE, party.size()); i++) {
			CaughtCreature c = party.get(i);
			var species = Species.get(c.species());
			if (species.isEmpty()) continue;
			int[] cell = b.freeCellNear(pc[0], pc[1], Unit.Side.PLAYER);
			Unit u = b.addCreature(Unit.Side.PLAYER, species.get(), c.influence(), c.level(), c.health(), i, cell[0], cell[1]);
			BlockPos at = b.cellPos(u);
			CreatureEntity e = Ahrivaea.spawn(level, species.get(), c.influence(), c.level(), u.hp, at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
			if (e != null) {
				e.tame(player);
				e.setNoAi(true);
				u.entity = e.getUUID();
			}
		}

		int wx = Math.clamp(wild.getBlockX() - grid.originX, 0, grid.size - 1), wz = Math.clamp(wild.getBlockZ() - grid.originZ, 0, grid.size - 1);
		var wildSpecies = wild.getSpecies();
		if (wildSpecies.isEmpty()) return null;
		int[] wc = b.freeCellNear(wx, wz, Unit.Side.ENEMY);
		Unit lead = b.addCreature(Unit.Side.ENEMY, wildSpecies.get(), wild.getInfluence(), wild.getCreatureLevel(), wild.getHealth(), -1, wc[0], wc[1]);
		lead.entity = wild.getUUID();
		wild.setNoAi(true);
		// 1-4 more wild creatures of the area join in
		int extra = 1 + b.random.nextInt(4);
		for (int i = 0; i < extra; i++) {
			var pick = Species.pickFor(level.getBiome(wild.blockPosition()), level, b.random);
			Species s = pick.map(Species.Pick::species).orElse(wildSpecies.get());
			var inf = pick.map(Species.Pick::influence).orElse(wild.getInfluence());
			int lvl = Math.clamp(wild.getCreatureLevel() - 2 + b.random.nextInt(5), 0, 50);
			int[] cell = b.freeCellNear(wc[0], wc[1], Unit.Side.ENEMY);
			Unit u = b.addCreature(Unit.Side.ENEMY, s, inf, lvl, -1, -1, cell[0], cell[1]);
			BlockPos at = b.cellPos(u);
			CreatureEntity e = Ahrivaea.spawn(level, s, inf, lvl, -1, at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
			if (e != null) {
				e.setNoAi(true);
				u.entity = e.getUUID();
			}
		}
		for (Unit u : b.units) placeEntity(b, u);
		b.say("A wild " + lead.name + " appears with " + extra + " more! Turn 1: your phase");
		ACTIVE.put(player.getUUID(), b);
		FINISHED.remove(player.getUUID());
		Ahrivaea.LOGGER.info("Battle started for {}: {} units on a {}x{} field at {}", player.getPlainTextName(), b.units.size(), grid.size, grid.size, center.toShortString());
		send(b);
		return b;
	}

	// ---- running ----

	private static void tick(MinecraftServer server) {
		for (Battle b : List.copyOf(ACTIVE.values())) {
			ServerPlayer p = b.player();
			if (p == null) continue;
			// the player's body stays on its square
			Unit me = b.playerUnit();
			if (me != null && me.alive()) {
				BlockPos c = b.cellPos(me);
				if (p.position().distanceToSqr(c.getX() + 0.5, c.getY(), c.getZ() + 0.5) > 0.04) p.connection.teleport(c.getX() + 0.5, c.getY(), c.getZ() + 0.5, p.getYRot(), p.getXRot());
				p.setDeltaMovement(0, 0, 0);
			}
			if (b.phase == Battle.Phase.ENEMY && --b.enemyDelay <= 0) {
				b.enemyStep();
				b.enemyDelay = 15;
				send(b);
			}
		}
	}

	static void placeEntity(Battle b, Unit u) {
		if (u.isPlayer) return; // handled every tick
		Entity e = b.level.getEntity(u.entity);
		if (e == null) return;
		BlockPos c = b.cellPos(u);
		e.snapTo(c.getX() + 0.5, c.getY(), c.getZ() + 0.5);
		e.setDeltaMovement(0, 0, 0);
	}

	static void removeEntity(Battle b, Unit u) {
		if (u.isPlayer) return;
		Entity e = b.level.getEntity(u.entity);
		if (e != null) e.discard();
	}

	/** Ends a battle: party HP saved, the player's creatures go back to the party, survivors go back to the wild. */
	static void cleanUp(Battle b) {
		ACTIVE.remove(b.playerId);
		ServerPlayer p = b.player();
		if (p != null) b.saveParty(p);
		for (Unit u : b.units) {
			if (u.isPlayer || !u.alive()) continue;
			Entity e = b.level.getEntity(u.entity);
			if (e == null) continue;
			if (u.side == Unit.Side.PLAYER) e.discard();
			else if (e instanceof CreatureEntity c) c.setNoAi(false);
		}
		if (p != null) {
			FINISHED.put(b.playerId, b);
			send(b);
		}
	}

	public static void send(Battle b) {
		ServerPlayer p = b.player();
		if (p != null) ServerPlayNetworking.send(p, new StatePayload(b.view(p.getUUID()).toString()));
	}

	/** An order from the battle screen. */
	public static String handle(ServerPlayer p, String json) {
		JsonObject j = JsonParser.parseString(json).getAsJsonObject();
		String kind = j.get("kind").getAsString();
		if (kind.equals("recruit") || kind.equals("leave")) {
			Battle done = FINISHED.get(p.getUUID());
			if (done == null) return "No battle to leave.";
			String msg = null;
			if (kind.equals("recruit")) msg = recruit(p, done, j.get("index").getAsInt());
			else {
				FINISHED.remove(p.getUUID());
				ServerPlayNetworking.send(p, new StatePayload("{}"));
			}
			if (msg != null) p.sendSystemMessage(Component.literal(msg).withStyle(ChatFormatting.RED));
			else if (FINISHED.containsKey(p.getUUID())) send(done);
			return msg;
		}
		Battle b = ACTIVE.get(p.getUUID());
		if (b == null) return "You're not in a battle.";
		Battle.Action a = new Battle.Action(kind, opt(j, "unit"), opt(j, "x"), opt(j, "z"), opt(j, "move"), opt(j, "target"));
		String problem = b.act(a);
		if (problem != null) p.sendOverlayMessage(Component.literal(problem));
		send(b);
		return problem;
	}

	private static int opt(JsonObject j, String k) {
		return j.has(k) ? j.get(k).getAsInt() : -1;
	}

	private static String recruit(ServerPlayer p, Battle b, int index) {
		if (index < 0 || index >= b.defeated.size()) return "Pick a creature to recruit.";
		Battle.Defeated d = b.defeated.get(index);
		if (Party.xp(p) < d.cost()) return "Recruiting " + d.name() + " costs " + d.cost() + " XP; you have " + Party.xp(p) + ".";
		var species = Species.get(d.species());
		if (species.isEmpty()) return "That creature no longer exists.";
		if (!Party.add(p, new CaughtCreature(d.species(), d.influence(), d.level(), 0, species.get().statsFor(d.influence()).maxHealth(d.level())))) {
			return "Your party is full (6).";
		}
		Party.addXp(p, -d.cost());
		b.defeated.remove(index);
		p.sendSystemMessage(Component.literal(d.name() + " joined your party! (-" + d.cost() + " XP)").withStyle(ChatFormatting.GREEN));
		return null;
	}

	// ---- commands (for testing and admins) ----

	public static void registerCommands(CommandDispatcher<CommandSourceStack> d) {
		d.register(Commands.literal("battle").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("start").executes(ctx -> {
					ServerPlayer p = ctx.getSource().getPlayerOrException();
					List<CreatureEntity> near = p.level().getEntitiesOfClass(CreatureEntity.class, new AABB(p.blockPosition()).inflate(16), c -> !c.isTame());
					if (near.isEmpty()) {
						ctx.getSource().sendFailure(Component.literal("No wild creature within 16 blocks."));
						return 0;
					}
					near.sort((x, y) -> Double.compare(x.distanceToSqr(p), y.distanceToSqr(p)));
					return start(p, near.getFirst()) != null ? 1 : 0;
				}))
				.then(Commands.literal("end").executes(ctx -> {
					Battle b = of(ctx.getSource().getPlayerOrException());
					if (b == null) return 0;
					b.outcome = Battle.Outcome.FLED;
					b.phase = Battle.Phase.OVER;
					cleanUp(b);
					return 1;
				}))
				.then(Commands.literal("recruit").then(Commands.argument("index", IntegerArgumentType.integer(0)).executes(ctx -> {
					ServerPlayer p = ctx.getSource().getPlayerOrException();
					Battle b = FINISHED.get(p.getUUID());
					String msg = b == null ? "No finished battle." : recruit(p, b, IntegerArgumentType.getInteger(ctx, "index"));
					if (msg != null) ctx.getSource().sendFailure(Component.literal(msg));
					return msg == null ? 1 : 0;
				}))));
	}
}
