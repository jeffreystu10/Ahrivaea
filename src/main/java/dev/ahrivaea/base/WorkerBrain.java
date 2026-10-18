package dev.ahrivaea.base;

import dev.ahrivaea.base.mixin.FurnaceAccess;
import dev.ahrivaea.creature.CreatureEntity;
import dev.ahrivaea.creature.Drops;
import dev.ahrivaea.creature.Job;
import dev.ahrivaea.creature.Species;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.BambooStalkBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What a base worker is doing: it picks a task from its jobs (in the order the creature list gives them), walks
 * there, works for a while and finishes it, then picks the next. Ranching runs on its own timer alongside.
 */
public final class WorkerBrain {
	/** Study sessions so far (for the enchanting table's bottles). */
	private int studies;

	private final CreatureEntity self;
	private final BlockPos core;

	private enum Kind { HARVEST, TEND, FELL, MINE, SMELT, PICK_UP, DELIVER, EMPTY_FURNACE, FISH, BREW, STUDY, POWER, CRAFT, ICE, LIGHT }

	private record Task(Kind kind, BlockPos target, ItemEntity item, int duration, String doing) {}

	private Task task;
	private int workTicks, travelTicks, cooldown, ranchTimer = 600;
	private ItemStack carried = ItemStack.EMPTY;
	private String status = "Getting settled";
	private double lastDist = Double.MAX_VALUE;

	public WorkerBrain(CreatureEntity self, BlockPos core) {
		this.self = self;
		this.core = core;
	}

	/** How many plants Growth workers have tended since the server started (for tests). */
	public static int TENDED;

	public String status() {
		return status;
	}

	/** Ticks left resting after being knocked out in a raid. */
	private int restTicks;
	private CreatureEntity fighting;
	private int attackCooldown;

	/** Knocked out by a raider: rests (and heals) for a minute before working again. */
	public void knockOut() {
		restTicks = 1200;
		fighting = null;
		if (task != null) {
			BaseCoreBlockEntity be = Bases.coreAt((ServerLevel) self.level(), core);
			if (be != null) finish(be, 20);
			else task = null;
		}
		self.getNavigation().stop();
	}

	public boolean resting() {
		return restTicks > 0;
	}

	/** The Creature Dynamo it's powering right now, if any. */
	public BlockPos powering() {
		return task != null && task.kind() == Kind.POWER && workTicks > 0 ? task.target() : null;
	}

	public BlockPos core() {
		return core;
	}

	private List<Job> jobs() {
		return Species.get(self.getSpeciesId()).map(s -> s.jobsFor(self.getInfluence())).orElse(List.of());
	}

	/** Higher-level creatures work a little faster (up to 40% at level 50). */
	private int scaled(int ticks) {
		return Math.max(10, (int) (ticks * (1 - self.getCreatureLevel() / 125.0)));
	}

