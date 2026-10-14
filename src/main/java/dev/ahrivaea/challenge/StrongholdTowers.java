package dev.ahrivaea.challenge;

import dev.ahrivaea.Ahrivaea;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BuiltinStructureSets;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.structures.StrongholdPieces;

import java.util.List;

/**
 * A ruined tower stands above the End portal of every stronghold. Strongholds are numbered from the
 * centre of the world outward (the same order the game lays out its stronghold rings), and tower n runs
 * the n-th challenge in challenge_order.json. A tower is built the first time its stronghold's chunk
 * loads, so it appears as the world is explored, including in worlds made before this existed.
 */
public final class StrongholdTowers {
	private StrongholdTowers() {}

	public static void init() {
		ServerChunkEvents.CHUNK_LOAD.register((level, chunk, generated) -> {
			if (level.dimension() != Level.OVERWORLD) return;
			Structure stronghold = stronghold(level);
			if (stronghold == null) return;
			StructureStart start = chunk.getStartForStructure(stronghold);
			if (start == null || !start.isValid()) return;
			// build at the end of the tick, outside chunk loading
			PENDING.add(chunk.getPos());
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (PENDING.isEmpty()) return;
			ServerLevel level = server.overworld();
			List<ChunkPos> todo = List.copyOf(PENDING);
			PENDING.clear();
			for (ChunkPos pos : todo) {
				int index = indexOf(level, pos);
				if (index >= 0 && Challenges.state(server, towerId(index)).isEmpty()) generate(level, index);
			}
		});
	}

	private static final java.util.Set<ChunkPos> PENDING = new java.util.LinkedHashSet<>();
	private static boolean generating;

	public static String towerId(int index) {
		return "tower_" + (index + 1);
	}

	private static Structure stronghold(ServerLevel level) {
		return level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getValue(BuiltinStructures.STRONGHOLD);
	}

	private static List<ChunkPos> rings(ServerLevel level) {
		var set = level.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET).getValue(BuiltinStructureSets.STRONGHOLDS);
		if (set == null || !(set.placement() instanceof ConcentricRingsStructurePlacement rings)) return List.of();
		List<ChunkPos> list = level.getChunkSource().getGeneratorState().getRingPositionsFor(rings);
		return list == null ? List.of() : list;
	}

	/** Which stronghold (0 = first one in the innermost ring) starts in this chunk, or -1. */
	public static int indexOf(ServerLevel level, ChunkPos pos) {
		return rings(level).indexOf(pos);
	}

	/** Builds (if it isn't already there) the tower over stronghold {@code index}; returns where it stands. */
	public static BlockPos generate(ServerLevel level, int index) {
		List<ChunkPos> rings = rings(level);
		if (index < 0 || index >= rings.size()) return null;
		String id = towerId(index);
		var existing = Challenges.state(level.getServer(), id);
		if (existing.isPresent()) return existing.get().origin;
		var def = ChallengeDef.forTower(index);
		if (def.isEmpty() || generating) return null;
		generating = true;
		try {
			return build(level, rings, index, id, def.get());
		} finally {
			generating = false;
		}
	}

	private static BlockPos build(ServerLevel level, List<ChunkPos> rings, int index, String id, ChallengeDef def) {

		ChunkPos cp = rings.get(index);
		var chunk = level.getChunk(cp.x(), cp.z());
		Structure stronghold = stronghold(level);
		StructureStart start = stronghold == null ? null : chunk.getStartForStructure(stronghold);
		// stand over the portal room; if there's no stronghold data, over the stronghold's chunk
		int x = cp.getMiddleBlockX(), z = cp.getMiddleBlockZ();
		if (start != null && start.isValid()) {
			for (StructurePiece piece : start.getPieces()) {
				if (piece instanceof StrongholdPieces.PortalRoom) {
					var c = piece.getBoundingBox().getCenter();
					x = c.getX();
					z = c.getZ();
				}
			}
		}
		Vec3i size = level.getServer().getStructureTemplateManager().get(def.ruined()).map(t -> t.getSize()).orElse(new Vec3i(1, 1, 1));
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		BlockPos origin = new BlockPos(x - size.getX() / 2, y - 1, z - size.getZ() / 2);
		return Challenges.place(level, def, origin, id) ? origin : null;
	}
}
