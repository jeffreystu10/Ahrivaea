package dev.ahrivaea.creature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import java.util.Locale;

/** The eighteen creature types and how effective each attacking type is against each defending type. */
public enum CreatureType {
	NORMAL, FIRE, WATER, GRASS, ELECTRIC, ICE, FIGHTING, POISON, GROUND, FLYING, PSYCHIC, BUG, ROCK, GHOST, DRAGON, DARK, STEEL, FAIRY;

	public static final Codec<CreatureType> CODEC = Codec.STRING.comapFlatMap(
			s -> {
				try {
					return DataResult.success(valueOf(s.toUpperCase(Locale.ROOT)));
				} catch (IllegalArgumentException e) {
					return DataResult.error(() -> "Unknown creature type: " + s);
				}
			},
			CreatureType::id);

	// rows: attacking type, columns: defending type, in enum order. 2 = super effective, 1 = normal, 0.5 = not very, 0 = no effect
	private static final float[][] CHART = new float[18][18];

	private static void set(CreatureType atk, float mult, CreatureType... defs) {
		for (CreatureType d : defs) CHART[atk.ordinal()][d.ordinal()] = mult;
	}

	static {
		for (float[] row : CHART) java.util.Arrays.fill(row, 1f);
		set(NORMAL, 0.5f, ROCK, STEEL); set(NORMAL, 0f, GHOST);
		set(FIRE, 2f, GRASS, ICE, BUG, STEEL); set(FIRE, 0.5f, FIRE, WATER, ROCK, DRAGON);
		set(WATER, 2f, FIRE, GROUND, ROCK); set(WATER, 0.5f, WATER, GRASS, DRAGON);
		set(GRASS, 2f, WATER, GROUND, ROCK); set(GRASS, 0.5f, FIRE, GRASS, POISON, FLYING, BUG, DRAGON, STEEL);
		set(ELECTRIC, 2f, WATER, FLYING); set(ELECTRIC, 0.5f, ELECTRIC, GRASS, DRAGON); set(ELECTRIC, 0f, GROUND);
		set(ICE, 2f, GRASS, GROUND, FLYING, DRAGON); set(ICE, 0.5f, FIRE, WATER, ICE, STEEL);
		set(FIGHTING, 2f, NORMAL, ICE, ROCK, DARK, STEEL); set(FIGHTING, 0.5f, POISON, FLYING, PSYCHIC, BUG, FAIRY); set(FIGHTING, 0f, GHOST);
		set(POISON, 2f, GRASS, FAIRY); set(POISON, 0.5f, POISON, GROUND, ROCK, GHOST); set(POISON, 0f, STEEL);
		set(GROUND, 2f, FIRE, ELECTRIC, POISON, ROCK, STEEL); set(GROUND, 0.5f, GRASS, BUG); set(GROUND, 0f, FLYING);
		set(FLYING, 2f, GRASS, FIGHTING, BUG); set(FLYING, 0.5f, ELECTRIC, ROCK, STEEL);
		set(PSYCHIC, 2f, FIGHTING, POISON); set(PSYCHIC, 0.5f, PSYCHIC, STEEL); set(PSYCHIC, 0f, DARK);
		set(BUG, 2f, GRASS, PSYCHIC, DARK); set(BUG, 0.5f, FIRE, FIGHTING, POISON, FLYING, GHOST, STEEL, FAIRY);
		set(ROCK, 2f, FIRE, ICE, FLYING, BUG); set(ROCK, 0.5f, FIGHTING, GROUND, STEEL);
		set(GHOST, 2f, PSYCHIC, GHOST); set(GHOST, 0.5f, DARK); set(GHOST, 0f, NORMAL);
		set(DRAGON, 2f, DRAGON); set(DRAGON, 0.5f, STEEL); set(DRAGON, 0f, FAIRY);
		set(DARK, 2f, PSYCHIC, GHOST); set(DARK, 0.5f, FIGHTING, DARK, FAIRY);
		set(STEEL, 2f, ICE, ROCK, FAIRY); set(STEEL, 0.5f, FIRE, WATER, ELECTRIC, STEEL);
		set(FAIRY, 2f, FIGHTING, DRAGON, DARK); set(FAIRY, 0.5f, FIRE, POISON, STEEL);
	}

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public String displayName() {
		return name().charAt(0) + name().substring(1).toLowerCase(Locale.ROOT);
	}

	/** Damage multiplier when a move of this type hits a creature with the given types. */
	public float against(java.util.List<CreatureType> defender) {
		float m = 1f;
		for (CreatureType d : defender) m *= CHART[ordinal()][d.ordinal()];
		return m;
	}
}