	public void tick(ServerLevel level) {
		BaseCoreBlockEntity be = Bases.coreAt(level, core);
		if (be == null) {
			status = "Its base is gone";
			return;
		}
		// stray too far and it comes straight back
		if (!be.contains(self.blockPosition()) && self.distanceToSqr(Vec3.atCenterOf(core)) > Math.pow(be.radius() + 8, 2)) {
			self.teleportTo(core.getX() + 1.5, core.getY() + 1, core.getZ() + 0.5);
			drop();
		}
		if (restTicks > 0) {
			restTicks--;
			status = "Knocked out, resting (" + (restTicks / 20 + 1) + "s)";
			if (restTicks % 20 == 0) self.heal(self.getMaxHealth() / 60f);
			return;
		}
		ranch(level);
		// defenders drop everything when raiders come
		if (jobs().contains(Job.DEFENSE) && defend(level, be)) return;
		if (task == null) {
			if (--cooldown > 0) return;
			task = plan(level, be);
			workTicks = travelTicks = 0;
			lastDist = Double.MAX_VALUE;
			if (task == null) {
				cooldown = 40;
				status = jobs().contains(Job.DEFENSE) ? "On guard" : jobs().stream().anyMatch(Job::working) ? "Idle (nothing to do)" : "Idle (no work it can do yet)";
				wander(level, be);
				return;
			}
			if (task.target() != null) be.claimed.put(task.target(), self.getUUID());
		}
		Vec3 where = task.item() != null ? task.item().position() : Vec3.atCenterOf(task.target());
		if (task.item() != null && !task.item().isAlive()) {
			finish(be, 10);
			return;
		}
		// measured from the worker's middle, so a wider worker reaches a little farther
		double reach = (task.kind() == Kind.FELL ? 3.5 : task.kind() == Kind.FISH || task.kind() == Kind.ICE ? 3 : 2.3) + self.getBbWidth() / 2 - 0.35;
		double d = self.position().distanceTo(where);
		if (d > reach) {
			status = "On the way: " + task.doing();
			if (++travelTicks % 20 == 1) self.getNavigation().moveTo(where.x, where.y, where.z, 1.1);
			if (d < lastDist - 0.5) {
				lastDist = d;
				travelTicks = Math.min(travelTicks, 1);
			}
			if (travelTicks > 300) finish(be, 100); // can't get there
			return;
		}
		self.getNavigation().stop();
		self.getLookControl().setLookAt(where.x, where.y, where.z);
		status = task.doing();
		workTicks++;
		if (task.kind() == Kind.SMELT) {
			if (!smeltTick(level, task.target()) || workTicks > 1200) finish(be, 20);
			return;
		}
		if (task.kind() == Kind.BREW) {
			if (!brewTick(level, task.target()) || workTicks > 1200) finish(be, 20);
			return;
		}
		if (task.kind() == Kind.POWER) {
			if (!level.getBlockState(task.target()).is(Bases.CREATURE_DYNAMO) || workTicks > 2400) {
				finish(be, 20);
				return;
			}
			if (workTicks % 20 == 1) CreatureDynamoBlock.power(level, task.target());
			// now and then the work turns up some redstone
			if (workTicks % 1200 == 0) output(level, task.target(), new ItemStack(Items.REDSTONE));
			return;
		}
		if (workTicks % 10 == 0) level.sendParticles(ParticleTypes.HAPPY_VILLAGER, where.x, where.y + 0.5, where.z, 2, 0.3, 0.3, 0.3, 0);
		if (workTicks < task.duration()) return;
		complete(level, be, task);
	}

	private void finish(BaseCoreBlockEntity be, int wait) {
		if (task != null && task.target() != null) be.claimed.remove(task.target());
		task = null;
		cooldown = wait;
	}

	// ---- choosing what to do ----

	private Task plan(ServerLevel level, BaseCoreBlockEntity be) {
		if (!carried.isEmpty()) {
			BlockPos chest = chestFor(level, be, carried);
			if (chest != null) return new Task(Kind.DELIVER, chest, null, 10, "Putting " + Drops.describe(carried) + " away");
			drop();
		}
		for (Job job : jobs()) {
			Task t = switch (job) {
				case FARMING -> nearest(level, be, be.crops, p -> isRipe(level, p, level.getBlockState(p)), p -> new Task(Kind.HARVEST, p, null, scaled(30), "Harvesting " + name(level, p)));
				case GROWTH -> any(level, be, be.growables, p -> level.getBlockState(p).getBlock() instanceof net.minecraft.world.level.block.BonemealableBlock b
						&& b.isValidBonemealTarget(level, p, level.getBlockState(p), net.minecraft.world.level.block.BonemealSource.MOB),
						p -> new Task(Kind.TEND, p, null, scaled(60), "Tending " + name(level, p)));
				case LOGGING -> nearest(level, be, be.trees, p -> naturalTree(level, p) != null, p -> new Task(Kind.FELL, p, null, scaled(60), "Felling a tree"));
				case MINING -> nearest(level, be, be.miningPosts, p -> level.getBlockState(p).is(Bases.MINING_POST), p -> new Task(Kind.MINE, p, null, scaled(100), "Mining"));
				case SMELTING -> nearest(level, be, be.furnaces, p -> needsSmelting(level, p), p -> new Task(Kind.SMELT, p, null, 0, "Running the " + name(level, p)));
				case FISHING -> nearest(level, be, be.water, p -> level.getFluidState(p).isSource(), p -> new Task(Kind.FISH, p, null, scaled(240), "Fishing"));
				case TRANSPORT -> transportTask(level, be);
				case BREWING -> nearest(level, be, be.brewingStands, p -> needsBrewing(level, p), p -> new Task(Kind.BREW, p, null, 0, "Brewing potions"));
				case ENCHANTING -> nearest(level, be, be.enchantingTables, p -> level.getBlockState(p).is(Blocks.ENCHANTING_TABLE),
						p -> new Task(Kind.STUDY, p, null, scaled(1200), "Studying at the enchanting table"));
				case REDSTONE -> nearest(level, be, be.dynamos, p -> level.getBlockState(p).is(Bases.CREATURE_DYNAMO),
						p -> new Task(Kind.POWER, p, null, 0, "Powering the Creature Dynamo"));
				case CRAFTING -> nearest(level, be, be.crafters, p -> level.getBlockEntity(p) instanceof net.minecraft.world.level.block.entity.CrafterBlockEntity c && !c.isEmpty(),
						p -> new Task(Kind.CRAFT, p, null, scaled(60), "Working the crafter"));
				case ICE -> nearest(level, be, be.water, p -> level.getFluidState(p).isSource(), p -> new Task(Kind.ICE, p, null, scaled(300), "Making ice"));
				case LIGHTING -> lightingTask(level, be);
				default -> null;
			};
			if (t != null) return t;
		}
		return null;
	}

