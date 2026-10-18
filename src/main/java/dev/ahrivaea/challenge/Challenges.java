package dev.ahrivaea.challenge;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.serialization.Codec;
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
			TowersMod.id("challenges"),
			b -> b.persistent(Codec.unboundedMap(Codec.STRING, ChallengeState.CODEC)).initializer(Map::of));

	public static void init() {
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
			String id = chestAt(server, hit.getBlockPos());
			if (id == null) return InteractionResult.PASS;
			// a completed tower's chest is a base core now (if Ahrivaea Creatures is installed), which handles its own clicks
			if (state(server.getServer(), id).map(st -> st.complete).orElse(true)
					&& !(server.getBlockState(hit.getBlockPos()).getBlock() instanceof net.minecraft.world.level.block.ChestBlock)) return InteractionResult.PASS;
			open(sp, id);
			return InteractionResult.SUCCESS;
		});
		// breaking, placing and using blocks around a tower are also stopped by ServerLevelMixin (mayInteract); this
		// catches any break that gets past it
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
			if (!(level instanceof ServerLevel server)) return true;
			if (protectedFrom(server, player, pos, false) != null) return false;
			// a completed tower's base core stays where the offering chest was
			String id = chestAt(server, pos);
			if (id != null && !player.isCreative() && state(server.getServer(), id).map(st -> st.complete).orElse(false)) {
				player.sendOverlayMessage(Component.literal("This base belongs to the tower; it can't be moved."));
				return false;
			}
			return true;
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> deliverPending(handler.getPlayer()));
		// towers completed before completion made a base: their chest becomes one once it's loaded
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 100 != 13) return;
			for (var e : states(server).entrySet()) {
				ChallengeState st = e.getValue();
				if (!st.complete) continue;
				var def = defOf(e.getKey(), st);
				ServerLevel lvl = level(server, st);
				if (def.isEmpty() || lvl == null) continue;
				BlockPos chest = st.origin.offset(def.get().chest());
				if (lvl.isLoaded(chest) && lvl.getBlockState(chest).getBlock() instanceof net.minecraft.world.level.block.ChestBlock && makeBase(lvl, e.getKey(), def.get(), st)) {
					save(server, e.getKey(), st);
					for (ServerPlayer p : server.getPlayerList().getPlayers()) deliverPending(p);
				}
			}
		});
	}

	// ---- protection ----

	/** How far around a tower its protection reaches, sideways and up (blocks). */
	public static final int PROTECT_RADIUS = 64;
	/** And how far below the tower's base, so the stronghold under it stays open to everyone. */
	public static final int PROTECT_BELOW = 8;
	private static final Map<String, BoundingBox> AREAS = new HashMap<>();

	/**
	 * Whether this player is kept from changing the block at pos because it's in a tower's protected area: every
	 * player until the tower is complete, then everyone but the player who controls it (its top donor). Players in
	 * creative mode (operators) aren't. The offering chest can always be used. Returns the protecting tower's id,
	 * or null if the player may go ahead; tells the player why when {@code tell}.
	 */
	public static String protectedFrom(ServerLevel level, net.minecraft.world.entity.Entity entity, BlockPos pos, boolean tell) {
		if (!(entity instanceof ServerPlayer player) || player.isCreative() || player.isSpectator()) return null;
		for (var e : states(level.getServer()).entrySet()) {
			ChallengeState s = e.getValue();
			if (!inLevel(s, level)) continue;
			var def = defOf(e.getKey(), s);
			if (def.isEmpty()) continue;
			// the End portal room under an unfinished tower is sealed for everyone
			BoundingBox room = s.portalRoomBox();
			if (!s.complete && room != null && room.inflatedBy(1).isInside(pos)) {
				if (tell) player.sendOverlayMessage(Component.literal("The way to the End is sealed until " + def.get().name() + " above is rebuilt."));
				return e.getKey();
			}
			BoundingBox area = AREAS.computeIfAbsent(e.getKey() + "@" + s.origin.asLong(), k -> {
				BoundingBox b = box(level, def.get(), s);
				return new BoundingBox(b.minX() - PROTECT_RADIUS, b.minY() - PROTECT_BELOW, b.minZ() - PROTECT_RADIUS,
						b.maxX() + PROTECT_RADIUS, level.getMaxY() + 1, b.maxZ() + PROTECT_RADIUS);
			});
			if (!area.isInside(pos)) continue;
			if (!s.complete && s.origin.offset(def.get().chest()).equals(pos)) return null;
			String owner = s.controller();
			if (!owner.isEmpty() && (owner.equals(player.getUUID().toString()) || sameGuild(level.getServer(), player, owner, s))) return null;
			if (tell) player.sendOverlayMessage(Component.literal(owner.isEmpty()
					? def.get().name() + " and the land around it are protected until it's rebuilt. Its top donor claims it."
					: def.get().name() + " belongs to " + s.names.getOrDefault(owner, "its top donor") + " and their guild."));
			return e.getKey();
		}
		return null;
	}

	/**
	 * Whether the player is in the same guild as a tower's owner. Guilds are scoreboard teams (the Ahrivaea Guilds mod
	 * makes them), so this works whether or not that mod is installed, and for owners who are offline.
	 */
	static boolean sameGuild(MinecraftServer server, ServerPlayer player, String ownerKey, ChallengeState s) {
		var team = player.getTeam();
		if (team == null) return false;
		ServerPlayer online = null;
		try {
			online = server.getPlayerList().getPlayer(UUID.fromString(ownerKey));
		} catch (IllegalArgumentException ignored) {}
		String name = online != null ? online.getScoreboardName() : s.names.get(ownerKey);
		if (name == null) return false;
		var ownerTeam = server.getScoreboard().getPlayersTeam(name);
		return ownerTeam != null && ownerTeam.getName().equals(team.getName());
	}

	// ---- the base a completed tower becomes ----

	/** The Base Core block from the main Ahrivaea mod (by name, so this mod doesn't need it). */
	public static final Identifier BASE_CORE = Identifier.fromNamespaceAndPath("ahrivaea", "base_core");

	/**
	 * Turns a completed tower's offering chest into a Base Core owned by the tower's owner: the only way to get a
	 * base. Does nothing if the Ahrivaea mod (which has bases) isn't installed.
	 */
	static boolean makeBase(ServerLevel level, String id, ChallengeDef def, ChallengeState s) {
		var block = BuiltInRegistries.BLOCK.getOptional(BASE_CORE);
		if (block.isEmpty() || level == null) return false;
		BlockPos pos = s.origin.offset(def.chest());
		level.setBlock(pos, block.get().defaultBlockState(), 3);
		String owner = s.controller();
		var be = level.getBlockEntity(pos);
		if (be != null && !owner.isEmpty()) {
			try {
				net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
				tag.store("owner", net.minecraft.core.UUIDUtil.CODEC, UUID.fromString(owner));
				tag.putString("owner_name", s.names.getOrDefault(owner, ""));
				be.loadCustomOnly(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess(), tag));
				be.setChanged();
			} catch (IllegalArgumentException ignored) {}
		}
		// the owner also gets a Base Core of their own, to put wherever they like
		if (!owner.isEmpty()) s.pendingRewards.computeIfAbsent(owner, k -> new ArrayList<>()).add(new ChallengeDef.RewardItem(BASE_CORE, 1));
		level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 40, 0.4, 0.6, 0.4, 0.05);
		level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1f, 1f);
		TowersMod.LOGGER.info("Tower {}: the offering chest at {} is now a base for {}", id, pos.toShortString(), s.names.getOrDefault(owner, "nobody"));
		return true;
	}

	// ---- the End portal room ----

	/**
	 * Seals the End portal room under an unfinished tower: every opening in its walls, floor and ceiling (doorways,
	 * cave holes) is filled with reinforced deepslate, and nothing in or around it can be broken, placed or used
	 * (see {@link #protectedFrom}) until the tower is complete, when the openings clear again.
	 */
	public static void sealPortalRoom(ServerLevel level, String id, BoundingBox room) {
		var s = state(level.getServer(), id).orElse(null);
		if (s == null || s.complete || room == null) return;
		List<BlockPos> gates = new ArrayList<>(s.gates);
		for (BlockPos p : BlockPos.betweenClosed(room.minX(), room.minY(), room.minZ(), room.maxX(), room.maxY(), room.maxZ())) {
			boolean shell = p.getX() == room.minX() || p.getX() == room.maxX() || p.getY() == room.minY() || p.getY() == room.maxY()
					|| p.getZ() == room.minZ() || p.getZ() == room.maxZ();
			if (!shell) continue;
			var st = level.getBlockState(p);
			if (!st.getCollisionShape(level, p).isEmpty() && !(st.getBlock() instanceof net.minecraft.world.level.block.DoorBlock)) continue;
			if (st.is(net.minecraft.world.level.block.Blocks.END_PORTAL_FRAME) || st.is(net.minecraft.world.level.block.Blocks.END_PORTAL)) continue;
			level.setBlock(p, net.minecraft.world.level.block.Blocks.REINFORCED_DEEPSLATE.defaultBlockState(), 2);
			gates.add(p.immutable());
		}
		s.portalRoom = List.of(room.minX(), room.minY(), room.minZ(), room.maxX(), room.maxY(), room.maxZ());
		s.gates = List.copyOf(gates);
		save(level.getServer(), id, s);
		TowersMod.LOGGER.info("Tower {}: sealed the End portal room at {} ({} openings)", id, room, gates.size());
	}

	private static void openPortalRoom(MinecraftServer server, ServerLevel level, ChallengeDef def, ChallengeState s) {
		if (s.gates.isEmpty() || level == null) return;
		for (BlockPos p : s.gates) if (level.getBlockState(p).is(net.minecraft.world.level.block.Blocks.REINFORCED_DEEPSLATE)) level.setBlock(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
		BoundingBox room = s.portalRoomBox();
		if (room != null) {
			var c = room.getCenter();
			level.playSound(null, c, SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 1f, 1f);
		}
		TowersMod.LOGGER.info("Tower {}: the End portal room is open", def.name());
		s.gates = List.of();
		server.getPlayerList().broadcastSystemMessage(Component.literal("The way to the End beneath " + def.name() + " is open!")
				.withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD), false);
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

	/** The challenge a tower runs (older saves used the tower id as the challenge id). */
	static Optional<ChallengeDef> defOf(String towerId, ChallengeState s) {
		return ChallengeDef.get(s.challenge.isEmpty() ? towerId : s.challenge);
	}

	private static boolean inLevel(ChallengeState s, ServerLevel level) {
		return s.dimension.equals(level.dimension().identifier().toString());
	}

	private static ServerLevel level(MinecraftServer server, ChallengeState s) {
		return server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(s.dimension)));
	}

	private static String chestAt(ServerLevel level, BlockPos pos) {
		for (var e : states(level.getServer()).entrySet()) {
			var def = defOf(e.getKey(), e.getValue());
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

	/**
	 * Places the ruined structure with its corner at {@code origin}, in the palette for the biome there, on a
	 * foundation reaching down to solid ground, and starts the challenge from zero under {@code towerId}.
	 */
	public static boolean place(ServerLevel level, ChallengeDef def, BlockPos origin, String towerId) {
		var ruined = template(level, def.ruined());
		if (ruined.isEmpty()) {
			TowersMod.LOGGER.error("Challenge {}: structure {} not found", def.id(), def.ruined());
			return false;
		}
		Vec3i size = ruined.get().getSize();
		BlockPos center = origin.offset(size.getX() / 2, 1, size.getZ() / 2);
		TowerPalette palette = TowerPalette.pick(level.getBiome(center));
		// foundation: fill under the floor down to solid ground (or the sea floor), at most 64 blocks
		var foundation = palette.foundationState();
		for (int x = 0; x < size.getX(); x++) {
			for (int z = 0; z < size.getZ(); z++) {
				BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(origin.getX() + x, origin.getY() - 1, origin.getZ() + z);
				for (int d = 0; d < 64 && p.getY() > level.getMinY(); d++, p.move(0, -1, 0)) {
					var st = level.getBlockState(p);
					if (!st.canBeReplaced() && !st.is(net.minecraft.tags.BlockTags.LEAVES) && !st.is(net.minecraft.tags.BlockTags.LOGS) && st.getFluidState().isEmpty()) break;
					level.setBlock(p, foundation, 2);
				}
			}
		}
		ruined.get().placeInWorld(level, origin, origin, palette.settings(), level.getRandom(), 2);
		save(level.getServer(), towerId, ChallengeState.fresh(def.id(), palette.id(), level.dimension().identifier().toString(), origin));
		TowersMod.LOGGER.info("Tower {} ({}) placed at {} in {} with the {} palette", towerId, def.id(), origin.toShortString(),
				level.getBiome(center).unwrapKey().map(k -> k.identifier().toString()).orElse("?"), palette.id());
		return true;
	}

	/** Rebuilds the finished structure from the bottom up to the height of the given stage. */
	private static void buildStage(ServerLevel level, ChallengeDef def, ChallengeState s, int stage) {
		var finished = template(level, def.finished());
		if (finished.isEmpty()) {
			TowersMod.LOGGER.error("Challenge {}: structure {} not found", def.id(), def.finished());
			return;
		}
		BoundingBox full = box(level, def, s);
		int height = full.getYSpan();
		int top = s.origin.getY() + (int) Math.ceil(height * (double) stage / def.stages()) - 1;
		BoundingBox clip = new BoundingBox(full.minX(), full.minY(), full.minZ(), full.maxX(), Math.min(top, full.maxY()), full.maxZ());
		finished.get().placeInWorld(level, s.origin, s.origin, TowerPalette.get(s.palette).settings().setBoundingBox(clip), level.getRandom(), 2);
		var c = full.getCenter();
		level.sendParticles(ParticleTypes.CLOUD, c.getX() + 0.5, top, c.getZ() + 0.5, 120, full.getXSpan() / 2.0, 1, full.getZSpan() / 2.0, 0.05);
		level.playSound(null, c, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 1.5f, 0.7f);
	}

	// ---- offerings ----

	private static void open(ServerPlayer player, String id) {
		var s = state(player.level().getServer(), id).orElseThrow();
		var def = defOf(id, s).orElseThrow();
		if (s.complete) {
			player.sendSystemMessage(Component.literal(def.name() + " is complete! Thank you to everyone who helped.").withStyle(ChatFormatting.GOLD));
			sendLeaderboard(player.createCommandSourceStack(), id, def, s, player);
			return;
		}
		Component title = Component.literal("Offer " + def.unit() + ": " + s.total + "/" + def.goal());
		player.openMenu(new SimpleMenuProvider((syncId, inv, p) -> new TributeMenu(syncId, inv, new SimpleContainer(27), id), title));
	}

	/** Counts what a player put in; accepted items are used up, the rest is handed back. */
	static void offer(ServerPlayer player, String id, List<ItemStack> stacks) {
		var s = state(player.level().getServer(), id).orElse(null);
		var def = s == null ? null : defOf(id, s).orElse(null);
		int given = 0;
		List<ItemStack> back = new ArrayList<>();
		for (ItemStack stack : stacks) {
			int worth = def == null ? 0 : def.worth(stack);
			if (worth <= 0 || s == null || s.complete) {
				back.add(stack);
				continue;
			}
			int remaining = def.goal() - s.total - given;
			int take = Math.min(stack.getCount(), Math.max(0, (remaining + worth - 1) / worth));
			given += take * worth;
			if (take > 0) ChallengeState.addStacks(s.deposits.computeIfAbsent(player.getUUID().toString(), k -> new ArrayList<>()), stack.copyWithCount(take));
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
		var s = state(server, id).orElse(null);
		var def = s == null ? null : defOf(id, s).orElse(null);
		if (def == null || s == null || s.complete || amount <= 0) return;
		ServerLevel level = level(server, s);
		s.total = Math.min(def.goal(), s.total + amount);
		s.donations.merge(key, amount, Integer::sum);
		s.names.put(key, name);
		TowersMod.LOGGER.info("Challenge {}: {} gave {} {} ({}/{})", id, name, amount, def.unit(), s.total, def.goal());
		server.getPlayerList().broadcastSystemMessage(Component.literal(name + " offered " + amount + " " + def.unit() + " to " + def.name()
				+ " (" + s.total + "/" + def.goal() + ")").withStyle(ChatFormatting.YELLOW), false);

		while (s.stage < def.stages() && s.total >= def.threshold(s.stage + 1)) {
			s.stage++;
			if (level != null) buildStage(level, def, s, s.stage);
			TowersMod.LOGGER.info("Challenge {}: stage {}/{} built", id, s.stage, def.stages());
			if (s.stage < def.stages()) {
				server.getPlayerList().broadcastSystemMessage(Component.literal(def.name() + " rises! Stage " + s.stage + " of " + def.stages() + " rebuilt.")
						.withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD), false);
			}
		}
		if (s.total >= def.goal()) {
			s.complete = true;
			save(server, id, s);
			complete(server, level, id, def, s);
		} else {
			save(server, id, s);
		}
	}

	private static void complete(MinecraftServer server, ServerLevel level, String id, ChallengeDef def, ChallengeState s) {
		List<String> ranking = s.ranking();
		openPortalRoom(server, level, def, s);
		// the top donor claims the tower: only they can build in and around it now
		if (!ranking.isEmpty()) {
			s.owner = ranking.getFirst();
			server.getPlayerList().broadcastSystemMessage(Component.literal(s.names.getOrDefault(s.owner, "?") + " claims " + def.name()
					+ " as its top donor. Only they and their guild can build in and around it now.").withStyle(ChatFormatting.GOLD), false);
		}
		// the offering chest becomes the owner's base, and they get a second Base Core to put wherever they like
		if (makeBase(level, id, def, s) && !s.owner.isEmpty()) {
			server.getPlayerList().broadcastSystemMessage(Component.literal("The offering chest of " + def.name() + " has become a Base Core for "
					+ s.names.getOrDefault(s.owner, "?") + ", who also gets a Base Core of their own to place anywhere.").withStyle(ChatFormatting.GOLD), false);
		}
		// everything that was offered goes back to whoever gave it
		s.deposits.forEach((key, items) -> items.forEach(it -> ChallengeState.addStacks(s.returns.computeIfAbsent(key, k -> new ArrayList<>()), it)));
		if (!s.deposits.isEmpty()) {
			server.getPlayerList().broadcastSystemMessage(Component.literal("Everything offered to " + def.name() + " goes back to the players who gave it.")
					.withStyle(ChatFormatting.YELLOW), false);
		}
		s.deposits.clear();
		TowersMod.LOGGER.info("Challenge {} complete. Top donors: {}", id,
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
		save(server, id, s);
		for (ServerPlayer p : server.getPlayerList().getPlayers()) deliverPending(p);
	}

	/** Gives a player any rewards waiting for them (they may have been offline when a challenge finished). */
	static void deliverPending(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		String key = player.getUUID().toString();
		for (var e : states(server).entrySet()) {
			List<ItemStack> back = e.getValue().returns.remove(key);
			if (back != null && !back.isEmpty()) {
				int count = 0;
				for (ItemStack it : back) {
					count += it.getCount();
					player.getInventory().placeItemBackInInventory(it.copy(), Prediction.SERVER_ONLY);
				}
				String name = defOf(e.getKey(), e.getValue()).map(ChallengeDef::name).orElse(e.getKey());
				player.sendSystemMessage(Component.literal("The " + count + " items you offered to " + name + " came back to you"
						+ " (anything that didn't fit is at your feet).").withStyle(ChatFormatting.GOLD));
				save(server, e.getKey(), e.getValue());
			}
			List<ChallengeDef.RewardItem> items = e.getValue().pendingRewards.remove(key);
			if (items == null || items.isEmpty()) continue;
			for (ChallengeDef.RewardItem it : items) {
				var item = BuiltInRegistries.ITEM.getValue(it.item());
				player.getInventory().placeItemBackInInventory(new ItemStack(item, it.count()), Prediction.SERVER_ONLY);
			}
			String name = defOf(e.getKey(), e.getValue()).map(ChallengeDef::name).orElse(e.getKey());
			player.sendSystemMessage(Component.literal("You received your reward for helping rebuild " + name + "!").withStyle(ChatFormatting.GOLD));
			save(server, e.getKey(), e.getValue());
		}
	}

	// ---- leaderboard and commands ----

	static void sendLeaderboard(CommandSourceStack source, String id, ChallengeDef def, ChallengeState s, ServerPlayer viewer) {
		int pct = (int) (100L * s.total / def.goal());
		int bars = pct / 5;
		MutableComponent text = Component.literal("\n" + def.name()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
				.append(Component.literal("  " + id + " at " + s.origin.getX() + ", " + s.origin.getZ() + "\n").withStyle(ChatFormatting.DARK_GRAY))
				.append(Component.literal("█".repeat(bars)).withStyle(ChatFormatting.GREEN))
				.append(Component.literal("█".repeat(20 - bars)).withStyle(ChatFormatting.DARK_GRAY))
				.append(Component.literal(" " + s.total + "/" + def.goal() + " " + def.unit() + " (" + pct + "%), stage " + s.stage + "/" + def.stages()
						+ (s.complete ? " - COMPLETE" : "")).withStyle(ChatFormatting.WHITE));
		String owner = s.controller();
		text.append(Component.literal(owner.isEmpty() ? "\nProtected until rebuilt; the top donor then claims it."
				: "\nClaimed by " + s.names.getOrDefault(owner, "?") + " (only they and their guild can build around it).").withStyle(ChatFormatting.LIGHT_PURPLE));
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
		TowersMod.LOGGER.info("Challenge {} leaderboard: {}/{} stage {}; {}", id, s.total, def.goal(), s.stage,
				String.join(", ", ranking.stream().map(k -> s.names.getOrDefault(k, k) + " " + s.donations.get(k)).toList()));
	}

	public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
		var challenges = (com.mojang.brigadier.suggestion.SuggestionProvider<CommandSourceStack>) (ctx, b) -> SharedSuggestionProvider.suggest(ChallengeDef.all().keySet(), b);
		var towers = (com.mojang.brigadier.suggestion.SuggestionProvider<CommandSourceStack>) (ctx, b) -> SharedSuggestionProvider.suggest(states(ctx.getSource().getServer()).keySet(), b);
		dispatcher.register(Commands.literal("challenge")
				// the nearest tower's leaderboard
				.executes(ctx -> {
					var src = ctx.getSource();
					String best = null;
					double bestDist = Double.MAX_VALUE;
					for (var e : states(src.getServer()).entrySet()) {
						if (!inLevel(e.getValue(), src.getLevel()) || defOf(e.getKey(), e.getValue()).isEmpty()) continue;
						double d = e.getValue().origin.distToCenterSqr(src.getPosition());
						if (d < bestDist) {
							bestDist = d;
							best = e.getKey();
						}
					}
					if (best == null) {
						src.sendSuccess(() -> Component.literal("No towers have been found yet. They stand above the strongholds: follow an Eye of Ender."), false);
						return 0;
					}
					var s = states(src.getServer()).get(best);
					sendLeaderboard(src, best, defOf(best, s).get(), s, src.getPlayer());
					int count = states(src.getServer()).size();
					if (count > 1) src.sendSuccess(() -> Component.literal(count + " towers found so far: /challenge list").withStyle(ChatFormatting.GRAY), false);
					return 1;
				})
				.then(Commands.literal("list").executes(ctx -> {
					var src = ctx.getSource();
					MutableComponent text = Component.literal("Towers found so far:").withStyle(ChatFormatting.GOLD);
					List<String> ids = new ArrayList<>(states(src.getServer()).keySet());
					ids.sort(java.util.Comparator.naturalOrder());
					for (String id : ids) {
						var s = states(src.getServer()).get(id);
						var def = defOf(id, s);
						if (def.isEmpty()) continue;
						text.append(Component.literal("\n " + def.get().name() + " (" + id + ") at " + s.origin.getX() + ", " + s.origin.getZ() + ": "
								+ (s.complete ? "complete" : s.total + "/" + def.get().goal() + " " + def.get().unit())).withStyle(s.complete ? ChatFormatting.GREEN : ChatFormatting.WHITE));
					}
					if (ids.isEmpty()) text.append(Component.literal("\n none yet").withStyle(ChatFormatting.GRAY));
					src.sendSuccess(() -> text, false);
					return ids.size();
				}))
				.then(Commands.literal("top").then(Commands.argument("tower", StringArgumentType.word()).suggests(towers).executes(ctx -> {
					String id = StringArgumentType.getString(ctx, "tower");
					var s = state(ctx.getSource().getServer(), id);
					var def = s.flatMap(st -> defOf(id, st));
					if (def.isEmpty()) {
						ctx.getSource().sendFailure(Component.literal("No tower called " + id + "."));
						return 0;
					}
					sendLeaderboard(ctx.getSource(), id, def.get(), s.get(), ctx.getSource().getPlayer());
					return 1;
				})))
				// operator tools
				.then(Commands.literal("place").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(Commands.argument("challenge", StringArgumentType.word()).suggests(challenges)
								.executes(ctx -> placeCommand(ctx.getSource(), StringArgumentType.getString(ctx, "challenge"), StringArgumentType.getString(ctx, "challenge")))
								.then(Commands.argument("tower", StringArgumentType.word())
										.executes(ctx -> placeCommand(ctx.getSource(), StringArgumentType.getString(ctx, "challenge"), StringArgumentType.getString(ctx, "tower"))))))
				.then(Commands.literal("generate").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(Commands.literal("nearest").executes(ctx -> {
							var src = ctx.getSource();
							BlockPos found = src.getLevel().findNearestMapStructure(net.minecraft.tags.StructureTags.EYE_OF_ENDER_LOCATED, BlockPos.containing(src.getPosition()), 100, false);
							if (found == null) {
								src.sendFailure(Component.literal("No stronghold nearby."));
								return 0;
							}
							return generateCommand(src, StrongholdTowers.indexOf(src.getLevel(), net.minecraft.world.level.ChunkPos.containing(found)));
						}))
						.then(Commands.argument("number", IntegerArgumentType.integer(0)).executes(ctx ->
								generateCommand(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "number")))))
				.then(Commands.literal("remove").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(Commands.argument("tower", StringArgumentType.word()).suggests(towers).executes(ctx -> {
							String id = StringArgumentType.getString(ctx, "tower");
							save(ctx.getSource().getServer(), id, null);
							ctx.getSource().sendSuccess(() -> Component.literal("Stopped " + id + " (the blocks stay where they are)."), true);
							return 1;
						})))
				.then(Commands.literal("credit").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(Commands.argument("tower", StringArgumentType.word()).suggests(towers)
								.then(Commands.argument("player", StringArgumentType.word())
										.then(Commands.argument("amount", IntegerArgumentType.integer(1)).executes(ctx -> {
											String id = StringArgumentType.getString(ctx, "tower"), name = StringArgumentType.getString(ctx, "player");
											var server = ctx.getSource().getServer();
											if (state(server, id).isEmpty()) {
												ctx.getSource().sendFailure(Component.literal("No tower called " + id + "."));
												return 0;
											}
											ServerPlayer online = server.getPlayerList().getPlayerByName(name);
											String key = online != null ? online.getUUID().toString()
													: UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
											credit(server, id, key, online != null ? online.getPlainTextName() : name, IntegerArgumentType.getInteger(ctx, "amount"));
											return 1;
										}))))));
	}

	private static int placeCommand(CommandSourceStack src, String challenge, String tower) {
		var def = ChallengeDef.get(challenge);
		if (def.isEmpty()) {
			src.sendFailure(Component.literal("No challenge called " + challenge + "."));
			return 0;
		}
		BlockPos origin = BlockPos.containing(src.getPosition());
		if (!place(src.getLevel(), def.get(), origin, tower)) {
			src.sendFailure(Component.literal("Couldn't find the structure files for " + challenge + "."));
			return 0;
		}
		src.sendSuccess(() -> Component.literal("Placed " + def.get().name() + " as " + tower + " at " + origin.toShortString()
				+ ". Its offering chest is at " + origin.offset(def.get().chest()).toShortString() + "."), true);
		return 1;
	}

	private static int generateCommand(CommandSourceStack src, int index) {
		BlockPos pos = StrongholdTowers.generate(src.getLevel(), index);
		if (pos == null) {
			src.sendFailure(Component.literal("Couldn't build a tower for stronghold " + index + "."));
			return 0;
		}
		src.sendSuccess(() -> Component.literal("Tower " + StrongholdTowers.towerId(index) + " stands at " + pos.toShortString() + "."), true);
		return 1;
	}
}
