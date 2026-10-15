package dev.ahrivaea.base;

import dev.ahrivaea.creature.CreatureEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A base: its owner, its level (which sets the radius and how many workers it takes) and its workers. It also keeps
 * a running survey of the blocks in the base that workers care about (crops, trees, furnaces, chests, water ...),
 * scanning a slice of the area every tick so the whole base is covered every few seconds.
 */
public class BaseCoreBlockEntity extends BlockEntity {
	/** Per level 1-5: radius (blocks, horizontally) and worker cap. */
	public static final int[] RADIUS = {0, 24, 28, 32, 36, 40};
	public static final int[] CAP = {0, 5, 7, 9, 12, 15};
	public static final int MAX_LEVEL = 5;
	/** How far below and above the core the base reaches. */
	public static final int DOWN = 8, UP = 16;

	/** What it takes to go from level n to n + 1. */
	public record Upgrade(Item item, int count) {}

	public static Upgrade upgradeFrom(int level) {
		return switch (level) {
			case 1 -> new Upgrade(Items.IRON_BLOCK, 4);
			case 2 -> new Upgrade(Items.GOLD_BLOCK, 4);
			case 3 -> new Upgrade(Items.DIAMOND_BLOCK, 2);
			case 4 -> new Upgrade(Items.EMERALD_BLOCK, 4);
			default -> null;
		};
	}

	private UUID owner;
	private String ownerName = "";
	private int level = 1;
	final List<Worker> workers = new ArrayList<>();

	// the survey (not saved: rebuilt by scanning)
	final Set<BlockPos> crops = new LinkedHashSet<>(), growables = new LinkedHashSet<>(), trees = new LinkedHashSet<>(),
			furnaces = new LinkedHashSet<>(), containers = new LinkedHashSet<>(), water = new LinkedHashSet<>(), miningPosts = new LinkedHashSet<>(),
			brewingStands = new LinkedHashSet<>(), enchantingTables = new LinkedHashSet<>(), dynamos = new LinkedHashSet<>(), crafters = new LinkedHashSet<>();
	private int scanIndex;
	/** Targets a worker is already on, so two don't go for the same one. */
	final Map<BlockPos, UUID> claimed = new HashMap<>();
	private final Map<UUID, Integer> missingChecks = new HashMap<>();

	public BaseCoreBlockEntity(BlockPos pos, BlockState state) {
		super(Bases.BASE_CORE_ENTITY, pos, state);
	}

	public UUID owner() {
		return owner;
	}

	public String ownerName() {
		return ownerName;
	}

	public void setOwner(ServerPlayer p) {
		owner = p.getUUID();
		ownerName = p.getPlainTextName();
		setChanged();
	}

	public int baseLevel() {
		return level;
	}

	public int radius() {
		return RADIUS[level];
	}

	public int cap() {
		return CAP[level];
	}

	public List<Worker> workers() {
		return List.copyOf(workers);
	}

	public boolean contains(BlockPos p) {
		BlockPos c = getBlockPos();
		return Math.abs(p.getX() - c.getX()) <= radius() && Math.abs(p.getZ() - c.getZ()) <= radius()
				&& p.getY() >= c.getY() - DOWN && p.getY() <= c.getY() + UP;
	}

	public AABB area() {
		BlockPos c = getBlockPos();
		return new AABB(c.getX() - radius(), c.getY() - DOWN, c.getZ() - radius(), c.getX() + radius() + 1, c.getY() + UP + 1, c.getZ() + radius() + 1);
	}

	/** For operators and tests: set the level directly. */
	public void setBaseLevel(int level) {
		this.level = Math.clamp(level, 1, MAX_LEVEL);
		setChanged();
	}

	// ---- upgrades ----

	public boolean isUpgradeItem(ItemStack stack) {
		Upgrade u = upgradeFrom(level);
		return u != null && stack.is(u.item());
	}

	void tryUpgrade(ServerPlayer p, ItemStack stack) {
		Upgrade u = upgradeFrom(level);
		if (u == null || !stack.is(u.item())) return;
		if (owner != null && !owner.equals(p.getUUID())) {
			p.sendOverlayMessage(Component.literal("This is " + ownerName + "'s base."));
			return;
		}
		if (stack.getCount() < u.count() && !p.isCreative()) {
			p.sendOverlayMessage(Component.literal("Upgrading to level " + (level + 1) + " takes " + u.count() + " ").append(stack.getHoverName()).append("."));
			return;
		}
		if (!p.isCreative()) stack.shrink(u.count());
		level++;
		setChanged();
		p.sendSystemMessage(Component.literal("Base upgraded to level " + level + ": radius " + radius() + ", up to " + cap() + " workers."));
		((ServerLevel) this.level()).playSound(null, getBlockPos(), net.minecraft.sounds.SoundEvents.BEACON_POWER_SELECT, net.minecraft.sounds.SoundSource.BLOCKS, 1f, 1f);
	}

	private Level level() {
		return super.getLevel();
	}

	// ---- saving ----

