package dev.ahrivaea.base;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * What a Mining worker gets from one turn at a Mining Post. Depends on where the post is: the Nether and the End have
 * their own stone and treasures; in the overworld, posts deep underground (below y=0) find deepslate and more diamonds.
 */
public final class MiningLoot {
	private MiningLoot() {}

	private record Entry(Item item, int weight, int min, int max) {}

	private static final List<Entry> SURFACE = List.of(
			new Entry(Items.COBBLESTONE, 40, 2, 4), new Entry(Items.STONE, 10, 1, 2), new Entry(Items.GRAVEL, 6, 1, 2),
			new Entry(Items.FLINT, 4, 1, 1), new Entry(Items.COAL, 14, 1, 3), new Entry(Items.RAW_COPPER, 10, 1, 3),
			new Entry(Items.RAW_IRON, 9, 1, 2), new Entry(Items.RAW_GOLD, 3, 1, 1), new Entry(Items.REDSTONE, 3, 2, 4),
			new Entry(Items.LAPIS_LAZULI, 2, 2, 4), new Entry(Items.EMERALD, 1, 1, 1), new Entry(Items.DIAMOND, 1, 1, 1));
	private static final List<Entry> DEEP = List.of(
			new Entry(Items.COBBLED_DEEPSLATE, 36, 2, 4), new Entry(Items.TUFF, 6, 1, 2), new Entry(Items.COAL, 6, 1, 2),
			new Entry(Items.RAW_IRON, 12, 1, 3), new Entry(Items.RAW_COPPER, 4, 1, 2), new Entry(Items.RAW_GOLD, 6, 1, 2),
			new Entry(Items.REDSTONE, 8, 3, 6), new Entry(Items.LAPIS_LAZULI, 5, 3, 6), new Entry(Items.DIAMOND, 3, 1, 1),
			new Entry(Items.AMETHYST_SHARD, 3, 1, 3), new Entry(Items.EMERALD, 1, 1, 1));
	private static final List<Entry> NETHER = List.of(
			new Entry(Items.NETHERRACK, 34, 2, 5), new Entry(Items.BLACKSTONE, 8, 1, 3), new Entry(Items.BASALT, 6, 1, 3),
			new Entry(Items.SOUL_SAND, 5, 1, 2), new Entry(Items.QUARTZ, 14, 1, 3), new Entry(Items.GOLD_NUGGET, 14, 2, 6),
			new Entry(Items.GLOWSTONE_DUST, 7, 1, 3), new Entry(Items.MAGMA_CREAM, 2, 1, 1), new Entry(Items.ANCIENT_DEBRIS, 1, 1, 1));
	private static final List<Entry> END = List.of(
			new Entry(Items.END_STONE, 50, 2, 4), new Entry(Items.OBSIDIAN, 8, 1, 1), new Entry(Items.CHORUS_FRUIT, 10, 1, 2),
			new Entry(Items.ENDER_PEARL, 4, 1, 1), new Entry(Items.SHULKER_SHELL, 1, 1, 1));

	public static List<ItemStack> roll(ServerLevel level, BlockPos post, RandomSource r) {
		return roll(level, post, r, false);
	}

	/**
	 * With silk touch the blocks come out whole: stone instead of cobblestone, ore blocks instead of raw ore, coal,
	 * diamonds and so on (one ore block per find; stone keeps its count), glowstone instead of dust.
	 */
	public static List<ItemStack> roll(ServerLevel level, BlockPos post, RandomSource r, boolean silkTouch) {
		boolean deep = level.dimension() == Level.OVERWORLD && post.getY() < 0;
		List<Entry> table = level.dimension() == Level.NETHER ? NETHER : level.dimension() == Level.END ? END : deep ? DEEP : SURFACE;
		int total = table.stream().mapToInt(Entry::weight).sum(), roll = r.nextInt(total);
		for (Entry e : table) {
			roll -= e.weight();
			if (roll >= 0) continue;
			int count = e.min() + r.nextInt(e.max() - e.min() + 1);
			if (silkTouch) {
				Item whole = silk(e.item(), deep);
				if (whole != null) return List.of(new ItemStack(whole, isOre(whole) ? 1 : count));
			}
			return List.of(new ItemStack(e.item(), count));
		}
		return List.of();
	}

	/** What a find is with silk touch, or null if it's the same. */
	static Item silk(Item item, boolean deep) {
		if (item == Items.COBBLESTONE) return Items.STONE;
		if (item == Items.COBBLED_DEEPSLATE) return Items.DEEPSLATE;
		if (item == Items.FLINT) return Items.GRAVEL;
		if (item == Items.COAL) return deep ? Items.DEEPSLATE_COAL_ORE : Items.COAL_ORE;
		if (item == Items.RAW_COPPER) return deep ? Items.DEEPSLATE_COPPER_ORE : Items.COPPER_ORE;
		if (item == Items.RAW_IRON) return deep ? Items.DEEPSLATE_IRON_ORE : Items.IRON_ORE;
		if (item == Items.RAW_GOLD) return deep ? Items.DEEPSLATE_GOLD_ORE : Items.GOLD_ORE;
		if (item == Items.REDSTONE) return deep ? Items.DEEPSLATE_REDSTONE_ORE : Items.REDSTONE_ORE;
		if (item == Items.LAPIS_LAZULI) return deep ? Items.DEEPSLATE_LAPIS_ORE : Items.LAPIS_ORE;
		if (item == Items.EMERALD) return deep ? Items.DEEPSLATE_EMERALD_ORE : Items.EMERALD_ORE;
		if (item == Items.DIAMOND) return deep ? Items.DEEPSLATE_DIAMOND_ORE : Items.DIAMOND_ORE;
		if (item == Items.AMETHYST_SHARD) return Items.AMETHYST_CLUSTER;
		if (item == Items.QUARTZ) return Items.NETHER_QUARTZ_ORE;
		if (item == Items.GOLD_NUGGET) return Items.NETHER_GOLD_ORE;
		if (item == Items.GLOWSTONE_DUST) return Items.GLOWSTONE;
		return null;
	}

	private static boolean isOre(Item item) {
		return item != Items.STONE && item != Items.DEEPSLATE && item != Items.GRAVEL;
	}
}
