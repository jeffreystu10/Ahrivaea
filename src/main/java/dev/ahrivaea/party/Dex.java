package dev.ahrivaea.party;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import dev.ahrivaea.Ahrivaea;
import dev.ahrivaea.creature.CreatureEntity;
import dev.ahrivaea.creature.Influence;
import dev.ahrivaea.creature.Job;
import dev.ahrivaea.creature.Mounts;
import dev.ahrivaea.creature.Species;
import dev.ahrivaea.creature.Stats;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The creature dex: every creature form, and how much each player knows about it. A form is "seen" once a wild one
 * has been near them (within {@value #SEE_RANGE} blocks, in sight) or fought them, and "caught" once one has been in
 * their party or box. Unknown forms show only a clue to where they live; seen ones their name, look and habitat;
 * caught ones everything.
 */
public final class Dex {
	private Dex() {}

	public static final int SEEN = 1, CAUGHT = 2;
	public static final int SEE_RANGE = 20;

	/** Form key ("species/influence") to SEEN or CAUGHT. Saved with the player and kept on death. */
	public static final AttachmentType<Map<String, Integer>> ATTACHMENT = AttachmentRegistry.create(Ahrivaea.id("dex"),
			b -> b.persistent(Codec.unboundedMap(Codec.STRING, Codec.INT)).copyOnDeath().initializer(Map::of));

	public record RequestPayload() implements CustomPacketPayload {
		public static final Type<RequestPayload> TYPE = new Type<>(Ahrivaea.id("dex_request"));
		public static final StreamCodec<RegistryFriendlyByteBuf, RequestPayload> CODEC = StreamCodec.unit(new RequestPayload());

		@Override
		public Type<RequestPayload> type() {
			return TYPE;
		}
	}

	public record DataPayload(String json) implements CustomPacketPayload {
		public static final Type<DataPayload> TYPE = new Type<>(Ahrivaea.id("dex_data"));
		public static final StreamCodec<RegistryFriendlyByteBuf, DataPayload> CODEC = StreamCodec.composite(ByteBufCodecs.stringUtf8(1 << 21), DataPayload::json, DataPayload::new);

		@Override
		public Type<DataPayload> type() {
			return TYPE;
		}
	}

	public static void init() {
		PayloadTypeRegistry.serverboundPlay().register(RequestPayload.TYPE, RequestPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(DataPayload.TYPE, DataPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(RequestPayload.TYPE, (payload, ctx) -> send(ctx.player()));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 20 != 7) return;
			for (ServerPlayer p : server.getPlayerList().getPlayers()) update(p);
		});
	}

	public static String key(String species, Influence influence) {
		return species + "/" + influence.getSerializedName();
	}

	/** How many forms the player has seen (or caught, with CAUGHT). */
	public static int count(ServerPlayer p, int atLeast) {
		int n = 0;
		for (int s : p.getAttachedOrElse(ATTACHMENT, Map.of()).values()) if (s >= atLeast) n++;
		return n;
	}

	public static int status(ServerPlayer p, String key) {
		return p.getAttachedOrElse(ATTACHMENT, Map.of()).getOrDefault(key, 0);
	}

	/** Records what a player knows; tells them when something is new. Never goes down (caught stays caught). */
	public static void mark(ServerPlayer p, String species, Influence influence, int status) {
		var sp = Species.get(species);
		if (sp.isEmpty()) return;
		influence = sp.get().formOrDefault(influence);
		String key = key(species, influence);
		Map<String, Integer> dex = p.getAttachedOrElse(ATTACHMENT, Map.of());
		if (dex.getOrDefault(key, 0) >= status) return;
		Map<String, Integer> copy = new HashMap<>(dex);
		copy.put(key, status);
		p.setAttached(ATTACHMENT, copy);
		String name = sp.get().nameFor(influence);
		p.sendOverlayMessage(Component.literal(status == CAUGHT ? "Dex: " + name + " caught! Its full entry is in your dex." : "Dex: new creature seen, " + name + "!")
				.withStyle(status == CAUGHT ? ChatFormatting.GOLD : ChatFormatting.AQUA));
	}

	/** Once a second: what's in the party, box and out (caught), and wild creatures in sight or in a battle (seen). */
	static void update(ServerPlayer p) {
		for (CaughtCreature c : Party.get(p)) mark(p, c.species(), c.influence(), CAUGHT);
		for (CaughtCreature c : Party.box(p)) mark(p, c.species(), c.influence(), CAUGHT);
		var battle = dev.ahrivaea.battle.Battles.of(p);
		if (battle != null) {
			for (var u : battle.units) if (!u.isPlayer && u.speciesId != null) mark(p, u.speciesId, u.influence, u.side == dev.ahrivaea.battle.Unit.Side.PLAYER ? CAUGHT : SEEN);
			return;
		}
		for (CreatureEntity e : p.level().getEntitiesOfClass(CreatureEntity.class, p.getBoundingBox().inflate(SEE_RANGE),
				e -> !e.getSpeciesId().isEmpty() && p.hasLineOfSight(e))) {
			mark(p, e.getSpeciesId(), e.getInfluence(), e.isTame() && e.isOwnedBy(p) ? CAUGHT : SEEN);
		}
	}

	/** The whole dex as this player knows it (sent when they open it). */
	public static void send(ServerPlayer p) {
		update(p);
		Map<String, Integer> dex = p.getAttachedOrElse(ATTACHMENT, Map.of());
		JsonObject o = new JsonObject();
		JsonArray list = new JsonArray();
		List<Species> all = new ArrayList<>(Species.all());
		all.sort(java.util.Comparator.comparing(s -> s.nameFor(s.formOrDefault(Influence.NONE))));
		int number = 0, seen = 0, caught = 0;
		for (Species s : all) {
			List<Influence> forms = new ArrayList<>(s.forms().keySet());
			forms.sort(java.util.Comparator.comparingInt(Enum::ordinal));
			for (Influence inf : forms) {
				number++;
				int st = dex.getOrDefault(key(s.id(), inf), 0);
				if (st >= SEEN) seen++;
				if (st >= CAUGHT) caught++;
				list.add(entry(s, inf, number, st));
			}
		}
		o.add("entries", list);
		o.addProperty("seen", seen);
		o.addProperty("caught", caught);
		ServerPlayNetworking.send(p, new DataPayload(o.toString()));
	}

	private static JsonObject entry(Species s, Influence inf, int number, int status) {
		JsonObject j = new JsonObject();
		j.addProperty("n", number);
		j.addProperty("species", s.id());
		j.addProperty("influence", inf.getSerializedName());
		j.addProperty("influenceName", inf.displayName());
		j.addProperty("color", inf.color());
		j.addProperty("status", status);
		var spawn = s.form(inf).spawn().or(s::spawn);
		List<String> biomes = spawn.map(Species.Spawn::biomes).orElse(List.of());
		// even an unknown creature gives a clue: the first place it lives
		j.addProperty("hint", s.id().equals("villager") ? "Lives in villages" : biomes.isEmpty() ? "Not found in the wild" : "Lives in " + biomeName(biomes.getFirst()) + (biomes.size() > 1 ? " and elsewhere" : ""));
		if (status < SEEN) return j;
		j.addProperty("name", s.nameFor(inf));
		j.addProperty("rarity", RecruitRules.rarity(s, inf));
		j.addProperty("movement", s.movement().getSerializedName());
		JsonArray where = new JsonArray();
		for (String b : biomes) where.add(biomeName(b));
		j.add("biomes", where);
		int[] levels = s.levelRange(inf);
		j.addProperty("levels", levels[0] + "-" + levels[1]);
		if (status < CAUGHT) return j;
		Stats st = s.statsFor(inf);
		JsonArray stats = new JsonArray();
		for (int v : new int[]{st.hp(), st.attack(), st.defense(), st.speed(), st.magic(), st.luck(), st.charisma()}) stats.add(v);
		j.add("stats", stats);
		j.addProperty("total", st.total());
		j.addProperty("squares", st.squaresPerTurn());
		StringBuilder jobs = new StringBuilder();
		for (Job job : s.jobsFor(inf)) jobs.append(jobs.isEmpty() ? "" : ", ").append(job.displayName());
		j.addProperty("jobs", jobs.isEmpty() ? "none" : jobs.toString());
		JsonArray drops = new JsonArray();
		for (Species.Drop d : s.form(inf).drops()) {
			Identifier id = Identifier.tryParse(d.item());
			var item = id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
			String name = item == null ? d.item() : item.getDefaultInstance().getHoverName().getString();
			drops.add(name + " (" + Math.round(d.chance() * 100) + "%" + (d.max() > 1 ? ", " + d.min() + "-" + d.max() : "") + ")");
		}
		j.add("drops", drops);
		j.addProperty("rideable", Mounts.rideable(s.sizeFor(inf)));
		return j;
	}

	/** "minecraft:snowy_plains" to "Snowy Plains", "#minecraft:is_forest" to "forests". */
	public static String biomeName(String id) {
		boolean tag = id.startsWith("#");
		String path = id.substring(id.indexOf(':') + 1);
		if (tag) {
			if (path.startsWith("is_")) path = path.substring(3);
			if (path.equals("overworld")) return "anywhere in the Overworld";
			if (path.equals("nether")) return "anywhere in the Nether";
			if (path.equals("end")) return "the End";
			String words = path.replace('_', ' ');
			return words.endsWith("s") ? words : words + "s";
		}
		StringBuilder out = new StringBuilder();
		for (String w : path.split("_")) if (!w.isEmpty()) out.append(out.isEmpty() ? "" : " ").append(Character.toUpperCase(w.charAt(0))).append(w.substring(1).toLowerCase(Locale.ROOT));
		return out.toString();
	}
}