	@Override
	protected void saveAdditional(ValueOutput out) {
		super.saveAdditional(out);
		if (owner != null) out.store("owner", UUIDUtil.CODEC, owner);
		out.putString("owner_name", ownerName);
		out.putInt("base_level", level);
		var list = out.list("workers", Worker.CODEC);
		for (Worker w : workers) list.add(w);
	}

	@Override
	protected void loadAdditional(ValueInput in) {
		super.loadAdditional(in);
		owner = in.read("owner", UUIDUtil.CODEC).orElse(null);
		ownerName = in.getStringOr("owner_name", "");
		level = Math.clamp(in.getIntOr("base_level", 1), 1, MAX_LEVEL);
		workers.clear();
		for (Worker w : in.listOrEmpty("workers", Worker.CODEC)) workers.add(w);
	}

	@Override
	public void setRemoved() {
		super.setRemoved();
		if (getLevel() instanceof ServerLevel sl) Bases.unindex(sl, getBlockPos());
	}

	// ---- ticking ----

	public static void serverTick(Level level, BlockPos pos, BlockState state, BaseCoreBlockEntity be) {
		ServerLevel sl = (ServerLevel) level;
		Bases.index(sl, pos);
		be.scan(sl, 2048);
		if (sl.getGameTime() % 100 == 0) be.checkWorkers(sl);
	}

	/** Looks at the next {@code count} blocks of the base and updates the survey. */
	void scan(ServerLevel level, int count) {
		int r = radius(), side = 2 * r + 1, height = DOWN + UP + 1, total = side * side * height;
		BlockPos c = getBlockPos();
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int i = 0; i < count; i++) {
			int n = scanIndex++ % total;
			if (scanIndex >= total) scanIndex = 0;
			int y = n / (side * side), rest = n % (side * side);
			p.set(c.getX() - r + rest % side, c.getY() - DOWN + y, c.getZ() - r + rest / side);
			if (!level.isLoaded(p)) continue;
			survey(level, p.immutable(), level.getBlockState(p));
		}
	}

	private void survey(ServerLevel level, BlockPos p, BlockState s) {
		set(crops, p, WorkerBrain.isCrop(level, p, s));
		set(growables, p, s.getBlock() instanceof BonemealableBlock b && !s.is(BlockTags.LEAVES) && !s.is(Blocks.GRASS_BLOCK) && !s.is(Blocks.MOSS_BLOCK)
				&& b.isValidBonemealTarget(level, p, s, net.minecraft.world.level.block.BonemealSource.MOB));
		set(trees, p, s.is(BlockTags.LOGS) && level.getBlockState(p.below()).is(BlockTags.DIRT));
		BlockEntity be = s.hasBlockEntity() ? level.getBlockEntity(p) : null;
		set(furnaces, p, be instanceof AbstractFurnaceBlockEntity);
		set(containers, p, be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity);
		set(water, p, s.getFluidState().is(Fluids.WATER) && s.getFluidState().isSource() && level.getBlockState(p.above()).isAir());
		set(miningPosts, p, s.is(Bases.MINING_POST));
		set(brewingStands, p, be instanceof net.minecraft.world.level.block.entity.BrewingStandBlockEntity);
		set(enchantingTables, p, s.is(Blocks.ENCHANTING_TABLE));
		set(dynamos, p, s.is(Bases.CREATURE_DYNAMO));
		set(crafters, p, be instanceof net.minecraft.world.level.block.entity.CrafterBlockEntity);
	}

	private static void set(Set<BlockPos> set, BlockPos p, boolean in) {
		if (in) set.add(p);
		else set.remove(p);
	}

	/** Brings back any worker whose body has gone missing (it's re-made at the core from what the base remembers). */
	private void checkWorkers(ServerLevel level) {
		for (int i = 0; i < workers.size(); i++) {
			Worker w = workers.get(i);
			if (level.getEntity(w.entity()) instanceof CreatureEntity) {
				missingChecks.remove(w.entity());
				continue;
			}
			int misses = missingChecks.merge(w.entity(), 1, Integer::sum);
			if (misses < 3) continue;
			missingChecks.remove(w.entity());
			CreatureEntity e = Bases.spawnWorker(level, this, w.creature());
			if (e != null) workers.set(i, new Worker(w.creature(), e.getUUID()));
			setChanged();
		}
	}

	/** Called by a worker to keep the base's copy of it (level, health) up to date. */
	void update(UUID entity, dev.ahrivaea.party.CaughtCreature now) {
		for (int i = 0; i < workers.size(); i++) if (workers.get(i).entity().equals(entity)) workers.set(i, new Worker(now, entity));
		setChanged();
	}

	boolean isWorker(UUID entity) {
		for (Worker w : workers) if (w.entity().equals(entity)) return true;
		return false;
	}

	/** Removes everything a worker had claimed. */
	void release(UUID entity) {
		claimed.values().removeIf(entity::equals);
	}

	Set<BlockPos> unclaimed(Set<BlockPos> set) {
		Set<BlockPos> out = new HashSet<>(set);
		out.removeAll(claimed.keySet());
		return out;
	}
}
