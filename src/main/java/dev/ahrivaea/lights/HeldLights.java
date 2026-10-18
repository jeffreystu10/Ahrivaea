package dev.ahrivaea.lights;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import net.minecraft.world.level.material.Fluids;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lights that follow players. While a player holds something that gives off light (a torch, lantern, glowstone,
 * a lava bucket, glow berries ...) in either hand, an invisible light block of that brightness sits in the air at
 * their head (or feet), moving with them; underwater it's a waterlogged one. When they put it away, move on, log
 * off or die, it's taken away again. Because the light is real, everyone sees it, players without the mod too.
 * <p>
 * Where the lights are is saved with each dimension, so any left behind by a crash are cleared when their chunk
 * next loads. Game rule {@code ahrivaea:held_lights false} turns it off.
 */
public final class HeldLights {
	private HeldLights() {}

	public static final GameRule<Boolean> ENABLED = GameRuleBuilder.forBoolean(true).category(GameRuleCategory.PLAYER)
			.buildAndRegister(Identifier.fromNamespaceAndPath("ahrivaea", "held_lights"));

	/** Light blocks this mod has put down in a dimension (saved, so leftovers can be cleared). */
	public static final AttachmentType<List<Long>> PLACED = AttachmentRegistry.create(Identifier.fromNamespaceAndPath("ahrivaea", "held_lights"),
			b -> b.persistent(Codec.LONG.listOf()).initializer(List::of));

	/** Things that glow but don't place a glowing block (block items use their block's own light). */
	public static final Map<Item, Integer> GLOWING = Map.ofEntries(
			Map.entry(Items.LAVA_BUCKET, 15), Map.entry(Items.GLOW_BERRIES, 14), Map.entry(Items.NETHER_STAR, 12), Map.entry(Items.END_CRYSTAL, 12),
			Map.entry(Items.BLAZE_ROD, 10), Map.entry(Items.FIRE_CHARGE, 10), Map.entry(Items.BLAZE_POWDER, 8), Map.entry(Items.GLOWSTONE_DUST, 8),
			Map.entry(Items.EXPERIENCE_BOTTLE, 6), Map.entry(Items.GLOW_INK_SAC, 6), Map.entry(Items.MAGMA_CREAM, 6), Map.entry(Items.SPECTRAL_ARROW, 6));

	private record Lit(ResourceKey<Level> dimension, BlockPos pos) {}

