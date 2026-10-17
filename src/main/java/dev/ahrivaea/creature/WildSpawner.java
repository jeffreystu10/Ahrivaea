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

import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;

/**
 * Puts wild creatures around players: on land, in water (swimmers only), in caves and in the Nether and the End.
 * Which one appears depends on the biome (each form's spawn list and rarity weight). Replaces Minecraft's own spawning
 * for creatures; wild ones wander off (despawn) when no player is near.
 *
 * <p>Creatures are rare, and tied to exploring: each player earns a creature for every {@code ahrivaea:wild_spawn_distance}
 * blocks they travel (50 by default, so about 20 on a 1000 block journey), plus one every
 * {@value #IDLE_SECONDS} seconds while staying put, and no more appear once {@value #CAP} wild ones are within
 * {@value #RANGE} blocks of them.
 */
public final class WildSpawner {
	private WildSpawner() {}

	/** Wild creatures allowed within {@link #RANGE} blocks of a player before no more appear. */
	public static int CAP = 5;
	public static final int RANGE = 80, MIN_DISTANCE = 24, MAX_DISTANCE = 56, EVERY_TICKS = 20;
	/** A creature turns up at most this often for someone who isn't moving. */
	public static final int IDLE_SECONDS = 90;
	/** More than this many blocks in a second is a teleport (or a portal), not travel. */
	public static final double MAX_TRAVEL_PER_SECOND = 60;

	/** Blocks of travel per wild creature. */
	public static final GameRule<Integer> DISTANCE = GameRuleBuilder.forInteger(50).minValue(1).category(GameRuleCategory.SPAWNING)
			.buildAndRegister(Ahrivaea.id("wild_spawn_distance"));

	/** How far each player has got towards their next creature (1 = one due), and where they were a second ago. */
	private record Travel(double credit, net.minecraft.world.phys.Vec3 last, net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension) {}

	private static final java.util.Map<java.util.UUID, Travel> TRAVEL = new java.util.HashMap<>();

	/** Places where nothing wild should appear (e.g. inside bases); added to by other systems. */
	public static final List<BiPredicate<ServerLevel, BlockPos>> BLOCKED = new ArrayList<>();
	/** Dimensions to leave alone (the battle arenas). */
	public static final List<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>> SKIP = new ArrayList<>();

	/** Why each recent attempt failed (for tests and /ahrivaea spawninfo). */
	public static final java.util.Map<String, Integer> FAILURES = new java.util.TreeMap<>();

	private static CreatureEntity fail(String why) {
		FAILURES.merge(why, 1, Integer::sum);
		return null;
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(WildSpawner::tick);
		net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((h, server) -> TRAVEL.remove(h.getPlayer().getUUID()));
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % EVERY_TICKS != 0) return;
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (p.isSpectator()) continue;
			ServerLevel level = p.level();
			if (SKIP.contains(level.dimension()) || !level.getGameRules().get(net.minecraft.world.level.gamerules.GameRules.SPAWN_MOBS)) continue;
			// credit for the ground covered since last second (not teleports), and a little for waiting
			Travel t = TRAVEL.get(p.getUUID());
			double moved = 0;
			if (t != null && t.dimension() == level.dimension()) {
				double d = Math.hypot(p.getX() - t.last().x, p.getZ() - t.last().z);
				if (d <= MAX_TRAVEL_PER_SECOND) moved = d;
			}
			double credit = (t == null ? 0 : t.credit()) + moved / level.getGameRules().get(DISTANCE) + 1.0 / IDLE_SECONDS;
			if (credit >= 1) {
				int wild = level.getEntitiesOfClass(CreatureEntity.class, p.getBoundingBox().inflate(RANGE), c -> !c.isTame()).size();
				// one due: try to place it (if the spot or the biome doesn't work out, it's tried again next second)
				if (wild >= CAP) credit = 1;
				else if (trySpawnNear(level, p, level.getRandom()) != null) credit -= 1;
			}
			TRAVEL.put(p.getUUID(), new Travel(Math.min(credit, 2), p.position(), level.dimension()));
		}
	}

	/** One attempt to put a wild creature somewhere 24-56 blocks from the player. Returns it, or null. */
	public static CreatureEntity trySpawnNear(ServerLevel level, ServerPlayer p, RandomSource rnd) {
		double angle = rnd.nextDouble() * Math.PI * 2;
		int dist = MIN_DISTANCE + rnd.nextInt(MAX_DISTANCE - MIN_DISTANCE);
		int x = p.getBlockX() + (int) (Math.cos(angle) * dist), z = p.getBlockZ() + (int) (Math.sin(angle) * dist);
		BlockPos column = new BlockPos(x, p.getBlockY(), z);
		if (!level.isLoaded(column)) return fail("not loaded");
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
			if (at == null) return fail("no cave floor");
			water = false;
		} else {
			at = new BlockPos(x, surface, z);
			water = level.getFluidState(at.below()).is(FluidTags.WATER);
			if (water) at = at.below();
			else if (!standable(level, at)) return fail("can't stand");
		}
		if (level.getFluidState(at).is(FluidTags.LAVA) || level.getNearestPlayer(at.getX(), at.getY(), at.getZ(), MIN_DISTANCE - 4, false) != null) return fail("lava or player");
		final BlockPos spot = at;
		for (var blocked : BLOCKED) if (blocked.test(level, spot)) return fail("blocked");
		boolean inWater = water;
		// only creatures within 5 levels of the player (by their character level), at a level in that band
		int me = dev.ahrivaea.character.PlayerCharacter.get(p).level();
		var pick = Species.pickForLevels(level.getBiome(spot), level, rnd, me - Species.WILD_LEVEL_SPREAD, me + Species.WILD_LEVEL_SPREAD,
				(s, inf) -> inWater == (s.movement() == Species.Movement.SWIM));
		if (pick.isEmpty()) return fail("nothing near level " + me + " lives in " + level.getBiome(spot).unwrapKey().map(k -> k.identifier().toString()).orElse("?") + (inWater ? " water" : ""));
		CreatureEntity e = Ahrivaea.spawn(level, pick.get().species(), pick.get().influence(), pick.get().level(), -1, spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
		if (e == null) return fail("couldn't create");
		e.setVariant(Variant.roll(level, rnd));
		FAILURES.merge("spawned", 1, Integer::sum);
		return e;
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
