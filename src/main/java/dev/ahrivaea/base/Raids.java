package dev.ahrivaea.base;

import dev.ahrivaea.Ahrivaea;
import dev.ahrivaea.creature.CreatureEntity;
import dev.ahrivaea.creature.Drops;
import dev.ahrivaea.creature.Species;
import dev.ahrivaea.battle.ArenaReturn;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Raids on bases. At nightfall (or once a day where there is no night), a base whose owner is nearby may be raided:
 * aggressive creatures of the area turn up at the edge of the base and head for the core, fighting any worker in their
 * way, and once there they steal from the base's chests. Defense workers fight them off (see {@link WorkerBrain});
 * the owner can also take raiders on in a battle by hitting one with a sword. Defeated raiders drop their loot.
 * Workers that lose a fight are knocked out for a while, never killed. Game rule {@code ahrivaea:base_raids false}
 * turns raids off.
 */
public final class Raids {
	private Raids() {}

	public static final GameRule<Boolean> ENABLED = GameRuleBuilder.forBoolean(true).category(GameRuleCategory.MOBS)
			.buildAndRegister(Ahrivaea.id("base_raids"));

	/** On a raider: the core it's raiding. Not saved: raiders that unload just leave. */
	public static final AttachmentType<BlockPos> RAIDER_OF = AttachmentRegistry.create(Ahrivaea.id("raider_of"));

	/** Chance a base is raided at nightfall: 25% plus 5% per base level. */
	public static double chance(int baseLevel) {
		return 0.25 + 0.05 * baseLevel;
	}

	/** How many raiders: 2 plus the base level plus 0-2. */
	public static int size(int baseLevel, net.minecraft.util.RandomSource r) {
		return 2 + baseLevel + r.nextInt(3);
	}

	public static final int MAX_TICKS = 6000, STEAL_EVERY = 200;

	public record Raid(ServerLevel level, BlockPos core, List<UUID> raiders, long started, int[] stolen) {}

	private static final Map<BlockPos, Raid> ACTIVE = new HashMap<>();
	private static final Map<BlockPos, Long> LAST = new HashMap<>();
	/** How the last raid on each base ended ("defeated" or "left"), and how much was stolen. */
	private static final Map<BlockPos, String> RESULTS = new HashMap<>();

	public static String lastResult(BlockPos core) {
		return RESULTS.get(core);
	}

	public static Raid at(BlockPos core) {
		return ACTIVE.get(core);
	}

