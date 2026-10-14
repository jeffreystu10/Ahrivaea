package dev.ahrivaea.battle;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The battlefield: the real terrain in a square around the battle, one cell per block column. Each cell has
 * the height a unit stands at and what kind of ground it is. Pure data once built, so the same rules run on the
 * server and in the client's highlights.
 */
public final class BattleGrid {
	public enum Terrain { GROUND, WATER, DEEP_WATER, LAVA, WALL }

	public final int size, originX, originZ;
	public final int[] heights;
	public final Terrain[] terrain;

	public BattleGrid(int size, int originX, int originZ, int[] heights, Terrain[] terrain) {
		this.size = size;
		this.originX = originX;
		this.originZ = originZ;
		this.heights = heights;
		this.terrain = terrain;
	}

	public boolean inside(int x, int z) {
		return x >= 0 && z >= 0 && x < size && z < size;
	}

	public int y(int x, int z) {
		return heights[x + z * size];
	}

	public Terrain at(int x, int z) {
		return terrain[x + z * size];
	}

	public BlockPos worldPos(int x, int z) {
		return new BlockPos(originX + x, y(x, z), originZ + z);
	}

	/** Reads the terrain around {@code center}: for each column, the highest spot a unit could stand within ±12 blocks. */
	public static BattleGrid read(Level level, BlockPos center, int radius) {
		int size = radius * 2 + 1;
		int[] h = new int[size * size];
		Terrain[] t = new Terrain[size * size];
		for (int x = 0; x < size; x++) {
			for (int z = 0; z < size; z++) {
				int wx = center.getX() - radius + x, wz = center.getZ() - radius + z;
				Terrain kind = Terrain.WALL;
				int y = center.getY();
				for (int yy = center.getY() + 12; yy >= center.getY() - 12; yy--) {
					BlockPos feet = new BlockPos(wx, yy, wz);
					BlockState f = level.getBlockState(feet), head = level.getBlockState(feet.above()), below = level.getBlockState(feet.below());
					if (!open(level, feet, f) || !open(level, feet.above(), head)) continue;
					if (f.getFluidState().is(FluidTags.LAVA)) {
						kind = Terrain.LAVA;
						y = yy;
						break;
					}
					if (f.getFluidState().is(FluidTags.WATER)) {
						kind = below.getFluidState().is(FluidTags.WATER) ? Terrain.DEEP_WATER : Terrain.WATER;
						y = yy;
						break;
					}
					if (below.isAir() || !below.getFluidState().isEmpty() && below.getCollisionShape(level, feet.below()).isEmpty()) continue;
					// trees are obstacles, not floors
					if (below.is(BlockTags.LEAVES) || below.is(BlockTags.LOGS)) {
						kind = Terrain.WALL;
						y = yy;
						break;
					}
					if (below.isFaceSturdy(level, feet.below(), Direction.UP) || !below.getCollisionShape(level, feet.below()).isEmpty()) {
						kind = Terrain.GROUND;
						y = yy;
						break;
					}
				}
				h[x + z * size] = y;
				t[x + z * size] = kind;
			}
		}
		return new BattleGrid(size, center.getX() - radius, center.getZ() - radius, h, t);
	}

	private static boolean open(Level level, BlockPos pos, BlockState s) {
		return s.getCollisionShape(level, pos).isEmpty() && !s.is(BlockTags.LEAVES);
	}
}
