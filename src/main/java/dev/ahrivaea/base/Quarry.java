package dev.ahrivaea.base;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;

import java.util.ArrayList;
import java.util.List;

/**
 * A quarry: the chunk a Mining Post stands in, dug out by the base's miners from just below the post down to
 * bedrock until only a one-block wall around the chunk's edge is left (and the staircase). First they cut a
 * staircase that spirals down inside the wall to the bottom; then they clear the chunk a layer at a time from the
 * top. Every block goes into the chests and barrels touching the post; when they're full the miners stop and the
 * owner is told to add a chest.
 * <p>
 * Never dug: bedrock and anything unbreakable, spawners (and trial spawners and vaults), anything in a stronghold,
 * blocks players placed (see {@link PlacedBlocks}), and other structures (ancient cities, mineshafts, trial chambers
 * ...) unless the quarry is set to dig them. Water and lava in the way are drained, and gaps in the wall are filled
 * with stone so nothing flows or falls in.
 */
public final class Quarry {
	public static final int STAIRS = 0, LAYERS = 1, DONE = 2;

	public int phase;
	/** The next stair step to cut (STAIRS). */
	public int step;
	/** The layer being cleared (LAYERS); Integer.MIN_VALUE until it starts. */
	public int layer = Integer.MIN_VALUE;
	/** Also dig through structures other than strongholds. */
	public boolean structures;
	public boolean paused;
	public long mined;
	/** Not saved: waiting for room in a chest, and when the owner was last told. */
	public boolean needsChest;
	public long lastAlert;

