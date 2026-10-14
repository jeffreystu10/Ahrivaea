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

	public CreatureEntity(EntityType<? extends TamableAnimal> type, Level level) {
		super(type, level);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(SPECIES, "");
		builder.define(LEVEL, 5);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new PanicGoal(this, 1.4));
		this.goalSelector.addGoal(2, new SitWhenOrderedToGoal(this));
		this.goalSelector.addGoal(3, new FollowOwnerGoal(this, 1.1, 6f, 2f));
		this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.9));
		this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 6f));
		this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
	}

	/** The species id; known on both server and client. */
	public String getSpeciesId() {
		return this.entityData.get(SPECIES);
	}

	/** The species data (server side; the client only knows the id). */
	public java.util.Optional<Species> getSpecies() {
		return Species.get(getSpeciesId());
	}

	public String displayName() {
		return getSpecies().map(Species::name).orElse(getSpeciesId());
	}

	public int getCreatureLevel() {
		return this.entityData.get(LEVEL);
	}

	/** Sets species and level, resets health to full and updates the name tag. */
	public void setup(Species species, int level, float health) {
		this.entityData.set(SPECIES, species.id());
		this.entityData.set(LEVEL, level);
		var maxHealth = this.getAttribute(Attributes.MAX_HEALTH);
		if (maxHealth != null) maxHealth.setBaseValue(species.healthAt(level));
		var speed = this.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null) speed.setBaseValue(species.walkSpeed());
		var scale = this.getAttribute(Attributes.SCALE);
		if (scale != null) scale.setBaseValue(species.scale());
		this.setHealth(health > 0 ? Math.min(health, this.getMaxHealth()) : this.getMaxHealth());
		refreshName();
	}

	private void refreshName() {
		this.setCustomName(Component.literal(displayName() + " Lv." + getCreatureLevel()));
		this.setCustomNameVisible(true);
	}

	@Override
	public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason, @Nullable SpawnGroupData data) {
		// wild creatures are chosen by the biome they appear in; summoned ones fall back to any species
		var picked = Species.pickFor(level.getBiome(this.blockPosition()), this.random);
		if (picked.isEmpty() && !Species.all().isEmpty()) {
			var all = Species.all();
			picked = java.util.Optional.of(all.get(this.random.nextInt(all.size())));
		}
		picked.ifPresent(species -> {
			int lo = species.spawn().map(Species.Spawn::minLevel).orElse(2);
			int hi = species.spawn().map(Species.Spawn::maxLevel).orElse(10);
			setup(species, lo + this.random.nextInt(Math.max(1, hi - lo + 1)), -1);
		});
		return super.finalizeSpawn(level, difficulty, reason, data);
	}

	@Override
	public boolean checkSpawnRules(net.minecraft.world.level.LevelAccessor level, EntitySpawnReason reason) {
		if (reason == EntitySpawnReason.NATURAL || reason == EntitySpawnReason.CHUNK_GENERATION) {
			if (Species.pickFor(level.getBiome(this.blockPosition()), this.random).isEmpty()) return false;
		}
		return super.checkSpawnRules(level, reason);
	}

	@Override
	public InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (this.isTame() && this.isOwnedBy(player)) {
			if (!this.level().isClientSide()) {
				if (player.isShiftKeyDown()) {
					// sneak + right click: call it back into the party
					Party.add(player, toCaught());
					player.sendOverlayMessage(Component.literal(displayName() + " came back to your party."));
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
			setup(all.get(this.random.nextInt(all.size())), 5, -1);
		}
	}

	public CaughtCreature toCaught() {
		return new CaughtCreature(getSpeciesId(), getCreatureLevel(), this.getHealth());
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putString("Species", getSpeciesId());
		output.putInt("CreatureLevel", getCreatureLevel());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.entityData.set(SPECIES, input.getStringOr("Species", ""));
		this.entityData.set(LEVEL, input.getIntOr("CreatureLevel", 5));
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
