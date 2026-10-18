package dev.ahrivaea.villager;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.serialization.JsonOps;
import dev.ahrivaea.Ahrivaea;
import dev.ahrivaea.creature.CreatureEntity;
import dev.ahrivaea.party.CaughtCreature;
import dev.ahrivaea.party.Party;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.StatFormatter;
import net.minecraft.stats.Stats;
import net.minecraft.util.Prediction;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Villagers: a creature found in villages (vanilla villagers become them) that you recruit like any other. Train
 * one in a profession and it sells that profession's items for creature XP. Each profession has five ranks, Novice
 * to Master; each rank is earned with a deed (mine 64 stone, catch 250 fish, enter the End ...) counted from when
 * its training began, and unlocks the next, pricier set of items. Opened by right-clicking your villager, from the
 * party screen, or with /villager.
 */
public final class Villagers {
	private Villagers() {}

	public static final String SPECIES = "villager";

	/** On a villager creature out in the world: its training (sent to players' games so they can dress it). */
	public static final AttachmentType<VillagerJob> JOB = AttachmentRegistry.create(Ahrivaea.id("villager_job"),
			b -> b.persistent(VillagerJob.CODEC).syncWith(VillagerJob.STREAM_CODEC, AttachmentSyncPredicate.all()));

	// deeds that count things Ahrivaea does (Minecraft's statistics count the rest)
	public static final Identifier BATTLES_WON = stat("battles_won");
	public static final Identifier NETHER_BATTLES_WON = stat("nether_battles_won");
	public static final Identifier END_BATTLES_WON = stat("end_battles_won");
	public static final Identifier CREATURES_RECRUITED = stat("creatures_recruited");

	private static Identifier stat(String name) {
		Identifier id = Ahrivaea.id(name);
		Registry.register(BuiltInRegistries.CUSTOM_STAT, id, id);
		Stats.CUSTOM.get(id, StatFormatter.DEFAULT);
		return id;
	}

	public static void award(ServerPlayer p, Identifier stat) {
		p.awardStat(Stats.CUSTOM.get(stat));
	}

	public record StatePayload(String json) implements CustomPacketPayload {
		public static final Type<StatePayload> TYPE = new Type<>(Ahrivaea.id("villager_state"));
		public static final StreamCodec<RegistryFriendlyByteBuf, StatePayload> CODEC = StreamCodec.composite(ByteBufCodecs.stringUtf8(1 << 21), StatePayload::json, StatePayload::new);

		@Override
		public Type<StatePayload> type() {
			return TYPE;
		}
	}

	public record ActionPayload(String json) implements CustomPacketPayload {
		public static final Type<ActionPayload> TYPE = new Type<>(Ahrivaea.id("villager_action"));
		public static final StreamCodec<RegistryFriendlyByteBuf, ActionPayload> CODEC = StreamCodec.composite(ByteBufCodecs.stringUtf8(4096), ActionPayload::json, ActionPayload::new);

		@Override
		public Type<ActionPayload> type() {
			return TYPE;
		}
	}

	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(StatePayload.TYPE, StatePayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ActionPayload.TYPE, ActionPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(ActionPayload.TYPE, (payload, ctx) -> handle(ctx.player(), payload.json()));
	}

	// ---- which villager: one in the party (by slot) or one out in the world (by entity id) ----

	/** A villager the player is dealing with. */
	public record Handle(int slot, int entity) {
		JsonObject json() {
			JsonObject o = new JsonObject();
			o.addProperty("slot", slot);
			o.addProperty("entity", entity);
			return o;
		}

		static Handle of(JsonObject o) {
			return new Handle(o.has("slot") ? o.get("slot").getAsInt() : -1, o.has("entity") ? o.get("entity").getAsInt() : -1);
		}

		public static Handle party(int slot) {
			return new Handle(slot, -1);
		}

		public static Handle out(CreatureEntity e) {
			return new Handle(-1, e.getId());
		}
	}

	/** The vanilla villager outfit each profession wears (tools/villager_trades.py keeps these the same). */
	private static final java.util.Map<String, String> OUTFITS = java.util.Map.ofEntries(
			java.util.Map.entry("mason", "mason"), java.util.Map.entry("carpenter", "toolsmith"), java.util.Map.entry("gardener", "shepherd"),
			java.util.Map.entry("farmer", "farmer"), java.util.Map.entry("cook", "butcher"), java.util.Map.entry("weaver", "shepherd"),
			java.util.Map.entry("leatherworker", "leatherworker"), java.util.Map.entry("fisherman", "fisherman"), java.util.Map.entry("smith", "armorer"),
			java.util.Map.entry("hunter", "fletcher"), java.util.Map.entry("librarian", "librarian"), java.util.Map.entry("alchemist", "cleric"),
			java.util.Map.entry("engineer", "toolsmith"), java.util.Map.entry("explorer", "cartographer"), java.util.Map.entry("netherwright", "weaponsmith"),
			java.util.Map.entry("voidwalker", "cleric"), java.util.Map.entry("jeweler", "nitwit"));

