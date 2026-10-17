package dev.ahrivaea.creature;

import dev.ahrivaea.party.CaughtCreature;
import dev.ahrivaea.party.Party;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.SitWhenOrderedToGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/** One creature in the world, wild or belonging to a trainer. Its species and level travel with it. */
public class CreatureEntity extends TamableAnimal {
	private static final EntityDataAccessor<String> SPECIES = SynchedEntityData.defineId(CreatureEntity.class, EntityDataSerializers.STRING);
	private static final EntityDataAccessor<Integer> LEVEL = SynchedEntityData.defineId(CreatureEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<String> INFLUENCE = SynchedEntityData.defineId(CreatureEntity.class, EntityDataSerializers.STRING);
	// riding (see Mounts); worked out on the server, which has the species data, and sent to players
	private static final EntityDataAccessor<Integer> RIDE_FLAGS = SynchedEntityData.defineId(CreatureEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Float> TOP_SPEED = SynchedEntityData.defineId(CreatureEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> SEAT = SynchedEntityData.defineId(CreatureEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Integer> MOUNT_TIER = SynchedEntityData.defineId(CreatureEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> MOUNT_FED = SynchedEntityData.defineId(CreatureEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> RIDE_TIER = SynchedEntityData.defineId(CreatureEntity.class, EntityDataSerializers.INT);
	private static final int RIDEABLE = 1, FLIES = 2, SWIMS = 4;
	/** Progress toward its next level (kept while it's out of the party). */
	private int xp;
	/** Whether the creature its rider is controlling is in the air (only on the rider's side, which moves it). */
	private boolean riddenFlying;
	private int takeoffTicks;

	public CreatureEntity(EntityType<? extends TamableAnimal> type, Level level) {
		super(type, level);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(SPECIES, "");
		builder.define(LEVEL, 5);
		builder.define(INFLUENCE, "none");
		builder.define(RIDE_FLAGS, 0);
		builder.define(TOP_SPEED, 0f);
		builder.define(SEAT, 1f);
		builder.define(MOUNT_TIER, 0);
		builder.define(MOUNT_FED, 0);
		builder.define(RIDE_TIER, 0);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		// at a base, work comes before anything else (following the owner, wandering ...)
		this.goalSelector.addGoal(1, new WorkGoal());
		this.goalSelector.addGoal(1, new RaidGoal());
		this.goalSelector.addGoal(1, new PanicGoal(this, 1.4));
		this.goalSelector.addGoal(2, new SitWhenOrderedToGoal(this));
		this.goalSelector.addGoal(3, new FollowOwnerGoal(this, 1.1, 6f, 2f));
		this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.9));
		this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 6f));
		this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
	}

	private dev.ahrivaea.base.WorkerBrain brain;

	/** What it's doing at its base, if it's a base worker (server side). */
	public dev.ahrivaea.base.WorkerBrain brain() {
		if (brain == null && !this.level().isClientSide() && this.hasAttached(dev.ahrivaea.base.Bases.WORKER_OF))
			brain = new dev.ahrivaea.base.WorkerBrain(this, this.getAttached(dev.ahrivaea.base.Bases.WORKER_OF));
		return brain;
	}

	private class WorkGoal extends net.minecraft.world.entity.ai.goal.Goal {
		WorkGoal() {
			setFlags(java.util.EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
		}

		@Override
		public boolean canUse() {
			return brain() != null;
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			brain().tick((net.minecraft.server.level.ServerLevel) level());
		}
	}

	private class RaidGoal extends net.minecraft.world.entity.ai.goal.Goal {
		RaidGoal() {
			setFlags(java.util.EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
		}

		@Override
		public boolean canUse() {
			return !level().isClientSide() && hasAttached(dev.ahrivaea.base.Raids.RAIDER_OF);
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			dev.ahrivaea.base.Raids.raiderTick(CreatureEntity.this);
		}
	}

	/** The species id; known on both server and client. */
	public String getSpeciesId() {
		return this.entityData.get(SPECIES);
	}

	/** The species data (server side; the client only knows the id). */
	public java.util.Optional<Species> getSpecies() {
		return Species.get(getSpeciesId());
	}

	public Influence getInfluence() {
		return Influence.byName(this.entityData.get(INFLUENCE));
	}

	/** The form's name, e.g. "Ashen Dragon". */
	public String displayName() {
		return getSpecies().map(s -> s.nameFor(getInfluence())).orElse(getSpeciesId());
	}

	public Stats getStats() {
		return getSpecies().map(s -> s.statsFor(getInfluence())).orElse(new Stats(50, 50, 50, 50, 50, 50, 50));
	}

	public int getCreatureLevel() {
		return this.entityData.get(LEVEL);
	}

	/**
	 * The biggest a creature's hitbox gets (as a multiple of 0.7 x 0.8 blocks), so even huge creatures can walk
	 * around a base. Their model is still drawn at full size.
	 */
	public static final float MAX_HITBOX = 4f;
	/**
	 * Base workers and raiders move around inside bases, through doors and between farms, so their hitbox is kept
	 * under a block wide (like a standard creature) whatever their size. Their model is still drawn at full size.
	 */
	public static final float BASE_HITBOX = 1.25f;
	/** In a battle every creature fits its square. */
	public static final float BATTLE_HITBOX = 1.4f;

	/** Shrinks the hitbox to at most {@code max} (used for battle copies). */
	public void capSize(float max) {
		var scale = this.getAttribute(Attributes.SCALE);
		if (scale != null && scale.getBaseValue() > max) scale.setBaseValue(max);
	}

	/** Sets species, influence and level; health below 0 means full. Updates the name tag. */
	public void setup(Species species, Influence influence, int level, float health) {
		influence = species.formOrDefault(influence);
		level = Math.clamp(level, 0, Stats.MAX_LEVEL);
		this.entityData.set(SPECIES, species.id());
		this.entityData.set(INFLUENCE, influence.getSerializedName());
		this.entityData.set(LEVEL, level);
		var maxHealth = this.getAttribute(Attributes.MAX_HEALTH);
		if (maxHealth != null) maxHealth.setBaseValue(species.statsFor(influence).maxHealth(level));
		var speed = this.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null) speed.setBaseValue(species.walkSpeed(influence));
		var scale = this.getAttribute(Attributes.SCALE);
		if (scale != null) scale.setBaseValue(Math.min(species.sizeFor(influence), MAX_HITBOX));
		this.setHealth(health > 0 ? Math.min(health, this.getMaxHealth()) : this.getMaxHealth());
		refreshName();
		refreshMount();
	}

	// ---- riding ----

	/** Works out (on the server) whether it can be ridden, how, and how fast, and sends it to players. */
	public void refreshMount() {
		var species = getSpecies();
		if (species.isEmpty()) return;
		Species s = species.get();
		float size = s.sizeFor(getInfluence());
		int flags = Mounts.rideable(size) ? RIDEABLE : 0;
		if (s.movement() == Species.Movement.FLY) flags |= FLIES;
		if (s.movement() == Species.Movement.SWIM) flags |= SWIMS;
		double speed = ridingSpeedStat();
		int tier = Mounts.tier(getMountTier(), speed);
		this.entityData.set(RIDE_FLAGS, flags);
		this.entityData.set(RIDE_TIER, tier);
		this.entityData.set(TOP_SPEED, (float) Mounts.blocksPerSecond(speed, tier));
		// the rider sits on its back: about 70% of the way up its model (drawn at full size even when the hitbox isn't)
		this.entityData.set(SEAT, size * 0.8f * 0.7f);
	}

	/** Its Speed stat at its level (server side). */
	public double ridingSpeedStat() {
		Stats st = getStats();
		return st.effective(st.speed(), getCreatureLevel());
	}

	public boolean isRideable() {
		return (this.entityData.get(RIDE_FLAGS) & RIDEABLE) != 0;
	}

	public boolean ridesFlying() {
		return (this.entityData.get(RIDE_FLAGS) & FLIES) != 0;
	}

	public boolean ridesSwimming() {
		return (this.entityData.get(RIDE_FLAGS) & SWIMS) != 0;
	}

	/** Its tier from treats (0-3). */
	public int getMountTier() {
		return this.entityData.get(MOUNT_TIER);
	}

	/** Treats eaten towards the next tier. */
	public int getMountFed() {
		return this.entityData.get(MOUNT_FED);
	}

	/** The tier it rides at (0-5): its treat tier, or 4 or 5 from its Speed. */
	public int getRideTier() {
		return this.entityData.get(RIDE_TIER);
	}

	/** Top speed ridden, in blocks per second. */
	public float getTopSpeed() {
		return this.entityData.get(TOP_SPEED);
	}

	public void setMountTraining(int tier, int fed) {
		this.entityData.set(MOUNT_TIER, Math.clamp(tier, 0, Mounts.MAX_FED_TIER));
		this.entityData.set(MOUNT_FED, Math.max(0, fed));
		refreshMount();
	}

	public boolean hasSaddle() {
		return this.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.SADDLE).is(net.minecraft.world.item.Items.SADDLE);
	}

	public void setSaddle(boolean on) {
		this.setItemSlot(net.minecraft.world.entity.EquipmentSlot.SADDLE, on ? new ItemStack(net.minecraft.world.item.Items.SADDLE) : ItemStack.EMPTY);
		if (on) this.setGuaranteedDrop(net.minecraft.world.entity.EquipmentSlot.SADDLE);
	}

	/** Takes on what a party creature carries besides species, level and health: XP, riding training and saddle. */
	public void applyCaught(CaughtCreature c) {
		this.xp = c.xp();
		setMountTraining(c.mountTier(), c.mountFed());
		if (c.saddled() && isRideable()) setSaddle(true);
	}

	@Override
	public net.minecraft.world.entity.LivingEntity getControllingPassenger() {
		if (hasSaddle() && isRideable() && this.getFirstPassenger() instanceof Player p) return p;
		return super.getControllingPassenger();
	}

	@Override
	protected boolean canAddPassenger(net.minecraft.world.entity.Entity passenger) {
		return this.getPassengers().isEmpty();
	}

	@Override
	protected net.minecraft.world.phys.Vec3 getPassengerAttachmentPoint(net.minecraft.world.entity.Entity passenger, net.minecraft.world.entity.EntityDimensions dimensions, float scale) {
		return new net.minecraft.world.phys.Vec3(0, this.entityData.get(SEAT), 0);
	}

	@Override
	protected boolean isImmobile() {
		return super.isImmobile() || this.getControllingPassenger() instanceof Player;
	}

	@Override
	public boolean isFlyingVehicle() {
		return ridesFlying();
	}

	@Override
	public float maxUpStep() {
		return this.isVehicle() ? Math.max(1.05f, super.maxUpStep()) : super.maxUpStep();
	}

	@Override
	public boolean causeFallDamage(double fallDistance, float multiplier, net.minecraft.world.damagesource.DamageSource source) {
		// fliers land softly, and so does whoever's riding them
		if (ridesFlying()) return false;
		return super.causeFallDamage(fallDistance, multiplier, source);
	}

	@Override
	protected void tickRidden(Player rider, net.minecraft.world.phys.Vec3 input) {
		super.tickRidden(rider, input);
		this.setRot(rider.getYRot(), rider.getXRot() * 0.5f);
		this.yRotO = this.yBodyRot = this.yHeadRot = this.getYRot();
		boolean jump = rider.isJumping();
		if (ridesFlying()) {
			if (!riddenFlying && jump && !this.isInWater()) {
				// jump to take off
				riddenFlying = true;
				takeoffTicks = 10;
				this.setDeltaMovement(this.getDeltaMovement().add(0, 0.6, 0));
			} else if (riddenFlying && takeoffTicks > 0) takeoffTicks--;
			else if (riddenFlying && (this.onGround() || this.isInWater()) && !jump) riddenFlying = false; // landed
			this.resetFallDistance();
		}
		boolean swimming = ridesSwimming() && this.isInWater();
		if (!riddenFlying && !swimming && jump && this.onGround()) {
			var v = this.getDeltaMovement();
			this.setDeltaMovement(v.x, 0.6, v.z);
		}
		// a walker in water paddles up while the rider holds jump
		this.setJumping(jump && this.isInWater() && !swimming);
	}

	@Override
	protected net.minecraft.world.phys.Vec3 getRiddenInput(Player rider, net.minecraft.world.phys.Vec3 input) {
		float strafe = rider.xxa * 0.5f, forward = rider.zza;
		if (forward <= 0 && !riddenFlying) forward *= 0.25f;
		return new net.minecraft.world.phys.Vec3(strafe, 0, forward);
	}

	@Override
	protected float getRiddenSpeed(Player rider) {
		return (float) (getTopSpeed() / Mounts.BLOCKS_PER_SECOND);
	}

	@Override
	public void travel(net.minecraft.world.phys.Vec3 input) {
		if (this.getControllingPassenger() instanceof Player rider) {
			if (ridesSwimming() && this.isInWater()) {
				move3d(rider, input, 0.85);
				return;
			}
			if (ridesFlying() && riddenFlying) {
				move3d(rider, input, 0.91);
				return;
			}
		} else riddenFlying = false;
		super.travel(input);
	}

	/** Flying or swimming: forward goes where the rider looks (up and down too), jump rises, no gravity. */
	private void move3d(Player rider, net.minecraft.world.phys.Vec3 input, double drag) {
		double perTick = getTopSpeed() / 20.0;
		double pitch = Math.toRadians(rider.getXRot());
		double forward = input.z;
		double up = -Math.sin(pitch) * forward + (rider.isJumping() ? 0.7 : 0);
		this.moveRelative((float) (perTick * (1 - drag)), new net.minecraft.world.phys.Vec3(input.x, up, forward * Math.cos(pitch)));
		this.move(net.minecraft.world.entity.MoverType.SELF, this.getDeltaMovement());
		this.setDeltaMovement(this.getDeltaMovement().scale(drag));
	}

	/** Feeding a treat: enough of them and it goes up a tier. */
	private InteractionResult feedTreat(Player player, ItemStack stack) {
		String name = displayName();
		if (!isRideable()) {
			player.sendOverlayMessage(Component.literal(name + " is too small to ride, so treats won't help."));
			return InteractionResult.FAIL;
		}
		int tier = getMountTier();
		if (tier >= Mounts.MAX_FED_TIER) {
			player.sendOverlayMessage(Component.literal(getRideTier() >= Mounts.MAX_TIER ? name + " is as fast as it gets."
					: "Treats can't take it past tier " + Mounts.MAX_FED_TIER + ". Tier 4 needs Speed " + Mounts.TIER_4_SPEED + " and tier 5 Speed "
					+ Mounts.TIER_5_SPEED + " (it has " + Math.round(ridingSpeedStat()) + "; it grows as it levels up)."));
			return InteractionResult.FAIL;
		}
		Mounts.Step step = Mounts.stepFrom(tier);
		if (!stack.is(step.treat())) {
			player.sendOverlayMessage(Component.literal(name + " is tier " + tier + "; it needs ").append(step.treat().getDefaultInstance().getHoverName()).append(" next."));
			return InteractionResult.FAIL;
		}
		stack.consume(1, player);
		ServerLevel level = (ServerLevel) this.level();
		int fed = getMountFed() + 1;
		double y = this.getY() + this.getBbHeight() * 0.8;
		if (fed >= step.amount()) {
			setMountTraining(tier + 1, 0);
			level.playSound(null, this.blockPosition(), net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP, net.minecraft.sounds.SoundSource.NEUTRAL, 1f, 1.2f);
			level.sendParticles(net.minecraft.core.particles.ParticleTypes.TOTEM_OF_UNDYING, this.getX(), y, this.getZ(), 30, 0.5, 0.6, 0.5, 0.3);
			player.sendOverlayMessage(Component.literal("Speed tier " + getRideTier() + "! Top speed " + Math.round(getTopSpeed()) + " blocks/sec")
					.withStyle(net.minecraft.ChatFormatting.GOLD));
		} else {
			setMountTraining(tier, fed);
			level.playSound(null, this.blockPosition(), net.minecraft.sounds.SoundEvents.GENERIC_EAT.value(), net.minecraft.sounds.SoundSource.NEUTRAL, 1f, 1f);
			level.sendParticles(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER, this.getX(), y, this.getZ(), 6, 0.4, 0.4, 0.4, 0);
			player.sendOverlayMessage(Component.literal("Tier " + tier + ": " + fed + "/" + step.amount() + " ").append(step.treat().getDefaultInstance().getHoverName())
					.append(" to tier " + (tier + 1)));
		}
		return InteractionResult.SUCCESS;
	}

	/** Saddles, treats, shears and getting on (server side; the creature is the player's own and not a base worker). */
	private InteractionResult rideInteract(Player player, ItemStack stack) {
		String name = displayName();
		if (Mounts.isTreat(stack)) return feedTreat(player, stack);
		if (stack.is(net.minecraft.world.item.Items.SADDLE) && !hasSaddle()) {
			if (!isRideable()) {
				player.sendOverlayMessage(Component.literal(name + " is too small to ride. Medium and bigger creatures can be saddled."));
				return InteractionResult.FAIL;
			}
			setSaddle(true);
			stack.consume(1, player);
			this.level().playSound(null, this.blockPosition(), net.minecraft.sounds.SoundEvents.HORSE_SADDLE.value(), net.minecraft.sounds.SoundSource.NEUTRAL, 1f, 1f);
			player.sendOverlayMessage(Component.literal(name + " is saddled. Right-click to ride" + (ridesFlying() ? "; jump to take off." : ridesSwimming() ? "; it swims too." : ".")));
			return InteractionResult.SUCCESS;
		}
		if (stack.is(net.minecraft.world.item.Items.SHEARS) && hasSaddle()) {
			setSaddle(false);
			ItemStack saddle = new ItemStack(net.minecraft.world.item.Items.SADDLE);
			if (!player.getInventory().add(saddle)) this.spawnAtLocation((ServerLevel) this.level(), saddle);
			this.level().playSound(null, this.blockPosition(), net.minecraft.sounds.SoundEvents.SADDLE_UNEQUIP.value(), net.minecraft.sounds.SoundSource.NEUTRAL, 1f, 1f);
			player.sendOverlayMessage(Component.literal("Took the saddle off " + name + "."));
			return InteractionResult.SUCCESS;
		}
		if (hasSaddle() && !player.isShiftKeyDown() && !this.isVehicle()) {
			this.setOrderedToSit(false);
			player.startRiding(this);
			return InteractionResult.SUCCESS;
		}
		return null;
	}

	private void refreshName() {
		this.setCustomName(Component.literal(displayName() + " Lv." + getCreatureLevel()).withColor(getInfluence().color()));
		this.setCustomNameVisible(true);
	}

	@Override
	public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason, @Nullable SpawnGroupData data) {
		// wild creatures are chosen by the biome they appear in, and take its influence; if nothing lives
		// here (e.g. a spawn egg), any species will do
		if (getSpeciesId().isEmpty()) {
			var picked = Species.pickFor(level.getBiome(this.blockPosition()), level.getLevel(), this.random);
			if (picked.isEmpty() && !Species.all().isEmpty()) {
				var all = Species.all();
				Species s = all.get(this.random.nextInt(all.size()));
				var inf = InfluenceRules.pick(level.getBiome(this.blockPosition()), level.getLevel(), s.forms().keySet(), this.random);
				picked = java.util.Optional.of(new Species.Pick(s, inf.orElse(s.formOrDefault(Influence.NONE))));
			}
			picked.ifPresent(p -> {
				int[] range = p.species().levelRange(p.influence());
				setup(p.species(), p.influence(), range[0] + this.random.nextInt(Math.max(1, range[1] - range[0] + 1)), -1);
			});
		}
		return super.finalizeSpawn(level, difficulty, reason, data);
	}

	/** Wild creatures wander off when nobody is near (tamed ones and ones in a battle stay). */
	@Override
	public boolean removeWhenFarAway(double distanceSqr) {
		return !this.isTame() && !dev.ahrivaea.battle.Battles.inBattle(this);
	}

	@Override
	public boolean checkSpawnRules(net.minecraft.world.level.LevelAccessor level, EntitySpawnReason reason) {
		if ((reason == EntitySpawnReason.NATURAL || reason == EntitySpawnReason.CHUNK_GENERATION) && level instanceof ServerLevelAccessor server) {
			if (Species.pickFor(level.getBiome(this.blockPosition()), server.getLevel(), this.random).isEmpty()) return false;
		}
		return super.checkSpawnRules(level, reason);
	}

	@Override
	public InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (this.isTame() && this.isOwnedBy(player) && this.hasAttached(dev.ahrivaea.base.Bases.WORKER_OF)) {
			if (!this.level().isClientSide()) player.sendOverlayMessage(Component.literal(displayName() + ": "
					+ (brain() != null ? brain().status() : "working") + ". (Use the base core to send it away.)"));
			return InteractionResult.SUCCESS;
		}
		if (this.isTame() && this.isOwnedBy(player)) {
			if (!this.level().isClientSide()) {
				InteractionResult ride = rideInteract(player, player.getItemInHand(hand));
				if (ride != null) return ride;
				if (player.isShiftKeyDown()) {
					// sneak + right click: call it back into the party
					recallTo(player);
				} else {
					this.setOrderedToSit(!this.isOrderedToSit());
					player.sendOverlayMessage(Component.literal(displayName() + (this.isOrderedToSit() ? " will wait here." : " will follow you.")));
				}
			}
			return InteractionResult.SUCCESS;
		}
		ItemStack held = player.getItemInHand(hand);
		if (held.is(net.minecraft.world.item.Items.SADDLE) || Mounts.isTreat(held)) {
			if (!this.level().isClientSide()) player.sendOverlayMessage(Component.literal("Only your own creatures can be saddled and fed treats. Catch it first."));
			return InteractionResult.FAIL;
		}
		return super.mobInteract(player, hand);
	}

