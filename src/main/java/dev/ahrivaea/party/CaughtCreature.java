package dev.ahrivaea.party;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.ahrivaea.creature.Influence;

/** A creature stored in a player's party (not out in the world). xp is progress toward the next level (0–99). */
public record CaughtCreature(String species, Influence influence, int level, int xp, float health) {
	public static final Codec<CaughtCreature> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("species").forGetter(CaughtCreature::species),
			Influence.CODEC.optionalFieldOf("influence", Influence.NONE).forGetter(CaughtCreature::influence),
			Codec.INT.fieldOf("level").forGetter(CaughtCreature::level),
			Codec.INT.optionalFieldOf("xp", 0).forGetter(CaughtCreature::xp),
			Codec.FLOAT.fieldOf("health").forGetter(CaughtCreature::health)
	).apply(i, CaughtCreature::new));

	public CaughtCreature withProgress(int level, int xp) {
		return new CaughtCreature(species, influence, level, xp, health);
	}
}
