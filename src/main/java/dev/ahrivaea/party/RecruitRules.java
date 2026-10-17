package dev.ahrivaea.party;

import dev.ahrivaea.creature.Influence;
import dev.ahrivaea.creature.Species;

import java.util.List;

/**
 * Rarity rules. Recruiting after a battle: any defeated creature, if you have the creature XP: 100 per level, times
 * 1 (Common), 1.5 (Uncommon), 2 (Rare), 3 (Super Rare) or 5 (Legendary). Reinforcements: the wild creatures that join
 * a battle are never rarer or higher level than the one you challenged (and at most 2 levels below it).
 */
public final class RecruitRules {
	private RecruitRules() {}

	public static final List<String> RARITIES = List.of("Common", "Uncommon", "Rare", "Super Rare", "Legendary");
	private static final double[] COST_MULTIPLIER = {1, 1.5, 2, 3, 5};
	public static final int COST_PER_LEVEL = 100;
	/** Reinforcements are at most this many levels below the creature you challenged. */
	public static final int REINFORCEMENT_LEVELS_BELOW = 2;

	public static int rank(String rarity) {
		int i = RARITIES.indexOf(rarity);
		return i < 0 ? 0 : i;
	}

	public static String rarity(Species s, Influence influence) {
		// a form without its own rarity takes the species' other forms'
		return s.form(influence).rarity().or(() -> s.forms().values().stream().map(f -> f.rarity()).flatMap(java.util.Optional::stream).findFirst()).orElse("Common");
	}

	/** Whether a creature may join a battle as a reinforcement for the wild creature you challenged. */
	public static boolean canReinforce(Species s, Influence influence, int level, Species wild, Influence wildInfluence, int wildLevel) {
		return rank(rarity(s, influence)) <= rank(rarity(wild, wildInfluence)) && level <= wildLevel && level >= wildLevel - REINFORCEMENT_LEVELS_BELOW;
	}

	/** A reinforcement from this biome for the challenged creature: no rarer, no higher level, at most 2 levels lower. */
	public static java.util.Optional<Species.LevelledPick> reinforcement(net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> biome,
			net.minecraft.world.level.Level level, net.minecraft.util.RandomSource random, Species wild, Influence wildInfluence, int wildLevel) {
		int maxRank = rank(rarity(wild, wildInfluence));
		return Species.pickForLevels(biome, level, random, Math.max(0, wildLevel - REINFORCEMENT_LEVELS_BELOW), wildLevel,
				(sp, in) -> rank(rarity(sp, in)) <= maxRank);
	}

	public static int cost(Species s, Influence influence, int level) {
		return (int) Math.round(COST_PER_LEVEL * Math.max(1, level) * COST_MULTIPLIER[rank(rarity(s, influence))]);
	}
}
