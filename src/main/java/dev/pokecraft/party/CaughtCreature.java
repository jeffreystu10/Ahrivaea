package dev.pokecraft.party;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** A creature stored in a player's party (not out in the world). */
public record CaughtCreature(String species, int level, float health) {
	public static final Codec<CaughtCreature> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("species").forGetter(CaughtCreature::species),
			Codec.INT.fieldOf("level").forGetter(CaughtCreature::level),
			Codec.FLOAT.fieldOf("health").forGetter(CaughtCreature::health)
	).apply(i, CaughtCreature::new));
}
