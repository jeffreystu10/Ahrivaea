package dev.ahrivaea.creature;

import net.minecraft.util.StringRepresentable;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Elemental influence. A creature takes its influence from the biome and time of day it spawns in;
 * it changes the creature's look, name and some of its moves. See docs/design.md for the favor chart.
 */
public enum Influence implements StringRepresentable {
	NONE("None", 0xA8A29E),
	FIRE("Fire", 0xE4572E),
	FROST("Frost", 0x9AD1F5),
	STORM("Storm", 0x5B7DB1),
	EARTH("Earth", 0x9C7A4B),
	WATER("Water", 0x2E86C1),
	NATURE("Nature", 0x4E9F3D),
	VOID("Void", 0x2B1B3F),
	FAE("Fae", 0xE58FD8),
	DARKNESS("Darkness", 0x3A3A44),
	RADIANT("Radiant", 0xF4D35E),
	ARCANE("Arcane", 0x7B5CD6);

	public static final StringRepresentable.EnumCodec<Influence> CODEC = StringRepresentable.fromEnum(Influence::values);

	private final String displayName;
	private final int color;

	Influence(String displayName, int color) {
		this.displayName = displayName;
		this.color = color;
	}

	@Override
	public String getSerializedName() {
		return name().toLowerCase(java.util.Locale.ROOT);
	}

	public String displayName() {
		return displayName;
	}

	/** A representative colour, used for name tags and placeholder textures. */
	public int color() {
		return color;
	}

	/**
	 * How rare the influence is among wild spawns: 1 for None (the most common), 2 for Fire, Water, Nature, Storm,
	 * Earth and Frost, 3 for Darkness, Void, Radiant, Fae and Arcane (the rarest).
	 */
	public int spawnTier() {
		return switch (this) {
			case NONE -> 1;
			case FIRE, WATER, NATURE, STORM, EARTH, FROST -> 2;
			default -> 3;
		};
	}

	/**
	 * The share of wild spawns each tier gets, as weights: 4 : 3 : 2, so about 44% tier 1, 33% tier 2 and 22% tier 3
	 * where a biome has creatures of all three (a tier with nothing in the biome passes its share to the others).
	 */
	public static final int[] TIER_WEIGHT = {0, 4, 3, 2};

	public static Influence byName(String name) {
		Influence i = CODEC.byName(name);
		return i == null ? NONE : i;
	}

	private static final Map<Influence, Set<Influence>> STRONG = new EnumMap<>(Influence.class);

	static {
		strong(FIRE, NATURE, FROST);
		strong(FROST, STORM, NATURE);
		strong(STORM, WATER, FROST);
		strong(EARTH, FIRE, STORM);
		strong(WATER, FIRE, EARTH);
		strong(NATURE, WATER, EARTH);
		strong(RADIANT, DARKNESS, FAE);
		strong(DARKNESS, ARCANE, VOID);
		strong(ARCANE, FAE, RADIANT);
		strong(FAE, VOID, DARKNESS);
		strong(VOID, RADIANT, ARCANE);
	}

	private static void strong(Influence attacker, Influence... targets) {
		STRONG.put(attacker, EnumSet.of(targets[0], targets));
	}

	private boolean beats(Influence other) {
		return STRONG.getOrDefault(this, Set.of()).contains(other);
	}

	/**
	 * Damage multiplier when a move of this influence hits a creature of the defending influence:
	 * 2 if this one is strong against it; ½ if the defender is strong against this one or they share an
	 * influence (other than None); otherwise 1.
	 */
	public double against(Influence defender) {
		if (beats(defender)) return 2.0;
		if (defender.beats(this) || (this == defender && this != NONE)) return 0.5;
		return 1.0;
	}
}