	public static final Codec<Quarry> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.optionalFieldOf("phase", 0).forGetter(q -> q.phase),
			Codec.INT.optionalFieldOf("step", 0).forGetter(q -> q.step),
			Codec.INT.optionalFieldOf("layer", Integer.MIN_VALUE).forGetter(q -> q.layer),
			Codec.BOOL.optionalFieldOf("structures", false).forGetter(q -> q.structures),
			Codec.BOOL.optionalFieldOf("paused", false).forGetter(q -> q.paused),
			Codec.LONG.optionalFieldOf("mined", 0L).forGetter(q -> q.mined)
	).apply(i, (phase, step, layer, structures, paused, mined) -> {
		Quarry q = new Quarry();
		q.phase = phase;
		q.step = step;
		q.layer = layer;
		q.structures = structures;
		q.paused = paused;
		q.mined = mined;
		return q;
	}));

	// ---- the shape ----

	public static int top(BlockPos post) {
		return post.getY() - 1;
	}

	/** The lowest layer dug: just above the world's bottom (bedrock itself is never dug). */
	public static int bottom(ServerLevel level) {
		return level.getMinY() + 1;
	}

	/** The cells just inside the wall, in walking order around the chunk (52 of them). */
	private static final int[][] RING = ring();

	private static int[][] ring() {
		List<int[]> out = new ArrayList<>();
		for (int x = 1; x <= 14; x++) out.add(new int[]{x, 1});
		for (int z = 2; z <= 14; z++) out.add(new int[]{14, z});
		for (int x = 13; x >= 1; x--) out.add(new int[]{x, 14});
		for (int z = 13; z >= 2; z--) out.add(new int[]{1, z});
		return out.toArray(new int[0][]);
	}

	/** Stair step i: the block you stand on (its floor). Step 0 is one below the top layer, by the post's corner. */
	public static BlockPos stepFloor(BlockPos post, int i) {
		int[] c = RING[i % RING.length];
		int bx = (post.getX() >> 4) << 4, bz = (post.getZ() >> 4) << 4;
		return new BlockPos(bx + c[0], top(post) - 1 - i, bz + c[1]);
	}

	public static int steps(ServerLevel level, BlockPos post) {
		return Math.max(0, top(post) - 1 - bottom(level));
	}

	/** Whether this is a stair step's floor (kept when the layers are cleared). */
	public static boolean isStep(BlockPos post, BlockPos p) {
		int i = top(post) - 1 - p.getY();
		return i >= 0 && stepFloor(post, i).equals(p);
	}

	public static boolean inChunk(BlockPos post, BlockPos p) {
		return (p.getX() >> 4) == (post.getX() >> 4) && (p.getZ() >> 4) == (post.getZ() >> 4);
	}

	/** Whether a spot is inside the quarry's hole (a worker down there is still at work, not lost). */
	public static boolean inHole(ServerLevel level, BlockPos post, BlockPos p) {
		return inChunk(post, p) && p.getY() <= top(post) + 1 && p.getY() >= level.getMinY();
	}

	// ---- what may be dug ----

	/** Why a block can't be dug, or null if it can. */
	public String keep(ServerLevel level, BlockPos p, BlockState s) {
		if (s.isAir()) return "air";
		if (s.getDestroySpeed(level, p) < 0 || s.is(Blocks.BEDROCK)) return "unbreakable";
		if (s.is(Blocks.SPAWNER) || s.is(Blocks.TRIAL_SPAWNER) || s.is(Blocks.VAULT) || s.is(Blocks.END_PORTAL_FRAME)
				|| s.is(Blocks.END_PORTAL) || s.is(Blocks.NETHER_PORTAL) || s.is(Bases.BASE_CORE) || s.is(Bases.MINING_POST)) return "kept";
		if (PlacedBlocks.isPlaced(level, p)) return "placed by a player";
		var sm = level.structureManager();
		if (sm.getStructureWithPieceAt(p, h -> h.is(BuiltinStructures.STRONGHOLD)).isValid()) return "stronghold";
		if (!structures && sm.getStructureWithPieceAt(p, h -> true).isValid()) return "structure";
		return null;
	}

	// ---- the work ----

	/** What happens next: a block to dig, or null when there's nothing (yet) and the phase moved on or it's done. */
	public BlockPos next(ServerLevel level, BlockPos post, BlockPos near, java.util.function.Predicate<BlockPos> free) {
		if (paused || phase == DONE) return null;
		if (phase == STAIRS) {
			int total = steps(level, post);
			while (step < total) {
				BlockPos floor = stepFloor(post, step);
				if (!level.isLoaded(floor)) return null;
				// a step to stand on (stone if there's a gap), and headroom above it
				BlockState f = level.getBlockState(floor);
				if ((f.isAir() || !f.getFluidState().isEmpty() || f.getCollisionShape(level, floor).isEmpty()) && !inStructure(level, floor))
					level.setBlockAndUpdate(floor, filler(floor));
				for (int h = 1; h <= 3; h++) {
					BlockPos head = floor.above(h);
					if (head.getY() > top(post)) break;
					BlockState s = level.getBlockState(head);
					if (!s.getFluidState().isEmpty()) drain(level, head);
					sealAround(level, post, head);
					if (keep(level, head, level.getBlockState(head)) == null && free.test(head)) return head;
					if (keep(level, head, level.getBlockState(head)) == null) return null; // a miner is on it already
				}
				step++;
			}
			phase = LAYERS;
			layer = top(post);
		}
		while (layer >= bottom(level)) {
			int bx = (post.getX() >> 4) << 4, bz = (post.getZ() >> 4) << 4;
			// the wall first: stone in any gap, so nothing flows or falls in; then drain the layer
			for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
				BlockPos p = new BlockPos(bx + x, layer, bz + z);
				boolean wall = x == 0 || x == 15 || z == 0 || z == 15;
				BlockState s = level.getBlockState(p);
				if (wall) {
					if ((s.isAir() || !s.getFluidState().isEmpty() || s.is(BlockTags.SAND) || s.is(Blocks.GRAVEL)) && !inStructure(level, p)) level.setBlockAndUpdate(p, filler(p));
				} else if (!s.getFluidState().isEmpty() && !isStep(post, p) && !inStructure(level, p)) drain(level, p);
			}
			BlockPos best = null;
			double bestD = Double.MAX_VALUE;
			boolean busy = false;
			for (int x = 1; x <= 14; x++) for (int z = 1; z <= 14; z++) {
				BlockPos p = new BlockPos(bx + x, layer, bz + z);
				if (isStep(post, p) || keep(level, p, level.getBlockState(p)) != null) continue;
				if (!free.test(p)) {
					busy = true;
					continue;
				}
				double d = p.distSqr(near);
				if (d < bestD) {
					best = p;
					bestD = d;
				}
			}
			if (best != null) return best;
			if (busy) return null; // others are finishing this layer
			layer--;
		}
		phase = DONE;
		return null;
	}

	/** Inside any structure (nothing is added or drained there). */
	static boolean inStructure(ServerLevel level, BlockPos p) {
		return level.structureManager().getStructureWithPieceAt(p, h -> true).isValid();
	}

	/** Stone to patch a gap with: deepslate down deep, cobblestone above, netherrack and end stone elsewhere. */
	static BlockState filler(BlockPos p) {
		return p.getY() < 0 ? Blocks.COBBLED_DEEPSLATE.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState();
	}

	private static void drain(ServerLevel level, BlockPos p) {
		BlockState s = level.getBlockState(p);
		if (s.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED))
			level.setBlockAndUpdate(p, s.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED, false));
		else if (s.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock || s.is(Blocks.BUBBLE_COLUMN)) level.setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
	}

	/** Seals water and lava next to a stair cut that's outside the quarry's inside (in the wall). */
	private static void sealAround(ServerLevel level, BlockPos post, BlockPos p) {
		for (Direction d : Direction.values()) {
			BlockPos n = p.relative(d);
			if (!level.getBlockState(n).getFluidState().isEmpty() && (!inChunk(post, n) || isWall(post, n))) level.setBlockAndUpdate(n, filler(n));
		}
	}

	private static boolean isWall(BlockPos post, BlockPos p) {
		int x = p.getX() & 15, z = p.getZ() & 15;
		return x == 0 || x == 15 || z == 0 || z == 15;
	}

	/** The chests and barrels touching the post (both halves of a double chest count once). */
	public static List<Container> chests(ServerLevel level, BlockPos post) {
		List<Container> out = new ArrayList<>();
		for (Direction d : Direction.values()) {
			Container c = HopperBlockEntity.getContainerAt(level, post.relative(d));
			if (c != null && !(c instanceof net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity) && !out.contains(c)) out.add(c);
		}
		return out;
	}

	/** What a block gives when a miner digs it: as with a diamond pickaxe (silk touch if it has it), plus whatever a container held. */
	static List<ItemStack> drops(ServerLevel level, BlockPos p, BlockState s, boolean silkTouch) {
		ItemStack tool = new ItemStack(Items.DIAMOND_PICKAXE);
		if (silkTouch) level.registryAccess().lookup(Registries.ENCHANTMENT).flatMap(l -> l.get(Enchantments.SILK_TOUCH)).ifPresent(e -> tool.enchant(e, 1));
		BlockEntity be = s.hasBlockEntity() ? level.getBlockEntity(p) : null;
		List<ItemStack> out = new ArrayList<>(Block.getDrops(s, level, p, be, null, tool));
		if (be instanceof Container c) for (int i = 0; i < c.getContainerSize(); i++) if (!c.getItem(i).isEmpty()) out.add(c.getItem(i).copy());
		out.removeIf(ItemStack::isEmpty);
		return out;
	}

	/** Whether the chests have room for all of these (checked before anything is dug). */
	static boolean fits(List<Container> chests, List<ItemStack> stacks) {
		// simulate on copies of the free space
		List<ItemStack[]> slots = new ArrayList<>();
		for (Container c : chests) {
			ItemStack[] copy = new ItemStack[c.getContainerSize()];
			for (int i = 0; i < copy.length; i++) copy[i] = c.getItem(i).copy();
			slots.add(copy);
		}
		for (ItemStack s : stacks) {
			int left = s.getCount();
			for (ItemStack[] chest : slots) {
				for (int i = 0; i < chest.length && left > 0; i++) {
					if (chest[i].isEmpty()) {
						int n = Math.min(left, s.getMaxStackSize());
						chest[i] = s.copyWithCount(n);
						left -= n;
					} else if (ItemStack.isSameItemSameComponents(chest[i], s)) {
						int n = Math.min(left, chest[i].getMaxStackSize() - chest[i].getCount());
						chest[i].grow(n);
						left -= n;
					}
				}
			}
			if (left > 0) return false;
		}
		return true;
	}

	static void store(List<Container> chests, List<ItemStack> stacks) {
		for (ItemStack s : stacks) {
			ItemStack left = s.copy();
			for (Container c : chests) {
				left = HopperBlockEntity.addItem(null, c, left, null);
				if (left.isEmpty()) break;
			}
		}
	}

	/** How far along it is (0-100). */
	public int percent(ServerLevel level, BlockPos post) {
		if (phase == DONE) return 100;
		int depth = Math.max(1, top(post) - bottom(level) + 1);
		if (phase == STAIRS) return (int) (10L * step / Math.max(1, steps(level, post)));
		return 10 + (int) (90L * (top(post) - layer) / depth);
	}

	public String describe(ServerLevel level, BlockPos post) {
		if (paused) return "Paused";
		if (needsChest) return "Stopped: the chests by the post are full. Put another chest next to it.";
		return switch (phase) {
			case STAIRS -> "Cutting the staircase: step " + step + " of " + steps(level, post);
			case LAYERS -> "Clearing layer y=" + layer + " (" + percent(level, post) + "% done)";
			default -> "Done: dug down to bedrock";
		};
	}
}