	public static boolean isRaider(net.minecraft.world.entity.Entity e) {
		return e.hasAttached(RAIDER_OF);
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(Raids::tick);
		// raiders and defenders can hurt each other; workers who lose are knocked out, not killed
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			if (entity instanceof CreatureEntity c && c.hasAttached(Bases.WORKER_OF)) {
				c.setHealth(1);
				if (c.brain() != null) c.brain().knockOut();
				return false;
			}
			return true;
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof CreatureEntity c && isRaider(c) && entity.level() instanceof ServerLevel level) {
				Species.get(c.getSpeciesId()).ifPresent(s -> {
					for (ItemStack stack : Drops.roll(s, c.getInfluence(), c.getCreatureLevel(), level.getRandom(), level.registryAccess()))
						Block.popResource(level, c.blockPosition(), stack);
				});
			}
		});
	}

	private static void tick(MinecraftServer server) {
		// nightfall: maybe raid each base
		for (ServerLevel level : server.getAllLevels()) {
			if (!level.getGameRules().get(ENABLED) || level.getGameTime() % 100 != 0) continue;
			long day = level.getDefaultClockTime() / 24000, time = level.getDefaultClockTime() % 24000;
			boolean nightfall = level.dimensionType().hasFixedTime() || !level.dimensionType().hasSkyLight() ? time < 100 || level.getGameTime() % 24000 < 100
					: time >= 13000 && time < 13100;
			if (!nightfall) continue;
			for (BlockPos core : Bases.cores(level)) {
				BaseCoreBlockEntity be = Bases.coreAt(level, core);
				if (be == null || ACTIVE.containsKey(core) || be.workers().isEmpty() || LAST.getOrDefault(core, -1L) == day) continue;
				LAST.put(core, day);
				if (owner(level, be) == null || level.getRandom().nextDouble() >= chance(be.baseLevel())) continue;
				start(level, be);
			}
		}
		// running raids
		for (Raid r : List.copyOf(ACTIVE.values())) step(r);
	}

	/** The base's owner, if they're online and within 128 blocks (bases aren't raided while you're away). */
	private static ServerPlayer owner(ServerLevel level, BaseCoreBlockEntity be) {
		if (be.owner() == null) return null;
		ServerPlayer p = level.getServer().getPlayerList().getPlayer(be.owner());
		return p != null && p.level() == level && p.distanceToSqr(Vec3.atCenterOf(be.getBlockPos())) < 128 * 128 ? p : null;
	}

	/** Starts a raid on a base now. Returns how many raiders came. */
	public static int start(ServerLevel level, BaseCoreBlockEntity be) {
		BlockPos core = be.getBlockPos();
		if (ACTIVE.containsKey(core)) return 0;
		int count = size(be.baseLevel(), level.getRandom());
		int avgLevel = (int) be.workers().stream().mapToInt(w -> w.creature().level()).average().orElse(10);
		List<UUID> raiders = new ArrayList<>();
		var biome = level.getBiome(core);
		for (int i = 0; i < count; i++) {
			double angle = level.getRandom().nextDouble() * Math.PI * 2;
			int r = be.radius() - 2;
			Vec3 want = new Vec3(core.getX() + 0.5 + Math.cos(angle) * r, core.getY(), core.getZ() + 0.5 + Math.sin(angle) * r);
			Vec3 at = ArenaReturn.safeSpot(level, new Vec3(want.x, level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
					(int) Math.floor(want.x), (int) Math.floor(want.z)), want.z));
			if (at == null) continue;
			var pick = Species.pickFor(biome, level, level.getRandom(), (s, inf) -> s.movement() != Species.Movement.SWIM);
			if (pick.isEmpty()) pick = Species.all().isEmpty() ? java.util.Optional.empty()
					: java.util.Optional.of(new Species.Pick(Species.all().getFirst(), Species.all().getFirst().forms().keySet().iterator().next()));
			if (pick.isEmpty()) break;
			int lvl = Math.clamp(avgLevel - 3 + level.getRandom().nextInt(7), 1, 50);
			CreatureEntity e = Ahrivaea.spawn(level, pick.get().species(), pick.get().influence(), lvl, -1, at.x, at.y, at.z);
			if (e == null) continue;
			e.setAttached(RAIDER_OF, core);
			e.capSize(CreatureEntity.BASE_HITBOX);
			e.setGlowingTag(true);
			raiders.add(e.getUUID());
		}
		if (raiders.isEmpty()) return 0;
		ACTIVE.put(core, new Raid(level, core, raiders, level.getGameTime(), new int[1]));
		ServerPlayer p = owner(level, be);
		if (p != null) {
			p.sendSystemMessage(Component.literal("Your base is being raided by " + raiders.size() + " creatures! Your Defense workers will fight them;"
					+ " hit one with a sword to battle it yourself.").withStyle(ChatFormatting.RED));
			level.playSound(null, core, SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 8f, 1f);
		}
		Ahrivaea.LOGGER.info("Raid on the base at {}: {} raiders", core.toShortString(), raiders.size());
		return raiders.size();
	}

	private static void step(Raid r) {
		ServerLevel level = r.level();
		BaseCoreBlockEntity be = Bases.coreAt(level, r.core());
		r.raiders().removeIf(id -> !(level.getEntity(id) instanceof CreatureEntity c) || !c.isAlive() || !c.hasAttached(RAIDER_OF));
		long age = level.getGameTime() - r.started();
		if (be == null || r.raiders().isEmpty()) {
			end(r, be, true);
			return;
		}
		boolean day = level.dimensionType().hasSkyLight() && !level.dimensionType().hasFixedTime() && level.getDefaultClockTime() % 24000 < 12000;
		if (age > MAX_TICKS || day && age > 200) {
			end(r, be, false);
			return;
		}
		// raiders at the core steal from the chests
		if (age % STEAL_EVERY == 0) {
			for (UUID id : r.raiders()) {
				if (!(level.getEntity(id) instanceof CreatureEntity c) || c.distanceToSqr(Vec3.atCenterOf(r.core())) > 25) continue;
				r.stolen()[0] += steal(level, be);
			}
		}
	}

	/** A raider takes up to 16 items from a random chest in the base; they're lost. Returns how many. */
	private static int steal(ServerLevel level, BaseCoreBlockEntity be) {
		List<BlockPos> chests = new ArrayList<>(be.containers);
		java.util.Collections.shuffle(chests, new java.util.Random(level.getRandom().nextLong()));
		for (BlockPos p : chests) {
			Container c = HopperBlockEntity.getContainerAt(level, p);
			if (c == null) continue;
			for (int i = 0; i < c.getContainerSize(); i++) {
				if (c.getItem(i).isEmpty()) continue;
				int n = c.removeItem(i, 16).getCount();
				level.playSound(null, p, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 1f, 1.4f);
				ServerPlayer owner = owner(level, be);
				if (owner != null) owner.sendOverlayMessage(Component.literal("Raiders are stealing from your chests!").withStyle(ChatFormatting.RED));
				return n;
			}
		}
		return 0;
	}

	private static void end(Raid r, BaseCoreBlockEntity be, boolean won) {
		ACTIVE.remove(r.core());
		RESULTS.put(r.core(), (won ? "defeated" : "left") + ", " + r.stolen()[0] + " stolen");
		ServerLevel level = r.level();
		for (UUID id : r.raiders()) if (level.getEntity(id) instanceof CreatureEntity c) c.discard();
		ServerPlayer p = be == null ? null : owner(level, be);
		String stolen = r.stolen()[0] > 0 ? " They stole " + r.stolen()[0] + " items." : "";
		if (p != null) p.sendSystemMessage(Component.literal(won ? "The raid on your base is over: every raider was defeated." + stolen
				: "The raiders gave up and left." + stolen).withStyle(won ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
		Ahrivaea.LOGGER.info("Raid on {} ended: {}; {} items stolen", r.core().toShortString(), won ? "raiders defeated" : "raiders left", r.stolen()[0]);
	}

	/** The raider a defender at this base should go for: the nearest one. */
	static CreatureEntity nearestRaider(ServerLevel level, BlockPos core, CreatureEntity from) {
		Raid r = ACTIVE.get(core);
		if (r == null) return null;
		CreatureEntity best = null;
		for (UUID id : r.raiders()) {
			if (level.getEntity(id) instanceof CreatureEntity c && c.isAlive() && (best == null || c.distanceToSqr(from) < best.distanceToSqr(from))) best = c;
		}
		return best;
	}

	private static final Map<UUID, Integer> COOLDOWN = new HashMap<>();

	/** A raider's turn: fight the nearest worker that's still standing, else head for the core. */
	public static void raiderTick(CreatureEntity c) {
		if (!(c.level() instanceof ServerLevel level)) return;
		BlockPos core = c.getAttached(RAIDER_OF);
		if (core == null || !ACTIVE.containsKey(core)) {
			c.discard(); // the raid is over (or was never registered, e.g. after a restart)
			return;
		}
		CreatureEntity target = null;
		for (CreatureEntity w : level.getEntitiesOfClass(CreatureEntity.class, c.getBoundingBox().inflate(10),
				w -> core.equals(w.getAttached(Bases.WORKER_OF)) && w.brain() != null && !w.brain().resting())) {
			if (target == null || w.distanceToSqr(c) < target.distanceToSqr(c)) target = w;
		}
		int cd = COOLDOWN.merge(c.getUUID(), -1, Integer::sum);
		if (target != null) {
			if (c.distanceTo(target) > Combat.reach(c, target)) {
				if (c.tickCount % 10 == 0) c.getNavigation().moveTo(target, 1.2);
			} else {
				c.getNavigation().stop();
				c.getLookControl().setLookAt(target);
				if (cd <= 0) {
					Combat.hit(level, c, target);
					COOLDOWN.put(c.getUUID(), 35);
				}
			}
			return;
		}
		if (c.distanceToSqr(Vec3.atCenterOf(core)) > 9) {
			if (c.tickCount % 20 == 0) c.getNavigation().moveTo(core.getX() + 0.5, core.getY(), core.getZ() + 0.5, 1.1);
		} else c.getNavigation().stop();
	}

	public static AABB around(BlockPos core, int radius) {
		return new AABB(core).inflate(radius + 8, 16, radius + 8);
	}
}
