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

	public CreatureEntity(EntityType<? extends TamableAnimal> type, Level level) {
		super(type, level);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(SPECIES, "");
		builder.define(LEVEL, 5);
		builder.define(INFLUENCE, "none");
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		// at a base, work comes before anything else (following the owner, wandering ...)
		this.goalSelector.addGoal(1, new WorkGoal());
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
		if (scale != null) scale.setBaseValue(species.size());
		this.setHealth(health > 0 ? Math.min(health, this.getMaxHealth()) : this.getMaxHealth());
		refreshName();
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
				if (player.isShiftKeyDown()) {
					// sneak + right click: call it back into the party
					boolean party = Party.addAnywhere(player, toCaught());
					player.sendOverlayMessage(Component.literal(displayName() + (party ? " came back to your party." : " went to your box (party full).")));
					this.discard();
				} else {
					this.setOrderedToSit(!this.isOrderedToSit());
					player.sendOverlayMessage(Component.literal(displayName() + (this.isOrderedToSit() ? " will wait here." : " will follow you.")));
				}
			}
			return InteractionResult.SUCCESS;
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
	}

	public CaughtCreature toCaught() {
		return new CaughtCreature(getSpeciesId(), getInfluence(), getCreatureLevel(), 0, this.getHealth());
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putString("Species", getSpeciesId());
		output.putInt("CreatureLevel", getCreatureLevel());
		output.putString("Influence", getInfluence().getSerializedName());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.entityData.set(SPECIES, input.getStringOr("Species", ""));
		this.entityData.set(LEVEL, input.getIntOr("CreatureLevel", 5));
		this.entityData.set(INFLUENCE, input.getStringOr("Influence", "none"));
		refreshName();
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
