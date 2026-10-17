package dev.ahrivaea.party;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.ahrivaea.creature.Influence;

/**
 * A creature stored in a player's party (not out in the world). xp is progress toward the next level (BattleSettings.xpToNext).
 * mountTier and mountFed are its riding training (see dev.ahrivaea.creature.Mounts); saddled is whether it wears one.
 */
public record CaughtCreature(String species, Influence influence, int level, int xp, float health, int mountTier, int mountFed, boolean saddled) {
	public static final Codec<CaughtCreature> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("species").forGetter(CaughtCreature::species),
			Influence.CODEC.optionalFieldOf("influence", Influence.NONE).forGetter(CaughtCreature::influence),
			Codec.INT.fieldOf("level").forGetter(CaughtCreature::level),
			Codec.INT.optionalFieldOf("xp", 0).forGetter(CaughtCreature::xp),
			Codec.FLOAT.fieldOf("health").forGetter(CaughtCreature::health),
			Codec.INT.optionalFieldOf("mount_tier", 0).forGetter(CaughtCreature::mountTier),
			Codec.INT.optionalFieldOf("mount_fed", 0).forGetter(CaughtCreature::mountFed),
			Codec.BOOL.optionalFieldOf("saddled", false).forGetter(CaughtCreature::saddled)
	).apply(i, CaughtCreature::new));

	public CaughtCreature(String species, Influence influence, int level, int xp, float health) {
		this(species, influence, level, xp, health, 0, 0, false);
	}

	public CaughtCreature withProgress(int level, int xp) {
		return new CaughtCreature(species, influence, level, xp, health, mountTier, mountFed, saddled);
	}

	public CaughtCreature withHealth(float health) {
		return new CaughtCreature(species, influence, level, xp, health, mountTier, mountFed, saddled);
	}
}