	/** Works on both sides (the client doesn't have the profession files). */
	public static String outfit(String profession) {
		return OUTFITS.getOrDefault(profession, "");
	}

	public static boolean isVillager(String species) {
		return SPECIES.equals(species);
	}

	private static CreatureEntity entity(ServerPlayer p, Handle h) {
		if (h.entity() < 0) return null;
		return p.level().getEntity(h.entity()) instanceof CreatureEntity c && c.isOwnedBy(p) && isVillager(c.getSpeciesId())
				&& c.distanceToSqr(p) < 24 * 24 ? c : null;
	}

	/** The villager's name and level, or null if the handle doesn't point at one of the player's villagers. */
	private static CaughtCreature creature(ServerPlayer p, Handle h) {
		CreatureEntity e = entity(p, h);
		if (e != null) return e.toCaught();
		List<CaughtCreature> party = Party.get(p);
		if (h.slot() >= 0 && h.slot() < party.size() && isVillager(party.get(h.slot()).species())) return party.get(h.slot());
		return null;
	}

	private static void setJob(ServerPlayer p, Handle h, VillagerJob job) {
		CreatureEntity e = entity(p, h);
		if (e != null) {
			e.setAttached(JOB, job);
			return;
		}
		Party.set(p, h.slot(), Party.get(p).get(h.slot()).withJob(job));
	}

	// ---- the screen ----

	public static void open(ServerPlayer p, Handle h) {
		CaughtCreature c = creature(p, h);
		if (c == null) {
			p.sendOverlayMessage(Component.literal("That isn't one of your villagers."));
			return;
		}
		ServerPlayNetworking.send(p, new StatePayload(view(p, h, c).toString()));
	}

	static JsonObject view(ServerPlayer p, Handle h, CaughtCreature c) {
		VillagerJob job = c.job();
		JsonObject o = new JsonObject();
		o.add("handle", h.json());
		o.addProperty("level", c.level());
		o.addProperty("xp", Party.xp(p));
		o.addProperty("profession", job.profession());
		o.addProperty("rank", job.rank());
		JsonArray profs = new JsonArray();
		for (Profession pr : Profession.sorted()) {
			JsonObject j = new JsonObject();
			j.addProperty("id", pr.id());
			j.addProperty("name", pr.name());
			j.addProperty("description", pr.description());
			j.addProperty("icon", BuiltInRegistries.ITEM.getKey(pr.icon()).toString());
			j.addProperty("first", pr.ranks().isEmpty() ? "" : pr.ranks().getFirst().deed().text());
			j.addProperty("offers", pr.ranks().stream().mapToInt(r -> r.sells().size()).sum());
			profs.add(j);
		}
		o.add("professions", profs);
		Profession pr = Profession.get(job.profession());
		if (pr == null) return o;
		o.addProperty("professionName", pr.name());
		JsonArray ranks = new JsonArray();
		for (int i = 0; i < pr.ranks().size(); i++) {
			Profession.Rank r = pr.ranks().get(i);
			JsonObject j = new JsonObject();
			j.addProperty("name", r.name());
			j.addProperty("deed", r.deed().text());
			j.addProperty("amount", r.deed().amount());
			j.addProperty("earned", i < job.rank());
			if (i == job.rank()) j.addProperty("progress", r.deed().progress(p, job.baseline()));
			ranks.add(j);
		}
		o.add("ranks", ranks);
		JsonArray offers = new JsonArray();
		var ops = RegistryOps.create(JsonOps.INSTANCE, p.level().registryAccess());
		for (int i = 0; i < pr.ranks().size(); i++) {
			for (Profession.Offer off : pr.ranks().get(i).sells()) {
				JsonObject j = new JsonObject();
				j.add("stack", ItemStack.CODEC.encodeStart(ops, off.stack(p.level().registryAccess())).getOrThrow());
				j.addProperty("cost", off.cost());
				j.addProperty("rank", i);
				offers.add(j);
			}
		}
		o.add("offers", offers);
		return o;
	}

	// ---- what the player does on it ----

	static void handle(ServerPlayer p, String json) {
		JsonObject j = JsonParser.parseString(json).getAsJsonObject();
		Handle h = Handle.of(j.getAsJsonObject("handle"));
		CaughtCreature c = creature(p, h);
		if (c == null) return;
		String problem = switch (j.get("kind").getAsString()) {
			case "train" -> train(p, h, c, j.get("profession").getAsString());
			case "promote" -> promote(p, h, c);
			case "buy" -> buy(p, c, j.get("offer").getAsInt(), j.has("times") ? j.get("times").getAsInt() : 1);
			default -> null;
		};
		if (problem != null) p.sendOverlayMessage(Component.literal(problem).withStyle(ChatFormatting.RED));
		CaughtCreature now = creature(p, h);
		if (now != null) ServerPlayNetworking.send(p, new StatePayload(view(p, h, now).toString()));
	}

