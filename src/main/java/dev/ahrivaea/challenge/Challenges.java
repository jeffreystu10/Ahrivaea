package dev.ahrivaea.challenge;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.serialization.Codec;
import dev.ahrivaea.Ahrivaea;
import it.unimi.dsi.fastutil.ints.IntList;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.core.particles.ParticleTypes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Server building challenges: players fill an offering chest, the structure rebuilds in stages, and a
 * leaderboard (/challenge) tracks who gave the most. Progress is saved with the overworld.
 */
public final class Challenges {
	private Challenges() {}

	public static final AttachmentType<Map<String, ChallengeState>> STATE = AttachmentRegistry.create(
			Ahrivaea.id("challenges"),
			b -> b.persistent(Codec.unboundedMap(Codec.STRING, ChallengeState.CODEC)).initializer(Map::of));

	public static void init() {
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
			String id = chestAt(server, hit.getBlockPos());
			if (id == null) return InteractionResult.PASS;
			open(sp, id);
			return InteractionResult.SUCCESS;
		});
		// the tower can't be broken by players outside creative mode
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
			if (!(level instanceof ServerLevel server) || player.isCreative()) return true;
			for (var e : states(server.getServer()).entrySet()) {
				var def = ChallengeDef.get(e.getKey());
				if (def.isPresent() && inLevel(e.getValue(), server) && box(server, def.get(), e.getValue()).isInside(pos)) {
					player.sendOverlayMessage(Component.literal(def.get().name() + " is protected. Help rebuild it at its chest!"));
					return false;
				}
			}
			return true;
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> deliverPending(handler.getPlayer()));
	}

	// ---- state ----

	static Map<String, ChallengeState> states(MinecraftServer server) {
		return server.overworld().getAttachedOrCreate(STATE);
	}

	public static Optional<ChallengeState> state(MinecraftServer server, String id) {
		return Optional.ofNullable(states(server).get(id));
	}

	static void save(MinecraftServer server, String id, ChallengeState state) {
		Map<String, ChallengeState> copy = new HashMap<>(states(server));
		if (state == null) copy.remove(id);
		else copy.put(id, state);
		server.overworld().setAttached(STATE, copy);
	}

	private static boolean inLevel(ChallengeState s, ServerLevel level) {
		return s.dimension.equals(level.dimension().identifier().toString());
	}

	private static ServerLevel level(MinecraftServer server, ChallengeState s) {
		return server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(s.dimension)));
	}

	private static String chestAt(ServerLevel level, BlockPos pos) {
		for (var e : states(level.getServer()).entrySet()) {
			var def = ChallengeDef.get(e.getKey());
			if (def.isPresent() && inLevel(e.getValue(), level) && e.getValue().origin.offset(def.get().chest()).equals(pos)) return e.getKey();
		}
		return null;
	}

	private static Optional<StructureTemplate> template(ServerLevel level, Identifier id) {
		return level.getServer().getStructureTemplateManager().get(id);
	}

	static BoundingBox box(ServerLevel level, ChallengeDef def, ChallengeState s) {
		Vec3i size = template(level, def.finished()).map(StructureTemplate::getSize).orElse(new Vec3i(1, 1, 1));
		BlockPos o = s.origin;
		return new BoundingBox(o.getX(), o.getY(), o.getZ(), o.getX() + size.getX() - 1, o.getY() + size.getY() - 1, o.getZ() + size.getZ() - 1);
	}

	// ---- placing and building ----

	/** Places the ruined structure with its corner at {@code origin} and starts the challenge from zero. */
	public static boolean place(ServerLevel level, ChallengeDef def, BlockPos origin) {
		var ruined = template(level, def.ruined());
		if (ruined.isEmpty()) {
			Ahrivaea.LOGGER.error("Challenge {}: structure {} not found", def.id(), def.ruined());
			return false;
		}
		boolean placed = ruined.get().placeInWorld(level, origin, origin, new StructurePlaceSettings(), level.getRandom(), 2);
		int solid = 0;
		for (BlockPos p : BlockPos.betweenClosed(origin, origin.offset(ruined.get().getSize()).offset(-1, -1, -1))) if (!level.getBlockState(p).isAir()) solid++;
		Ahrivaea.LOGGER.info("Challenge {}: ruined structure {} size {} placed={} chest block now {}, {} solid blocks in its box, chunk loaded {}", def.id(), def.ruined(),
				ruined.get().getSize(), placed, level.getBlockState(origin.offset(def.chest())), solid, level.isLoaded(origin));
		save(level.getServer(), def.id(), ChallengeState.fresh(level.dimension().identifier().toString(), origin));
		Ahrivaea.LOGGER.info("Challenge {} placed at {}", def.id(), origin.toShortString());
		return true;
	}

	/** Rebuilds the finished structure from the bottom up to the height of the given stage. */
	private static void buildStage(ServerLevel level, ChallengeDef def, ChallengeState s, int stage) {
		var finished = template(level, def.finished());
		if (finished.isEmpty()) {
			Ahrivaea.LOGGER.error("Challenge {}: structure {} not found", def.id(), def.finished());
			return;
		}
		BoundingBox full = box(level, def, s);
		int height = full.getYSpan();
		int top = s.origin.getY() + (int) Math.ceil(height * (double) stage / def.stages()) - 1;
		BoundingBox clip = new BoundingBox(full.minX(), full.minY(), full.minZ(), full.maxX(), Math.min(top, full.maxY()), full.maxZ());
		boolean placed = finished.get().placeInWorld(level, s.origin, s.origin, new StructurePlaceSettings().setBoundingBox(clip), level.getRandom(), 2);
		Ahrivaea.LOGGER.info("Challenge {}: stage {} up to y={} placed={}", def.id(), stage, top, placed);
		var c = full.getCenter();
		level.sendParticles(ParticleTypes.CLOUD, c.getX() + 0.5, top, c.getZ() + 0.5, 120, full.getXSpan() / 2.0, 1, full.getZSpan() / 2.0, 0.05);
		level.playSound(null, c, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 1.5f, 0.7f);
	}

	// ---- offerings ----

	private static void open(ServerPlayer player, String id) {
		var def = ChallengeDef.get(id).orElseThrow();
		var s = state(player.level().getServer(), id).orElseThrow();
		if (s.complete) {
			player.sendSystemMessage(Component.literal(def.name() + " is complete! Thank you to everyone who helped.").withStyle(ChatFormatting.GOLD));
			sendLeaderboard(player.createCommandSourceStack(), def, s, player);
			return;
		}
		Component title = Component.literal("Offer " + def.unit() + ": " + s.total + "/" + def.goal());
		player.openMenu(new SimpleMenuProvider((syncId, inv, p) -> new TributeMenu(syncId, inv, new SimpleContainer(27), id), title));
	}

	/** Counts what a player put in; accepted items are used up, the rest is handed back. */
	static void offer(ServerPlayer player, String id, List<ItemStack> stacks) {
		var def = ChallengeDef.get(id).orElse(null);
		var s = state(player.level().getServer(), id).orElse(null);
		int given = 0;
		List<ItemStack> back = new ArrayList<>();
		for (ItemStack stack : stacks) {
			Integer worth = def == null ? null : def.items().get(BuiltInRegistries.ITEM.getKey(stack.getItem()));
			if (worth == null || worth <= 0 || s == null || s.complete) {
				back.add(stack);
				continue;
			}
			int remaining = def.goal() - s.total - given;
			int take = Math.min(stack.getCount(), Math.max(0, (remaining + worth - 1) / worth));
			given += take * worth;
			if (take < stack.getCount()) back.add(stack.copyWithCount(stack.getCount() - take));
		}
		for (ItemStack b : back) player.getInventory().placeItemBackInInventory(b, Prediction.SERVER_ONLY);
		if (given > 0) {
			if (!back.isEmpty()) player.sendSystemMessage(Component.literal("Only " + def.unit() + " count; the rest went back in your inventory.").withStyle(ChatFormatting.GRAY));
			credit(player.level().getServer(), id, player.getUUID().toString(), player.getPlainTextName(), given);
		} else if (!back.isEmpty() && def != null) {
			player.sendOverlayMessage(Component.literal("This chest only takes " + def.unit() + "."));
		}
	}

	/** Records a donation, rebuilds any stages it completes and finishes the challenge at the goal. */
	public static void credit(MinecraftServer server, String id, String key, String name, int amount) {
		var def = ChallengeDef.get(id).orElse(null);
		var s = state(server, id).orElse(null);
		if (def == null || s == null || s.complete || amount <= 0) return;
		ServerLevel level = level(server, s);
		s.total = Math.min(def.goal(), s.total + amount);
		s.donations.merge(key, amount, Integer::sum);
		s.names.put(key, name);
		Ahrivaea.LOGGER.info("Challenge {}: {} gave {} {} ({}/{})", id, name, amount, def.unit(), s.total, def.goal());
		server.getPlayerList().broadcastSystemMessage(Component.literal(name + " offered " + amount + " " + def.unit() + " to " + def.name()
				+ " (" + s.total + "/" + def.goal() + ")").withStyle(ChatFormatting.YELLOW), false);

		while (s.stage < def.stages() && s.total >= def.threshold(s.stage + 1)) {
			s.stage++;
			if (level != null) buildStage(level, def, s, s.stage);
			Ahrivaea.LOGGER.info("Challenge {}: stage {}/{} built", id, s.stage, def.stages());
			if (s.stage < def.stages()) {
				server.getPlayerList().broadcastSystemMessage(Component.literal(def.name() + " rises! Stage " + s.stage + " of " + def.stages() + " rebuilt.")
						.withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD), false);
			}
		}
		if (s.total >= def.goal()) {
			s.complete = true;
			save(server, id, s);
			complete(server, level, def, s);
		} else {
			save(server, id, s);
		}
	}

	private static void complete(MinecraftServer server, ServerLevel level, ChallengeDef def, ChallengeState s) {
		List<String> ranking = s.ranking();
		Ahrivaea.LOGGER.info("Challenge {} complete. Top donors: {}", def.id(),
				String.join(", ", ranking.stream().limit(5).map(k -> s.names.getOrDefault(k, k) + " " + s.donations.get(k)).toList()));
		server.getPlayerList().broadcastSystemMessage(Component.literal(def.name() + " has been fully rebuilt! Thank you all!")
				.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			p.connection.send(new ClientboundSetTitleTextPacket(Component.literal(def.name()).withStyle(ChatFormatting.GOLD)));
			p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("has been rebuilt!")));
			p.level().playSound(null, p.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 1f, 1f);
		}
		if (level != null) {
			BoundingBox b = box(level, def, s);
			var rnd = level.getRandom();
			int[] colors = {0xFFD700, 0xFF5533, 0x55AAFF, 0x77FF77, 0xFFFFFF};
			for (int i = 0; i < 12; i++) {
				ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
				rocket.set(DataComponents.FIREWORKS, new Fireworks(1 + rnd.nextInt(2), List.of(new FireworkExplosion(
						FireworkExplosion.Shape.values()[rnd.nextInt(FireworkExplosion.Shape.values().length)],
						IntList.of(colors[rnd.nextInt(colors.length)], colors[rnd.nextInt(colors.length)]), IntList.of(0xFFFFFF), true, true))));
				double x = b.minX() + rnd.nextDouble() * b.getXSpan(), z = b.minZ() + rnd.nextDouble() * b.getZSpan();
				level.addFreshEntity(new FireworkRocketEntity(level, x, b.maxY() + 1, z, rocket));
			}
		}
		// rewards: by rank, or rank 0 for everyone who gave
		for (ChallengeDef.Reward r : def.rewards()) {
			List<String> who = r.rank() == 0 ? ranking : (r.rank() <= ranking.size() ? List.of(ranking.get(r.rank() - 1)) : List.of());
			for (String key : who) {
				s.pendingRewards.computeIfAbsent(key, k -> new ArrayList<>()).addAll(r.items());
				if (r.rank() > 0) {
					server.getPlayerList().broadcastSystemMessage(Component.literal("#" + r.rank() + " " + s.names.getOrDefault(key, "?")
							+ " with " + s.donations.get(key) + " " + def.unit() + " earns a reward!").withStyle(ChatFormatting.GOLD), false);
				}
			}
		}
		save(server, def.id(), s);
		for (ServerPlayer p : server.getPlayerList().getPlayers()) deliverPending(p);
	}

	/** Gives a player any rewards waiting for them (they may have been offline when a challenge finished). */
	static void deliverPending(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		String key = player.getUUID().toString();
		for (var e : states(server).entrySet()) {
			List<ChallengeDef.RewardItem> items = e.getValue().pendingRewards.remove(key);
			if (items == null || items.isEmpty()) continue;
			for (ChallengeDef.RewardItem it : items) {
				var item = BuiltInRegistries.ITEM.getValue(it.item());
				player.getInventory().placeItemBackInInventory(new ItemStack(item, it.count()), Prediction.SERVER_ONLY);
			}
			String name = ChallengeDef.get(e.getKey()).map(ChallengeDef::name).orElse(e.getKey());
			player.sendSystemMessage(Component.literal("You received your reward for helping rebuild " + name + "!").withStyle(ChatFormatting.GOLD));
			save(server, e.getKey(), e.getValue());
		}
	}

	// ---- leaderboard and commands ----

	static void sendLeaderboard(CommandSourceStack source, ChallengeDef def, ChallengeState s, ServerPlayer viewer) {
		int pct = (int) (100L * s.total / def.goal());
		int bars = pct / 5;
		MutableComponent text = Component.literal("\n" + def.name() + "\n").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
				.append(Component.literal("█".repeat(bars)).withStyle(ChatFormatting.GREEN))
				.append(Component.literal("█".repeat(20 - bars)).withStyle(ChatFormatting.DARK_GRAY))
				.append(Component.literal(" " + s.total + "/" + def.goal() + " " + def.unit() + " (" + pct + "%), stage " + s.stage + "/" + def.stages()
						+ (s.complete ? " - COMPLETE" : "")).withStyle(ChatFormatting.WHITE));
		List<String> ranking = s.ranking();
		if (ranking.isEmpty()) text.append(Component.literal("\nNo offerings yet. Be the first!").withStyle(ChatFormatting.GRAY));
		for (int i = 0; i < Math.min(10, ranking.size()); i++) {
			String k = ranking.get(i);
			ChatFormatting color = i == 0 ? ChatFormatting.GOLD : i == 1 ? ChatFormatting.GRAY : i == 2 ? ChatFormatting.RED : ChatFormatting.WHITE;
			text.append(Component.literal("\n " + (i + 1) + ". " + s.names.getOrDefault(k, "?") + " - " + s.donations.get(k)).withStyle(color));
		}
		if (viewer != null) {
			String me = viewer.getUUID().toString();
			int rank = s.rankOf(me);
			text.append(Component.literal(rank > 0 ? "\nYou: " + s.donations.get(me) + " " + def.unit() + " (#" + rank + ")" : "\nYou haven't offered anything yet.")
					.withStyle(ChatFormatting.AQUA));
		}
		source.sendSuccess(() -> text, false);
		Ahrivaea.LOGGER.info("Challenge {} leaderboard: {}/{} stage {}; {}", def.id(), s.total, def.goal(), s.stage,
				String.join(", ", ranking.stream().map(k -> s.names.getOrDefault(k, k) + " " + s.donations.get(k)).toList()));
	}

	public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
		var ids = (com.mojang.brigadier.suggestion.SuggestionProvider<CommandSourceStack>) (ctx, b) -> SharedSuggestionProvider.suggest(ChallengeDef.all().keySet(), b);
		dispatcher.register(Commands.literal("challenge")
				.executes(ctx -> {
					var server = ctx.getSource().getServer();
					int shown = 0;
					for (var e : states(server).entrySet()) {
						var def = ChallengeDef.get(e.getKey());
						if (def.isEmpty()) continue;
						sendLeaderboard(ctx.getSource(), def.get(), e.getValue(), ctx.getSource().getPlayer());
						shown++;
					}
					if (shown == 0) ctx.getSource().sendSuccess(() -> Component.literal("There are no challenges running right now."), false);
					return shown;
				})
				.then(Commands.literal("top").then(Commands.argument("id", StringArgumentType.word()).suggests(ids).executes(ctx -> {
					String id = StringArgumentType.getString(ctx, "id");
					var def = ChallengeDef.get(id);
					var s = state(ctx.getSource().getServer(), id);
					if (def.isEmpty() || s.isEmpty()) {
						ctx.getSource().sendFailure(Component.literal("No challenge called " + id + " is running."));
						return 0;
					}
					sendLeaderboard(ctx.getSource(), def.get(), s.get(), ctx.getSource().getPlayer());
					return 1;
				})))
				// operator tools
				.then(Commands.literal("place").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(Commands.argument("id", StringArgumentType.word()).suggests(ids).executes(ctx -> {
							String id = StringArgumentType.getString(ctx, "id");
							var def = ChallengeDef.get(id);
							if (def.isEmpty()) {
								ctx.getSource().sendFailure(Component.literal("No challenge called " + id + "."));
								return 0;
							}
							BlockPos origin = BlockPos.containing(ctx.getSource().getPosition());
							if (!place(ctx.getSource().getLevel(), def.get(), origin)) {
								ctx.getSource().sendFailure(Component.literal("Couldn't find the structure files for " + id + "."));
								return 0;
							}
							ctx.getSource().sendSuccess(() -> Component.literal("Placed " + def.get().name() + " at " + origin.toShortString()
									+ ". Its offering chest is at " + origin.offset(def.get().chest()).toShortString() + "."), true);
							return 1;
						})))
				.then(Commands.literal("remove").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(Commands.argument("id", StringArgumentType.word()).suggests(ids).executes(ctx -> {
							String id = StringArgumentType.getString(ctx, "id");
							save(ctx.getSource().getServer(), id, null);
							ctx.getSource().sendSuccess(() -> Component.literal("Stopped " + id + " (the blocks stay where they are)."), true);
							return 1;
						})))
				.then(Commands.literal("credit").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(Commands.argument("id", StringArgumentType.word()).suggests(ids)
								.then(Commands.argument("player", StringArgumentType.word())
										.then(Commands.argument("amount", IntegerArgumentType.integer(1)).executes(ctx -> {
											String id = StringArgumentType.getString(ctx, "id"), name = StringArgumentType.getString(ctx, "player");
											var server = ctx.getSource().getServer();
											if (state(server, id).isEmpty()) {
												ctx.getSource().sendFailure(Component.literal("No challenge called " + id + " is running."));
												return 0;
											}
											ServerPlayer online = server.getPlayerList().getPlayerByName(name);
											String key = online != null ? online.getUUID().toString()
													: UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
											credit(server, id, key, online != null ? online.getPlainTextName() : name, IntegerArgumentType.getInteger(ctx, "amount"));
											return 1;
										}))))));
	}
}