	@Override
	public void tick() {
		super.tick();
		// a creature made without a species (for example /summon with extra data) picks one at random
		if (!this.level().isClientSide() && getSpeciesId().isEmpty() && !Species.all().isEmpty()) {
			var all = Species.all();
			Species s = all.get(this.random.nextInt(all.size()));
			setup(s, s.formOrDefault(Influence.NONE), 5, -1);
		}
		// a swimming mount keeps its rider breathing underwater
		if (!this.level().isClientSide() && this.tickCount % 20 == 0 && ridesSwimming() && this.getControllingPassenger() instanceof Player rider)
			rider.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.WATER_BREATHING, 60, 0, true, false));
	}

	/** Back into its owner's party (or box if the party is full); it leaves the world. */
	public void recallTo(Player player) {
		this.ejectPassengers();
		boolean party = Party.addAnywhere(player, toCaught());
		player.sendOverlayMessage(Component.literal(displayName() + (party ? " came back to your party." : " went to your box (party full).")));
		this.discard();
	}

	public CaughtCreature toCaught() {
		return new CaughtCreature(getSpeciesId(), getInfluence(), getCreatureLevel(), xp, this.getHealth(), getMountTier(), getMountFed(), hasSaddle());
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putString("Species", getSpeciesId());
		output.putInt("CreatureLevel", getCreatureLevel());
		output.putString("Influence", getInfluence().getSerializedName());
		output.putInt("CreatureXp", xp);
		output.putInt("MountTier", getMountTier());
		output.putInt("MountFed", getMountFed());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.entityData.set(SPECIES, input.getStringOr("Species", ""));
		this.entityData.set(LEVEL, input.getIntOr("CreatureLevel", 5));
		this.entityData.set(INFLUENCE, input.getStringOr("Influence", "none"));
		xp = input.getIntOr("CreatureXp", 0);
		this.entityData.set(MOUNT_TIER, input.getIntOr("MountTier", 0));
		this.entityData.set(MOUNT_FED, input.getIntOr("MountFed", 0));
		refreshName();
		refreshMount();
	}

	@Override
	public boolean isFood(ItemStack stack) {
		return false;
	}

	@Override
	public @Nullable AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
		return null;
	}
}
