package dev.ahrivaea.creature;

import dev.ahrivaea.Ahrivaea;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;

/**
 * Puts wild creatures around players: on land, in water (swimmers only), in caves and in the Nether and the End.
 * Which one appears depends on the biome (each form's spawn list and rarity weight). Replaces Minecraft's own spawning
 * for creatures; wild ones wander off (despawn) when no player is near.
 */
public final class WildSpawner {
	private WildSpawner() {}

	/** Wild creatures allowed within {@link #RANGE} blocks of a player before no more appear. */
	public static int CAP = 10;
	public static final int RANGE = 80, MIN_DISTANCE = 24, MAX_DISTANCE = 56, EVERY_TICKS = 20;

	/** Places where nothing wild should appear (e.g. inside bases); added to by other systems. */
	public static final List<BiPredicate<ServerLevel, BlockPos>> BLOCKED = new ArrayList<>();
	/** Dimensions to leave alone (the battle arenas). */
	public static final List<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>> SKIP = new ArrayList<>();

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(WildSpawner::tick);
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % EVERY_TICKS != 0) return;
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (p.isSpectator()) continue;
			ServerLevel level = p.level();
			if (SKIP.contains(level.dimension()) || !level.getGameRules().get(net.minecraft.world.level.gamerules.GameRules.SPAWN_MOBS)) continue;
			int wild = level.getEntitiesOfClass(CreatureEntity.class, p.getBoundingBox().inflate(RANGE), c -> !c.isTame()).size();
			if (wild >= CAP) continue;
			trySpawnNear(level, p, level.getRandom());
		}
	}

	/** One attempt to put a wild creature somewhere 24-56 blocks from the player. Returns it, or null. */
	public static CreatureEntity trySpawnNear(ServerLevel level, ServerPlayer p, RandomSource rnd) {
		double angle = rnd.nextDouble() * Math.PI * 2;
		int dist = MIN_DISTANCE + rnd.nextInt(MAX_DISTANCE - MIN_DISTANCE);
		int x = p.getBlockX() + (int) (Math.cos(angle) * dist), z = p.getBlockZ() + (int) (Math.sin(angle) * dist);
		BlockPos column = new BlockPos(x, p.getBlockY(), z);
		if (!level.isLoaded(column)) return null;
		int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		int playerSurface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p.getBlockX(), p.getBlockZ());
		boolean underground = level.dimensionType().hasCeiling() || p.getBlockY() < playerSurface - 8;
		BlockPos at;
		boolean water;
		if (underground) {
			at = null;
			for (int dy = 12; dy >= -12 && at == null; dy--) {
				BlockPos q = new BlockPos(x, p.getBlockY() + dy, z);
				if (standable(level, q)) at = q;
			}
			if (at == null) return null;
			water = false;
		} else {
			at = new BlockPos(x, surface, z);
			water = level.getFluidState(at.below()).is(FluidTags.WATER);
			if (water) at = at.below();
			else if (!standable(level, at)) return null;
		}
		if (level.getFluidState(at).is(FluidTags.LAVA) || level.getNearestPlayer(at.getX(), at.getY(), at.getZ(), MIN_DISTANCE - 4, false) != null) return null;
		final BlockPos spot = at;
		for (var blocked : BLOCKED) if (blocked.test(level, spot)) return null;
		boolean inWater = water;
		var pick = Species.pickFor(level.getBiome(spot), level, rnd,
				(s, inf) -> inWater == (s.movement() == Species.Movement.SWIM));
		if (pick.isEmpty()) return null;
		int[] range = pick.get().species().levelRange(pick.get().influence());
		int lvl = range[0] + rnd.nextInt(Math.max(1, range[1] - range[0] + 1));
		return Ahrivaea.spawn(level, pick.get().species(), pick.get().influence(), lvl, -1, spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
	}

	private static boolean standable(ServerLevel level, BlockPos feet) {
		var below = level.getBlockState(feet.below());
		return below.isFaceSturdy(level, feet.below(), net.minecraft.core.Direction.UP) && below.getFluidState().isEmpty()
				&& level.getBlockState(feet).getCollisionShape(level, feet).isEmpty() && level.getFluidState(feet).isEmpty()
				&& level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty();
	}

	/** How many wild creatures are around a spot (for tests and the cap). */
	public static int wildAround(ServerLevel level, BlockPos pos, int range) {
		return level.getEntitiesOfClass(CreatureEntity.class, new AABB(pos).inflate(range), c -> !c.isTame()).size();
	}
}