	/** Starts training in a profession (or switches to another, starting over as a Novice). Returns why not, or null. */
	public static String train(ServerPlayer p, Handle h, CaughtCreature c, String professionId) {
		Profession pr = Profession.get(professionId);
		if (pr == null || pr.ranks().isEmpty()) return "There's no such profession.";
		if (pr.id().equals(c.job().profession())) return "It's already a " + pr.name() + ".";
		setJob(p, h, new VillagerJob(pr.id(), 0, pr.ranks().getFirst().deed().value(p)));
		p.sendSystemMessage(Component.literal("Your villager is training as a " + pr.name() + ". To become a Novice: "
				+ pr.ranks().getFirst().deed().text() + ".").withStyle(ChatFormatting.GREEN));
		return null;
	}

	/** Earns the rank it's training for, if the deed is done. */
	public static String promote(ServerPlayer p, Handle h, CaughtCreature c) {
		VillagerJob job = c.job();
		Profession pr = Profession.get(job.profession());
		if (pr == null) return "Pick a profession first.";
		if (job.rank() >= pr.ranks().size()) return "It's already a Master " + pr.name() + ".";
		Profession.Rank r = pr.ranks().get(job.rank());
		if (r.deed().progress(p, job.baseline()) < r.deed().amount()) return "Not yet: " + r.deed().text() + ".";
		int next = job.rank() + 1;
		int baseline = next < pr.ranks().size() ? pr.ranks().get(next).deed().value(p) : 0;
		setJob(p, h, new VillagerJob(pr.id(), next, baseline));
		p.sendSystemMessage(Component.literal("Your villager is now a " + r.name() + " " + pr.name() + "! " + r.sells().size() + " more items to buy.")
				.withStyle(ChatFormatting.GOLD));
		p.level().playSound(null, p.blockPosition(), net.minecraft.sounds.SoundEvents.VILLAGER_CELEBRATE, net.minecraft.sounds.SoundSource.NEUTRAL, 1f, 1f);
		return null;
	}

	/** Buys an offer (its number in the profession's list) up to {@code times} times. */
	public static String buy(ServerPlayer p, CaughtCreature c, int index, int times) {
		Profession pr = Profession.get(c.job().profession());
		if (pr == null) return "It doesn't sell anything until it's trained.";
		int i = 0;
		for (int r = 0; r < pr.ranks().size(); r++) {
			for (Profession.Offer off : pr.ranks().get(r).sells()) {
				if (i++ != index) continue;
				if (r >= c.job().rank()) return "That's sold from " + pr.ranks().get(r).name() + " rank.";
				int afford = Party.xp(p) / Math.max(1, off.cost());
				int n = Math.min(Math.clamp(times, 1, 64), afford);
				if (n <= 0) return "That costs " + off.cost() + " XP; you have " + Party.xp(p) + ".";
				Party.addXp(p, -off.cost() * n);
				for (int k = 0; k < n; k++) p.getInventory().placeItemBackInInventory(off.stack(p.level().registryAccess()), Prediction.SERVER_ONLY);
				p.level().playSound(null, p.blockPosition(), net.minecraft.sounds.SoundEvents.VILLAGER_YES, net.minecraft.sounds.SoundSource.NEUTRAL, 0.7f, 1f);
				return null;
			}
		}
		return "No such item.";
	}

	public static void registerCommands(CommandDispatcher<CommandSourceStack> d) {
		d.register(Commands.literal("villager")
				.executes(ctx -> {
					ServerPlayer p = ctx.getSource().getPlayerOrException();
					List<CaughtCreature> party = Party.get(p);
					for (int i = 0; i < party.size(); i++) {
						if (isVillager(party.get(i).species())) {
							open(p, Handle.party(i));
							return 1;
						}
					}
					ctx.getSource().sendFailure(Component.literal("You have no villager in your party. Villagers live in villages: recruit one there."));
					return 0;
				})
				.then(Commands.literal("party").then(Commands.argument("slot", IntegerArgumentType.integer(1, Party.MAX)).executes(ctx -> {
					open(ctx.getSource().getPlayerOrException(), Handle.party(IntegerArgumentType.getInteger(ctx, "slot") - 1));
					return 1;
				})))
				.then(Commands.literal("out").then(Commands.argument("entity", IntegerArgumentType.integer()).executes(ctx -> {
					open(ctx.getSource().getPlayerOrException(), new Handle(-1, IntegerArgumentType.getInteger(ctx, "entity")));
					return 1;
				}))));
	}
}
