package dev.ahrivaea.battle;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/**
 * Where a unit can move this turn. Shared by the server's rules and the client's highlights.
 * Each step costs one square. Stepping up 2+ blocks or down 4+ is impossible unless the unit flies; walls
 * (trees, cliffs) and deep water stop walkers, lava stops everyone but Fire creatures and flyers. A unit may
 * pass through its own side but not the other side.
 */
public final class Pathing {
	private Pathing() {}

	/** What occupies a cell: 0 = nobody, 1 = same side, 2 = other side. */
	public interface Occupancy {
		int at(int x, int z);
	}

	public static Map<Integer, Integer> reachable(BattleGrid g, int sx, int sz, int squares, boolean flies, boolean lavaOk, Occupancy occ) {
		Map<Integer, Integer> cost = new HashMap<>();
		ArrayDeque<int[]> q = new ArrayDeque<>();
		cost.put(sx + sz * g.size, 0);
		q.add(new int[]{sx, sz});
		int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		while (!q.isEmpty()) {
			int[] c = q.poll();
			int here = cost.get(c[0] + c[1] * g.size);
			if (here >= squares) continue;
			for (int[] d : dirs) {
				int nx = c[0] + d[0], nz = c[1] + d[1];
				if (!g.inside(nx, nz) || cost.containsKey(nx + nz * g.size)) continue;
				if (!canStep(g, c[0], c[1], nx, nz, flies, lavaOk)) continue;
				if (occ.at(nx, nz) == 2) continue;
				cost.put(nx + nz * g.size, here + 1);
				q.add(new int[]{nx, nz});
			}
		}
		return cost;
	}

	public static boolean canStep(BattleGrid g, int x, int z, int nx, int nz, boolean flies, boolean lavaOk) {
		BattleGrid.Terrain t = g.at(nx, nz);
		if (flies) return true;
		if (t == BattleGrid.Terrain.WALL || t == BattleGrid.Terrain.DEEP_WATER) return false;
		if (t == BattleGrid.Terrain.LAVA && !lavaOk) return false;
		int dy = g.y(nx, nz) - g.y(x, z);
		return dy <= 1 && dy >= -3;
	}

	/** Whether a unit may end its move on this cell (it must be empty, and ground a unit can stand on). */
	public static boolean canStop(BattleGrid g, int x, int z, boolean flies, boolean lavaOk, Occupancy occ, int selfX, int selfZ) {
		if (!(x == selfX && z == selfZ) && occ.at(x, z) != 0) return false;
		BattleGrid.Terrain t = g.at(x, z);
		if (t == BattleGrid.Terrain.WALL) return false;
		if (t == BattleGrid.Terrain.LAVA) return lavaOk;
		if (t == BattleGrid.Terrain.DEEP_WATER) return flies;
		return true;
	}

	public static int distance(int x1, int z1, int x2, int z2) {
		return Math.abs(x1 - x2) + Math.abs(z1 - z2);
	}
}
