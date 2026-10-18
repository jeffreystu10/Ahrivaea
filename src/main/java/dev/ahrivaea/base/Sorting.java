package dev.ahrivaea.base;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * How Transport workers keep a base's chests sorted. Put an item frame on a chest or barrel with an item in it and
 * the chest is labelled: it takes that item, and everything in the same category (a frame with oak logs takes every
 * log; one with raw iron, every raw ore ...). Chests without a frame take anything. Carrying something, a worker
 * picks, in order: a labelled chest for that exact item, a labelled chest for its category, an unlabelled chest that
 * already holds some, then the nearest unlabelled chest with room. It never puts things in a chest labelled for
 * something else. When it has nothing else to do it tidies up: anything sitting in an unlabelled chest (or the wrong
 * labelled one) that has a labelled chest is carried there.
 */
public final class Sorting {
	private Sorting() {}

	private static TagKey<Item> c(String path) {
		return TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", path));
	}

	/** The categories a label covers (an item matches a label if it's the same item or shares one of these). */
	public static final List<TagKey<Item>> CATEGORIES = List.of(
			ItemTags.LOGS, ItemTags.PLANKS, ItemTags.SAPLINGS, ItemTags.LEAVES, ItemTags.WOOL, ItemTags.FISHES, ItemTags.COALS,
			ItemTags.ARROWS, ItemTags.SAND, ItemTags.DIRT, ItemTags.MEAT, ItemTags.EGGS, ItemTags.MUSHROOMS, ItemTags.SWORDS, ItemTags.AXES,
			ItemTags.PICKAXES, ItemTags.SHOVELS, ItemTags.HOES, ItemTags.BOATS, ItemTags.CANDLES, ItemTags.TERRACOTTA, ItemTags.BEDS,
			ItemTags.SIGNS, ItemTags.RAILS, ItemTags.BANNERS,
			c("ores"), c("raw_materials"), c("ingots"), c("gems"), c("nuggets"), c("dusts"), c("seeds"), c("crops"), c("foods"),
			c("potions"), c("dyes"), c("stones"), c("cobblestones"), c("music_discs"), c("armors"), c("enchanted_books"), c("glass_blocks"));

	public enum Match { NONE, CATEGORY, EXACT }

	/** How well an item fits a label. */
	public static Match match(ItemStack label, ItemStack stack) {
		if (label.is(stack.getItem())) return Match.EXACT;
		for (TagKey<Item> tag : CATEGORIES) if (label.is(tag) && stack.is(tag)) return Match.CATEGORY;
		return Match.NONE;
	}

	/** How well an item fits a container's labels (NONE for an unlabelled container: see {@link #labels}). */
	public static Match match(List<ItemStack> labels, ItemStack stack) {
		Match best = Match.NONE;
		for (ItemStack l : labels) {
			Match m = match(l, stack);
			if (m.ordinal() > best.ordinal()) best = m;
		}
		return best;
	}

	/** The positions that make up the container at p: both halves of a double chest. */
	public static List<BlockPos> parts(ServerLevel level, BlockPos p) {
		List<BlockPos> out = new ArrayList<>(List.of(p));
		BlockState s = level.getBlockState(p);
		if (s.getBlock() instanceof ChestBlock && s.hasProperty(ChestBlock.TYPE) && s.getValue(ChestBlock.TYPE) != ChestType.SINGLE)
			out.add(p.relative(ChestBlock.getConnectedDirection(s)));
		return out;
	}

	/** The items in the item frames hung on a container (either half of a double chest). Empty: it takes anything. */
	public static List<ItemStack> labels(ServerLevel level, BlockPos p) {
		List<ItemStack> out = new ArrayList<>();
		for (BlockPos part : parts(level, p)) {
			for (ItemFrame f : level.getEntitiesOfClass(ItemFrame.class, new AABB(part).inflate(1),
					f -> f.getPos().relative(f.getDirection().getOpposite()).equals(part))) {
				if (!f.getItem().isEmpty()) out.add(f.getItem());
			}
		}
		return out;
	}

	static boolean hasRoom(Container c, ItemStack stack) {
		for (int i = 0; i < c.getContainerSize(); i++) {
			ItemStack in = c.getItem(i);
			if (in.isEmpty() || ItemStack.isSameItemSameComponents(in, stack) && in.getCount() < in.getMaxStackSize()) return true;
		}
		return false;
	}

	static boolean holds(Container c, ItemStack stack) {
		for (int i = 0; i < c.getContainerSize(); i++) if (ItemStack.isSameItemSameComponents(c.getItem(i), stack)) return true;
		return false;
	}

	/**
	 * Where a worker at {@code from} should put {@code stack}, or null if nowhere has room. Skips the container at
	 * {@code except} (and its other half), and when {@code labelledOnly} only looks at chests labelled for it.
	 */
	public static BlockPos chestFor(ServerLevel level, Set<BlockPos> containers, ItemStack stack, BlockPos from, BlockPos except, boolean labelledOnly) {
		List<BlockPos> skip = except == null ? List.of() : parts(level, except);
		BlockPos best = null;
		int bestRank = -1;
		double bestD = Double.MAX_VALUE;
		for (BlockPos p : containers) {
			if (skip.contains(p) || !level.isLoaded(p) || quarryChest(level, p)) continue;
			Container c = HopperBlockEntity.getContainerAt(level, p);
			if (c == null || !hasRoom(c, stack)) continue;
			List<ItemStack> labels = labels(level, p);
			int rank;
			if (!labels.isEmpty()) {
				Match m = match(labels, stack);
				if (m == Match.NONE) continue; // labelled for something else
				rank = m == Match.EXACT ? 4 : 3;
			} else {
				if (labelledOnly) continue;
				rank = holds(c, stack) ? 2 : 1;
			}
			double d = p.distSqr(from);
			if (rank > bestRank || rank == bestRank && d < bestD) {
				best = p;
				bestRank = rank;
				bestD = d;
			}
		}
		return best;
	}

	/** A chest beside a Mining Post holds its quarry's finds: nothing else is put in it (things are taken out, though). */
	public static boolean quarryChest(ServerLevel level, BlockPos p) {
		for (BlockPos part : parts(level, p))
			for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values())
				if (level.getBlockState(part.relative(d)).is(Bases.MINING_POST)) return true;
		return false;
	}

	/** Something to tidy: a stack in a container that belongs in a labelled chest elsewhere. */
	public record Misplaced(BlockPos from, int slot, BlockPos to) {}

	/** The first stack in {@code sources} that has a better place among {@code containers}, or null. */
	public static Misplaced misplaced(ServerLevel level, Set<BlockPos> sources, Set<BlockPos> containers, BlockPos near) {
		for (BlockPos p : sources) {
			if (!level.isLoaded(p)) continue;
			Container c = HopperBlockEntity.getContainerAt(level, p);
			if (c == null) continue;
			List<ItemStack> labels = labels(level, p);
			for (int i = 0; i < c.getContainerSize(); i++) {
				ItemStack s = c.getItem(i);
				if (s.isEmpty()) continue;
				Match here = labels.isEmpty() ? Match.NONE : match(labels, s);
				if (here == Match.EXACT) continue;
				BlockPos to = chestFor(level, containers, s, near, p, true);
				if (to == null) continue;
				// only move it to a better fit (an exact label beats a category label beats none)
				Match there = match(labels(level, to), s);
				if (there.ordinal() > here.ordinal()) return new Misplaced(p, i, to);
			}
		}
		return null;
	}
}
