package dev.ahrivaea.party;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.serialization.Codec;
import dev.ahrivaea.Ahrivaea;
import dev.ahrivaea.creature.Influence;
import dev.ahrivaea.creature.Job;
import dev.ahrivaea.creature.Mounts;
import dev.ahrivaea.creature.Species;
import dev.ahrivaea.creature.Stats;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A new player picks a starter: any Common creature in its None form, which joins their party at level 1. The
 * choice is offered when they first join with an empty party (and with /starter until they've picked).
 */
public final class Starters {
	private Starters() {}

	/** Whether the player has had their starter (or started with creatures already). */
	public static final AttachmentType<Boolean> CHOSEN = AttachmentRegistry.create(Ahrivaea.id("starter_chosen"),
			b -> b.persistent(Codec.BOOL).copyOnDeath().initializer(() -> false));

	public static final int LEVEL = 1;

	public record OfferPayload(String json) implements CustomPacketPayload {
		public static final Type<OfferPayload> TYPE = new Type<>(Ahrivaea.id("starter_offer"));
		public static final StreamCodec<RegistryFriendlyByteBuf, OfferPayload> CODEC = StreamCodec.composite(ByteBufCodecs.stringUtf8(1 << 20), OfferPayload::json, OfferPayload::new);

		@Override
		public Type<OfferPayload> type() {
			return TYPE;
		}
	}

	public record PickPayload(String species) implements CustomPacketPayload {
		public static final Type<PickPayload> TYPE = new Type<>(Ahrivaea.id("starter_pick"));
		public static final StreamCodec<RegistryFriendlyByteBuf, PickPayload> CODEC = StreamCodec.composite(ByteBufCodecs.stringUtf8(256), PickPayload::species, PickPayload::new);

		@Override
		public Type<PickPayload> type() {
			return TYPE;
		}
	}

	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(OfferPayload.TYPE, OfferPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(PickPayload.TYPE, PickPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(PickPayload.TYPE, (payload, ctx) -> pick(ctx.player(), payload.species()));
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayer p = handler.getPlayer();
			if (chosen(p)) return;
			// players who already have creatures don't need one
			if (!Party.get(p).isEmpty() || !Party.box(p).isEmpty()) {
				p.setAttached(CHOSEN, true);
				return;
			}
			// (the automatic offer is left out of the game tests, which start with empty parties; StarterTest asks for it)
			if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("ahrivaea-test")) offer(p);
		});
	}

	public static boolean chosen(ServerPlayer p) {
		return p.getAttachedOrElse(CHOSEN, false);
	}

	/** Every Common creature with a None form, by name. */
	public static List<Species> options() {
		List<Species> out = new ArrayList<>();
		for (Species s : Species.all())
			if (s.forms().containsKey(Influence.NONE) && RecruitRules.rank(RecruitRules.rarity(s, Influence.NONE)) == 0
					&& !dev.ahrivaea.villager.Villagers.isVillager(s.id())) out.add(s); // villagers are found in villages
		out.sort(Comparator.comparing(s -> s.nameFor(Influence.NONE)));
		return out;
	}

	/** Opens the starter screen for the player (if their client has the mod). */
	public static void offer(ServerPlayer p) {
		if (!ServerPlayNetworking.canSend(p, OfferPayload.TYPE)) return;
		JsonObject o = new JsonObject();
		JsonArray list = new JsonArray();
		for (Species s : options()) {
			JsonObject j = new JsonObject();
			j.addProperty("id", s.id());
			j.addProperty("name", s.nameFor(Influence.NONE));
			Stats st = s.statsFor(Influence.NONE);
			JsonArray stats = new JsonArray();
			for (int v : new int[]{st.hp(), st.attack(), st.defense(), st.speed(), st.magic(), st.luck(), st.charisma()}) stats.add(v);
			j.add("stats", stats);
			j.addProperty("total", st.total());
			j.addProperty("squares", st.squaresPerTurn());
			j.addProperty("movement", s.movement().getSerializedName());
			j.addProperty("rideable", Mounts.rideable(s.sizeFor(Influence.NONE)));
			StringBuilder jobs = new StringBuilder();
			for (Job job : s.jobsFor(Influence.NONE)) jobs.append(jobs.isEmpty() ? "" : ", ").append(job.displayName());
			j.addProperty("jobs", jobs.isEmpty() ? "none" : jobs.toString());
			list.add(j);
		}
		o.add("options", list);
		o.addProperty("level", LEVEL);
		ServerPlayNetworking.send(p, new OfferPayload(o.toString()));
	}

	/** The player's choice: it joins their party at level 1. Only once. */
	static void pick(ServerPlayer p, String speciesId) {
		if (chosen(p)) {
			p.sendOverlayMessage(Component.literal("You've already chosen your starter."));
			return;
		}
		var s = options().stream().filter(o -> o.id().equals(speciesId)).findFirst();
		if (s.isEmpty()) {
			p.sendOverlayMessage(Component.literal("That creature can't be a starter."));
			return;
		}
		Party.addAnywhere(p, new CaughtCreature(s.get().id(), Influence.NONE, LEVEL, 0, s.get().statsFor(Influence.NONE).maxHealth(LEVEL)));
		p.setAttached(CHOSEN, true);
		p.sendSystemMessage(Component.literal(s.get().nameFor(Influence.NONE) + " is your starter! It's in your party (press K to see it; /party send 1 to bring it out).")
				.withStyle(ChatFormatting.GOLD));
	}

	public static void registerCommands(CommandDispatcher<CommandSourceStack> d) {
		d.register(Commands.literal("starter").executes(ctx -> {
			ServerPlayer p = ctx.getSource().getPlayerOrException();
			if (chosen(p)) {
				ctx.getSource().sendFailure(Component.literal("You've already chosen your starter."));
				return 0;
			}
			offer(p);
			return 1;
		}));
	}
}