	private interface Check {
		boolean ok(BlockPos p);
	}

	private interface Make {
		Task make(BlockPos p);
	}

	/** A random one rather than the nearest, so a gardener spreads its care around. */
	private Task any(ServerLevel level, BaseCoreBlockEntity be, Set<BlockPos> set, Check check, Make make) {
		List<BlockPos> options = new ArrayList<>(be.unclaimed(set));
		java.util.Collections.shuffle(options, new java.util.Random(level.getRandom().nextLong()));
		for (BlockPos p : options) if (level.isLoaded(p) && check.ok(p)) return make.make(p);
		return null;
	}

	private Task nearest(ServerLevel level, BaseCoreBlockEntity be, Set<BlockPos> set, Check check, Make make) {
		BlockPos best = null;
		double bestD = Double.MAX_VALUE;
		for (BlockPos p : be.unclaimed(set)) {
			double d = p.distSqr(self.blockPosition());
			if (d < bestD && level.isLoaded(p) && check.ok(p)) {
				best = p;
				bestD = d;
			}
		}
		return best == null ? null : make.make(best);
	}

	private Task transportTask(ServerLevel level, BaseCoreBlockEntity be) {
		if (be.containers.isEmpty()) return null;
		// loose items first (not ones still flying out of something)
		List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, be.area(), i -> i.isAlive() && !i.hasPickUpDelay() && i.getAge() > 40);
		items.removeIf(i -> be.claimed.containsKey(i.blockPosition()));
		items.sort(Comparator.comparingDouble(i -> i.distanceToSqr(self)));
		if (!items.isEmpty()) {
			ItemEntity i = items.getFirst();
			return new Task(Kind.PICK_UP, i.blockPosition(), i, 5, "Picking up " + Drops.describe(i.getItem()));
		}
		// then finished smelting
		return nearest(level, be, be.furnaces, p -> level.getBlockEntity(p) instanceof AbstractFurnaceBlockEntity f && !f.getItem(2).isEmpty(),
				p -> new Task(Kind.EMPTY_FURNACE, p, null, 10, "Emptying the " + name(level, p)));
	}

	// ---- doing it ----

	private void complete(ServerLevel level, BaseCoreBlockEntity be, Task t) {
		switch (t.kind()) {
			case HARVEST -> harvest(level, t.target());
			case TEND -> {
				TENDED++;
				// like a dose of bone meal half the time, otherwise a few growth ticks
				BlockState s = level.getBlockState(t.target());
				if (s.getBlock() instanceof net.minecraft.world.level.block.BonemealableBlock b && level.getRandom().nextBoolean()
						&& b.isValidBonemealTarget(level, t.target(), s, net.minecraft.world.level.block.BonemealSource.MOB)) {
					b.performBonemeal(level, level.getRandom(), t.target(), s, net.minecraft.world.level.block.BonemealSource.MOB);
					level.levelEvent(null, net.minecraft.world.level.block.LevelEvent.PARTICLES_AND_SOUND_PLANT_GROWTH, t.target(), 15);
				} else {
					for (int i = 0; i < 4 && level.getBlockState(t.target()) == s; i++) s.randomTick(level, t.target(), level.getRandom());
				}
			}
			case FELL -> fell(level, t.target());
			case MINE -> {
				for (ItemStack s : MiningLoot.roll(level, t.target(), level.getRandom())) output(level, t.target(), s);
			}
			case FISH -> {
				LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(t.target()))
						.withParameter(LootContextParams.TOOL, new ItemStack(Items.FISHING_ROD)).withOptionalParameter(LootContextParams.THIS_ENTITY, self)
						.withLuck(self.getCreatureLevel() / 25f).create(LootContextParamSets.FISHING);
				for (ItemStack s : level.getServer().reloadableRegistries().getLootTable(BuiltInLootTables.FISHING).getRandomItems(params))
					Block.popResource(level, self.blockPosition(), s);
			}
			case PICK_UP -> {
				if (t.item().isAlive()) {
					carried = t.item().getItem().copy();
					t.item().discard();
				}
			}
			case EMPTY_FURNACE -> {
				if (level.getBlockEntity(t.target()) instanceof AbstractFurnaceBlockEntity f) carried = f.removeItem(2, 64);
			}
			case DELIVER -> {
				Container c = HopperBlockEntity.getContainerAt(level, t.target());
				if (c != null) carried = HopperBlockEntity.addItem(null, c, carried, null);
				if (!carried.isEmpty() && chestFor(level, be, carried) == null) drop();
			}
			case STUDY -> {
				// every 4th study session turns into a bottle o' enchanting (the 1st, 5th, 9th ...), and rarely an enchanted book
				if (studies++ % 4 == 0) output(level, t.target(), new ItemStack(Items.EXPERIENCE_BOTTLE));
				if (level.getRandom().nextInt(20) == 0) output(level, t.target(), net.minecraft.world.item.enchantment.EnchantmentHelper.enchantItem(
						level.getRandom(), new ItemStack(Items.BOOK), 10 + self.getCreatureLevel() / 2, level.registryAccess(), java.util.Optional.empty()));
			}
			case CRAFT -> {
				BlockState s = level.getBlockState(t.target());
				if (s.getBlock() instanceof net.minecraft.world.level.block.CrafterBlock cb) ((dev.ahrivaea.base.mixin.CrafterAccess) cb).ahrivaea$craft(s, level, t.target());
			}
			case ICE -> {
				RandomSource r = level.getRandom();
				Block.popResource(level, self.blockPosition(), new ItemStack(Items.ICE, 1 + r.nextInt(2)));
				Block.popResource(level, self.blockPosition(), new ItemStack(Items.SNOWBALL, 2 + r.nextInt(3)));
				if (r.nextInt(8) == 0) Block.popResource(level, self.blockPosition(), new ItemStack(Items.PACKED_ICE));
				if (r.nextInt(40) == 0) Block.popResource(level, self.blockPosition(), new ItemStack(Items.BLUE_ICE));
			}
			case LIGHT -> {
				if (level.getBlockState(t.target()).isAir() && Blocks.TORCH.defaultBlockState().canSurvive(level, t.target())) {
					level.setBlockAndUpdate(t.target(), Blocks.TORCH.defaultBlockState());
					carried = ItemStack.EMPTY;
				}
			}
			case SMELT, BREW, POWER -> {
			}
		}
		finish(be, 10);
	}

	private void ranch(ServerLevel level) {
		if (!jobs().contains(Job.RANCHING) || --ranchTimer > 0) return;
		ranchTimer = 2400 + level.getRandom().nextInt(1200);
		Species.get(self.getSpeciesId()).ifPresent(s -> {
			for (ItemStack stack : Drops.roll(s, self.getInfluence(), self.getCreatureLevel(), level.getRandom(), level.registryAccess()))
				Block.popResource(level, self.blockPosition(), stack);
		});
	}

	private void drop() {
		if (carried.isEmpty()) return;
		self.spawnAtLocation((ServerLevel) self.level(), carried);
		carried = ItemStack.EMPTY;
	}

	private void wander(ServerLevel level, BaseCoreBlockEntity be) {
		if (self.getNavigation().isInProgress() || level.getRandom().nextInt(4) != 0) return;
		RandomSource r = level.getRandom();
		int range = Math.clamp(be.radius() / 3, 4, 16);
		BlockPos to = core.offset(r.nextInt(2 * range + 1) - range, 0, r.nextInt(2 * range + 1) - range);
		self.getNavigation().moveTo(to.getX() + 0.5, level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, to.getX(), to.getZ()), to.getZ() + 0.5, 0.8);
	}

	private static String name(Level level, BlockPos p) {
		return level.getBlockState(p).getBlock().getName().getString().toLowerCase(java.util.Locale.ROOT);
	}

	/** Puts something into a container next to the block, or on top of it if there isn't one. */
	private static void output(ServerLevel level, BlockPos at, ItemStack stack) {
		for (Direction d : Direction.values()) {
			Container c = HopperBlockEntity.getContainerAt(level, at.relative(d));
			if (c != null && !(c instanceof AbstractFurnaceBlockEntity)) {
				stack = HopperBlockEntity.addItem(null, c, stack, null);
				if (stack.isEmpty()) return;
			}
		}
		Block.popResourceFromFace(level, at, Direction.UP, stack);
	}

	private BlockPos chestFor(ServerLevel level, BaseCoreBlockEntity be, ItemStack stack) {
		BlockPos best = null;
		double bestD = Double.MAX_VALUE;
		for (BlockPos p : be.containers) {
			Container c = level.isLoaded(p) ? HopperBlockEntity.getContainerAt(level, p) : null;
			if (c == null || !hasRoom(c, stack)) continue;
			double d = p.distSqr(self.blockPosition());
			if (d < bestD) {
				best = p;
				bestD = d;
			}
		}
		return best;
	}

	private static boolean hasRoom(Container c, ItemStack stack) {
		for (int i = 0; i < c.getContainerSize(); i++) {
			ItemStack in = c.getItem(i);
			if (in.isEmpty() || ItemStack.isSameItemSameComponents(in, stack) && in.getCount() < in.getMaxStackSize()) return true;
		}
		return false;
	}

	// ---- defense ----

	/** During a raid, a defender goes for the nearest raider. Returns true while it's fighting. */
	private boolean defend(ServerLevel level, BaseCoreBlockEntity be) {
		if (Raids.at(core) == null) {
			fighting = null;
			return false;
		}
		if (fighting == null || !fighting.isAlive() || !fighting.hasAttached(Raids.RAIDER_OF)) fighting = Raids.nearestRaider(level, core, self);
		if (fighting == null) return false;
		if (task != null) finish(be, 0);
		double reach = Combat.reach(self, fighting);
		if (self.distanceTo(fighting) > reach) {
			status = "Charging " + fighting.displayName();
			if (self.tickCount % 10 == 0) self.getNavigation().moveTo(fighting, 1.3);
		} else {
			self.getNavigation().stop();
			self.getLookControl().setLookAt(fighting);
			status = "Fighting " + fighting.displayName();
			if (--attackCooldown <= 0) {
				Combat.hit(level, self, fighting);
				attackCooldown = scaled(30);
			}
		}
		return true;
	}

	// ---- brewing ----

	private static boolean needsBrewing(ServerLevel level, BlockPos p) {
		return level.getBlockEntity(p) instanceof net.minecraft.world.level.block.entity.BrewingStandBlockEntity b
				&& dev.ahrivaea.base.mixin.BrewingAccess.ahrivaea$isBrewable(level, b);
	}

	/** One tick of running a brewing stand: no blaze powder needed, twice as fast. False when there's nothing to brew. */
	private boolean brewTick(ServerLevel level, BlockPos p) {
		if (!(level.getBlockEntity(p) instanceof net.minecraft.world.level.block.entity.BrewingStandBlockEntity b)) return false;
		var a = (dev.ahrivaea.base.mixin.BrewingAccess) b;
		boolean brewing = a.ahrivaea$brewTime() > 0;
		if (!brewing && !dev.ahrivaea.base.mixin.BrewingAccess.ahrivaea$isBrewable(level, b)) return false;
		if (a.ahrivaea$fuel() < 1) a.ahrivaea$setFuel(1);
		if (a.ahrivaea$brewTime() > 2) a.ahrivaea$setBrewTime(a.ahrivaea$brewTime() - 1);
		b.setChanged();
		if (workTicks % 20 == 0) level.sendParticles(ParticleTypes.WITCH, p.getX() + 0.5, p.getY() + 0.8, p.getZ() + 0.5, 3, 0.2, 0.2, 0.2, 0);
		return true;
	}

	// ---- lighting ----

	/** A dark spot on the ground in the base to put a torch on, if there are torches in the base's chests. */
	private Task lightingTask(ServerLevel level, BaseCoreBlockEntity be) {
		if (!carried.isEmpty() && !carried.is(Items.TORCH)) return null;
		RandomSource r = level.getRandom();
		BlockPos spot = null;
		for (int i = 0; i < 24 && spot == null; i++) {
			int x = be.minX() + r.nextInt(be.maxX() - be.minX() + 1), z = be.minZ() + r.nextInt(be.maxZ() - be.minZ() + 1);
			BlockPos top = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
			if (!be.contains(top) || !level.getBlockState(top).isAir() || level.getLightEngine().getLayerListener(net.minecraft.world.level.LightLayer.BLOCK).getLightValue(top) >= 8) continue;
			if (Blocks.TORCH.defaultBlockState().canSurvive(level, top) && !be.claimed.containsKey(top)) spot = top;
		}
		if (spot == null) return null;
		if (carried.isEmpty() && !takeTorch(level, be)) return null;
		return new Task(Kind.LIGHT, spot, null, scaled(20), "Lighting up a dark spot");
	}

	private boolean takeTorch(ServerLevel level, BaseCoreBlockEntity be) {
		for (BlockPos p : be.containers) {
			Container c = HopperBlockEntity.getContainerAt(level, p);
			for (int i = 0; c != null && i < c.getContainerSize(); i++) {
				if (c.getItem(i).is(Items.TORCH)) {
					carried = c.removeItem(i, 1);
					return true;
				}
			}
		}
		return false;
	}

	// ---- farming ----

	/** Blocks the survey keeps as crops: things that grow and can be harvested. */
	static boolean isCrop(Level level, BlockPos p, BlockState s) {
		Block b = s.getBlock();
		if (b instanceof CropBlock || b instanceof NetherWartBlock || b instanceof SweetBerryBushBlock || b instanceof CocoaBlock) return true;
		if (b instanceof AttachedStemBlock) return true;
		// sugar cane, cactus and bamboo: the bottom block, with the rest growing on top
		return (b instanceof SugarCaneBlock || b instanceof CactusBlock || b instanceof BambooStalkBlock) && !level.getBlockState(p.below()).is(b);
	}

	static boolean isRipe(Level level, BlockPos p, BlockState s) {
		Block b = s.getBlock();
		if (b instanceof CropBlock c) return c.isMaxAge(s);
		if (b instanceof NetherWartBlock) return s.getValue(NetherWartBlock.AGE) >= NetherWartBlock.MAX_AGE;
		if (b instanceof SweetBerryBushBlock) return s.getValue(SweetBerryBushBlock.AGE) >= 2;
		if (b instanceof CocoaBlock) return s.getValue(CocoaBlock.AGE) >= CocoaBlock.MAX_AGE;
		if (b instanceof AttachedStemBlock) {
			BlockState fruit = level.getBlockState(p.relative(s.getValue(AttachedStemBlock.FACING)));
			return fruit.is(Blocks.PUMPKIN) || fruit.is(Blocks.MELON);
		}
		if (b instanceof SugarCaneBlock || b instanceof CactusBlock || b instanceof BambooStalkBlock) return level.getBlockState(p.above()).is(b);
		return false;
	}

	private void harvest(ServerLevel level, BlockPos p) {
		BlockState s = level.getBlockState(p);
		if (!isRipe(level, p, s)) return;
		Block b = s.getBlock();
		if (b instanceof SugarCaneBlock || b instanceof CactusBlock || b instanceof BambooStalkBlock) {
			// leave the bottom block so it grows back
			BlockPos up = p.above();
			List<BlockPos> stack = new ArrayList<>();
			while (level.getBlockState(up).is(b)) {
				stack.add(up);
				up = up.above();
			}
			for (int i = stack.size() - 1; i >= 0; i--) level.destroyBlock(stack.get(i), true, self);
			return;
		}
		if (b instanceof AttachedStemBlock) {
			level.destroyBlock(p.relative(s.getValue(AttachedStemBlock.FACING)), true, self);
			return;
		}
		for (ItemStack drop : Block.getDrops(s, level, p, null)) Block.popResource(level, p, drop);
		BlockState replant = b instanceof CropBlock c ? c.getStateForAge(0)
				: b instanceof NetherWartBlock ? s.setValue(NetherWartBlock.AGE, 0)
				: b instanceof SweetBerryBushBlock ? s.setValue(SweetBerryBushBlock.AGE, 1)
				: b instanceof CocoaBlock ? s.setValue(CocoaBlock.AGE, 0) : Blocks.AIR.defaultBlockState();
		level.setBlockAndUpdate(p, replant);
	}

	// ---- smelting ----

	private static boolean needsSmelting(ServerLevel level, BlockPos p) {
		return level.getBlockEntity(p) instanceof AbstractFurnaceBlockEntity f && !f.getItem(0).isEmpty()
				&& f.getItem(2).getCount() < f.getItem(2).getMaxStackSize();
	}

	/** One tick of running a furnace: it burns with no fuel and cooks twice as fast. False when there's nothing left to cook. */
	private boolean smeltTick(ServerLevel level, BlockPos p) {
		if (!(level.getBlockEntity(p) instanceof AbstractFurnaceBlockEntity f) || !needsSmelting(level, p)) return false;
		FurnaceAccess a = (FurnaceAccess) f;
		if (a.ahrivaea$litTime() < 2) {
			a.ahrivaea$setLitTime(20);
			a.ahrivaea$setLitTotal(20);
			BlockState s = level.getBlockState(p);
			if (s.hasProperty(AbstractFurnaceBlock.LIT) && !s.getValue(AbstractFurnaceBlock.LIT)) level.setBlock(p, s.setValue(AbstractFurnaceBlock.LIT, true), Block.UPDATE_ALL);
		}
		if (a.ahrivaea$cookTotal() > 0 && a.ahrivaea$cookTime() < a.ahrivaea$cookTotal() - 1) a.ahrivaea$setCookTime(a.ahrivaea$cookTime() + 1);
		f.setChanged();
		if (workTicks % 20 == 0) level.sendParticles(ParticleTypes.FLAME, p.getX() + 0.5, p.getY() + 0.6, p.getZ() + 0.5, 3, 0.2, 0.1, 0.2, 0);
		return true;
	}

	// ---- logging ----

	private static final Map<Block, Block> SAPLINGS = Map.of(Blocks.OAK_LOG, Blocks.OAK_SAPLING, Blocks.SPRUCE_LOG, Blocks.SPRUCE_SAPLING,
			Blocks.BIRCH_LOG, Blocks.BIRCH_SAPLING, Blocks.JUNGLE_LOG, Blocks.JUNGLE_SAPLING, Blocks.ACACIA_LOG, Blocks.ACACIA_SAPLING,
			Blocks.DARK_OAK_LOG, Blocks.DARK_OAK_SAPLING, Blocks.CHERRY_LOG, Blocks.CHERRY_SAPLING, Blocks.PALE_OAK_LOG, Blocks.PALE_OAK_SAPLING,
			Blocks.MANGROVE_LOG, Blocks.MANGROVE_PROPAGULE);

	/**
	 * The logs of the tree standing on this block, if it's a natural tree: connected logs with natural (not player-placed)
	 * leaves on them. Buildings made of logs have no natural leaves, so they're left alone. Null if it isn't one.
	 */
	static List<BlockPos> naturalTree(ServerLevel level, BlockPos base) {
		if (!level.getBlockState(base).is(BlockTags.LOGS)) return null;
		List<BlockPos> logs = new ArrayList<>();
		Set<BlockPos> seen = new HashSet<>();
		ArrayDeque<BlockPos> todo = new ArrayDeque<>(List.of(base));
		boolean leaves = false;
		while (!todo.isEmpty() && logs.size() < 200) {
			BlockPos p = todo.poll();
			if (!seen.add(p)) continue;
			BlockState s = level.getBlockState(p);
			if (s.is(BlockTags.LEAVES)) {
				if (s.hasProperty(LeavesBlock.PERSISTENT) && !s.getValue(LeavesBlock.PERSISTENT)) leaves = true;
				continue;
			}
			if (!s.is(BlockTags.LOGS)) continue;
			logs.add(p);
			for (int dx = -1; dx <= 1; dx++) for (int dy = 0; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++)
				if (dx != 0 || dy != 0 || dz != 0) todo.add(p.offset(dx, dy, dz));
		}
		return leaves && logs.size() < 200 ? logs : null;
	}

	private void fell(ServerLevel level, BlockPos base) {
		List<BlockPos> logs = naturalTree(level, base);
		if (logs == null) return;
		Block kind = level.getBlockState(base).getBlock();
		logs.sort(Comparator.comparingInt((BlockPos p) -> p.getY()).reversed());
		for (BlockPos p : logs) level.destroyBlock(p, true, self);
		// replant where it stood (the leaves left behind decay and drop more saplings and apples)
		Block sapling = SAPLINGS.get(kind);
		if (sapling != null && level.getBlockState(base).isAir() && sapling.defaultBlockState().canSurvive(level, base)) {
			level.setBlockAndUpdate(base, sapling.defaultBlockState());
		}
	}
}
