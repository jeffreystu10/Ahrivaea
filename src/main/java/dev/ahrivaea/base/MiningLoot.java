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
		List<Entry> table = level.dimension() == Level.NETHER ? NETHER : level.dimension() == Level.END ? END : post.getY() < 0 ? DEEP : SURFACE;
		int total = table.stream().mapToInt(Entry::weight).sum(), roll = r.nextInt(total);
		for (Entry e : table) {
			roll -= e.weight();
			if (roll < 0) return List.of(new ItemStack(e.item(), e.min() + r.nextInt(e.max() - e.min() + 1)));
		}
		return List.of();
	}
}
