package dev.ahrivaea.party;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.ahrivaea.Ahrivaea;
import dev.ahrivaea.creature.BattleSettings;
import dev.ahrivaea.creature.CreatureEntity;
import dev.ahrivaea.creature.Job;
import dev.ahrivaea.creature.Mounts;
import dev.ahrivaea.creature.Species;
import dev.ahrivaea.creature.Stats;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Sends each player what their party screen and the on-screen party summary show: the party, creatures out in the
 * world with them, the box and their XP to give out. Worked out here because only the server has the species data;
 * sent when the player joins and again whenever anything in it changes (checked twice a second).
 */
public final class PartySync {
	private PartySync() {}

	/** How far away a creature you sent out still shows as "out with you". */
	public static final double OUT_RANGE = 64;

	public record StatePayload(String json) implements CustomPacketPayload {
		public static final Type<StatePayload> TYPE = new Type<>(Ahrivaea.id("party_state"));
		public static final StreamCodec<RegistryFriendlyByteBuf, StatePayload> CODEC = StreamCodec.composite(ByteBufCodecs.stringUtf8(1 << 20), StatePayload::json, StatePayload::new);

		@Override
		public Type<StatePayload> type() {
			return TYPE;
		}
	}

	private static final Map<UUID, String> LAST_SENT = new HashMap<>();

	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(StatePayload.TYPE, StatePayload.CODEC);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> LAST_SENT.remove(handler.getPlayer().getUUID()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> LAST_SENT.remove(handler.getPlayer().getUUID()));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 10 != 0) return;
			for (ServerPlayer p : server.getPlayerList().getPlayers()) sync(p);
		});
	}

	/** Sends the player's party view if it changed since last time. */
	public static void sync(ServerPlayer p) {
		if (!ServerPlayNetworking.canSend(p, StatePayload.TYPE)) return;
		String json = view(p).toString();
		if (json.equals(LAST_SENT.get(p.getUUID()))) return;
		LAST_SENT.put(p.getUUID(), json);
		ServerPlayNetworking.send(p, new StatePayload(json));
	}

	public static JsonObject view(ServerPlayer p) {
		JsonObject o = new JsonObject();
		o.addProperty("xp", Party.xp(p));
		JsonArray party = new JsonArray();
		for (CaughtCreature c : Party.get(p)) party.add(describe(c));
		o.add("party", party);
		JsonArray box = new JsonArray();
		for (CaughtCreature c : Party.box(p)) box.add(describe(c));
		o.add("box", box);
		JsonArray out = new JsonArray();
		if (dev.ahrivaea.battle.Battles.of(p) == null) {
			List<CreatureEntity> near = p.level().getEntitiesOfClass(CreatureEntity.class, p.getBoundingBox().inflate(OUT_RANGE),
					e -> e.isTame() && e.isOwnedBy(p) && !e.hasAttached(dev.ahrivaea.base.Bases.WORKER_OF) && !dev.ahrivaea.battle.Battles.inBattle(e));
			near.sort(java.util.Comparator.comparingInt(CreatureEntity::getId));
			for (CreatureEntity e : near) {
				JsonObject j = describe(e.toCaught());
				j.addProperty("hp", Math.round(e.getHealth()));
				j.addProperty("entity", e.getId());
				j.addProperty("riding", p.getVehicle() == e);
				j.addProperty("sitting", e.isOrderedToSit());
				out.add(j);
			}
		}
		o.add("out", out);
		return o;
	}

	/** Everything the screen shows about one creature. */
	public static JsonObject describe(CaughtCreature c) {
		JsonObject j = new JsonObject();
		var sp = Species.get(c.species());
		j.addProperty("name", sp.map(s -> s.nameFor(c.influence())).orElse(c.species()));
		j.addProperty("influence", c.influence().displayName());
		j.addProperty("color", c.influence().color());
		j.addProperty("level", c.level());
		j.addProperty("xp", c.xp());
		j.addProperty("xpNext", BattleSettings.xpToNext(c.level()));
		j.addProperty("hp", Math.round(c.health()));
		if (sp.isEmpty()) return j;
		Species s = sp.get();
		Stats st = s.statsFor(c.influence());
		j.addProperty("maxHp", st.maxHealth(c.level()));
		j.addProperty("hp", Math.min(Math.round(c.health()), st.maxHealth(c.level())));
		JsonArray stats = new JsonArray();
		int[] base = {st.hp(), st.attack(), st.defense(), st.speed(), st.magic(), st.luck(), st.charisma()};
		for (int b : base) {
			JsonArray pair = new JsonArray();
			pair.add((int) Math.round(st.effective(b, c.level())));
			pair.add(b);
			stats.add(pair);
		}
		j.add("stats", stats);
		j.addProperty("squares", st.squaresPerTurn());
		j.addProperty("movement", s.movement().getSerializedName());
		StringBuilder jobs = new StringBuilder();
		for (Job job : s.jobsFor(c.influence())) jobs.append(jobs.isEmpty() ? "" : ", ").append(job.displayName());
		j.addProperty("jobs", jobs.isEmpty() ? "none" : jobs.toString());
		// riding
		boolean rideable = Mounts.rideable(s.sizeFor(c.influence()));
		j.addProperty("rideable", rideable);
		if (rideable) {
			double speed = st.effective(st.speed(), c.level());
			int tier = Mounts.tier(c.mountTier(), speed);
			j.addProperty("rideTier", tier);
			j.addProperty("topSpeed", Math.round(Mounts.blocksPerSecond(speed, tier) * 10) / 10.0);
			j.addProperty("saddled", c.saddled());
			Mounts.Step step = Mounts.stepFrom(c.mountTier());
			j.addProperty("nextTier", step != null ? c.mountFed() + "/" + step.amount() + " " + step.treat().getDefaultInstance().getHoverName().getString()
					: tier >= Mounts.MAX_TIER ? "top tier" : "Speed " + (tier < 4 ? Mounts.TIER_4_SPEED : Mounts.TIER_5_SPEED) + " (it has " + Math.round(speed) + ")");
		}
		return j;
	}
}