	private static final Map<UUID, Lit> LIT = new HashMap<>();
	/** Positions saved from before this server started: cleared as their chunks load. */
	private static final Map<ResourceKey<Level>, List<BlockPos>> LEFTOVER = new HashMap<>();
	private static final List<Lit> CLEAR = new ArrayList<>();

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(HeldLights::tick);
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			LIT.clear();
			LEFTOVER.clear();
			for (ServerLevel level : server.getAllLevels()) {
				List<Long> saved = level.getAttachedOrCreate(PLACED);
				if (saved.isEmpty()) continue;
				List<BlockPos> list = new ArrayList<>();
				for (long l : saved) list.add(BlockPos.of(l));
				LEFTOVER.put(level.dimension(), list);
			}
		});
		// leftovers in a chunk that just loaded are cleared at the end of the tick (not while it's loading)
		ServerChunkEvents.CHUNK_LOAD.register((level, chunk, generated) -> {
			List<BlockPos> left = LEFTOVER.get(level.dimension());
			if (left == null || left.isEmpty()) return;
			for (var it = left.iterator(); it.hasNext(); ) {
				BlockPos p = it.next();
				if ((p.getX() >> 4) == chunk.getPos().x() && (p.getZ() >> 4) == chunk.getPos().z()) {
					synchronized (CLEAR) {
						CLEAR.add(new Lit(level.dimension(), p));
					}
					it.remove();
				}
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			// take every light down before saving
			for (var e : new ArrayList<>(LIT.entrySet())) remove(server, e.getValue());
			LIT.clear();
		});
	}

	/** How brightly a held item glows (0 for not at all). */
	public static int lightOf(ItemStack s) {
		if (s.isEmpty()) return 0;
		Integer glow = GLOWING.get(s.getItem());
		if (glow != null) return glow;
		if (s.getItem() instanceof BlockItem bi) return bi.getBlock().defaultBlockState().getLightEmission();
		return 0;
	}

	public static int lightOf(ServerPlayer p) {
		return Math.max(lightOf(p.getMainHandItem()), lightOf(p.getOffhandItem()));
	}

	private static void tick(MinecraftServer server) {
		List<Lit> clear;
		synchronized (CLEAR) {
			clear = new ArrayList<>(CLEAR);
			CLEAR.clear();
		}
		for (Lit l : clear) remove(server, l);
		Map<UUID, Boolean> seen = new HashMap<>();
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			seen.put(p.getUUID(), true);
			ServerLevel level = p.level();
			int light = level.getGameRules().get(ENABLED) && p.isAlive() && !p.isSpectator() ? lightOf(p) : 0;
			Lit now = LIT.get(p.getUUID());
			BlockPos spot = light > 0 ? spot(level, p, now) : null;
			if (now != null && spot != null && now.dimension() == level.dimension() && now.pos().equals(spot)
					&& level.getBlockState(spot).getBlock() instanceof LightBlock && level.getBlockState(spot).getValue(LightBlock.LEVEL) == light) continue;
			if (now != null) {
				remove(server, now);
				LIT.remove(p.getUUID());
			}
			if (spot != null && place(level, spot, light)) LIT.put(p.getUUID(), new Lit(level.dimension(), spot));
		}
		// players who left
		for (var it = LIT.entrySet().iterator(); it.hasNext(); ) {
			var e = it.next();
			if (!seen.containsKey(e.getKey())) {
				remove(server, e.getValue());
				it.remove();
			}
		}
	}

	/** Where a player's light goes: their head, else their feet, else just above their head; their current light counts as free. */
	private static BlockPos spot(ServerLevel level, ServerPlayer p, Lit now) {
		BlockPos eye = BlockPos.containing(p.getEyePosition());
		for (BlockPos c : new BlockPos[]{eye, p.blockPosition(), eye.above()}) {
			if (now != null && now.dimension() == level.dimension() && now.pos().equals(c)) return c;
			if (free(level, c)) return c;
		}
		return null;
	}

	/** Empty air, or still water (a light there is waterlogged). */
	private static boolean free(ServerLevel level, BlockPos p) {
		if (!level.isInWorldBounds(p)) return false;
		BlockState s = level.getBlockState(p);
		return s.isAir() || s.is(Blocks.WATER) && s.getFluidState().isSource();
	}

	private static boolean place(ServerLevel level, BlockPos p, int light) {
		BlockState s = level.getBlockState(p);
		boolean water = s.is(Blocks.WATER);
		if (!s.isAir() && !water && !(s.getBlock() instanceof LightBlock)) return false;
		if (s.getBlock() instanceof LightBlock) water = s.getValue(LightBlock.WATERLOGGED);
		level.setBlock(p, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, light).setValue(LightBlock.WATERLOGGED, water), Block.UPDATE_CLIENTS);
		remember(level, p, true);
		return true;
	}

	private static void remove(MinecraftServer server, Lit l) {
		ServerLevel level = server.getLevel(l.dimension());
		if (level == null) return;
		remember(level, l.pos(), false);
		if (!level.isLoaded(l.pos())) return;
		BlockState s = level.getBlockState(l.pos());
		if (!(s.getBlock() instanceof LightBlock)) return; // something else is there now
		level.setBlock(l.pos(), s.getValue(LightBlock.WATERLOGGED) ? Fluids.WATER.defaultFluidState().createLegacyBlock() : Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
	}

	private static void remember(ServerLevel level, BlockPos p, boolean placed) {
		List<Long> list = level.getAttachedOrCreate(PLACED);
		boolean has = list.contains(p.asLong());
		if (has == placed) return;
		List<Long> copy = new ArrayList<>(list);
		if (placed) copy.add(p.asLong());
		else copy.remove((Long) p.asLong());
		level.setAttached(PLACED, copy);
	}

	/** Where a player's light is right now (for tests), or null. */
	public static BlockPos litAt(ServerPlayer p) {
		Lit l = LIT.get(p.getUUID());
		return l == null ? null : l.pos();
	}
}
