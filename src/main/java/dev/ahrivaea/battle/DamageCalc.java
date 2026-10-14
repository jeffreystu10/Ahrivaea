package dev.ahrivaea.battle;

import dev.ahrivaea.creature.Influence;
import dev.ahrivaea.creature.Stats;

import static dev.ahrivaea.creature.BattleSettings.*;

/** The damage formula from docs/design.md. Pure maths, no Minecraft state, so it can be tested on its own. */
public final class DamageCalc {
	private DamageCalc() {}

	public enum Category { PHYSICAL, MAGIC }

	/** One side of an exchange: a creature's stats, level and influence, and whether it stands in water. */
	public record Fighter(Stats stats, int level, Influence influence, boolean inWater) {
		double eff(int base) {
			return stats.effective(base, level);
		}
	}

	/** A move as far as damage is concerned. Accuracy is 0–1. */
	public record Move(int power, Category category, Influence influence, double accuracy) {}

	public record Result(int damage, double multiplier, double hitChance, double critChance, double expected) {}

	/**
	 * @param crit        whether this hit is a critical
	 * @param roll        the random damage roll, 0.9–1.1 (1 = average)
	 * @param allyCharisma total effective Charisma of allies next to the attacker
	 */
	public static Result calculate(Fighter attacker, Fighter defender, Move move, boolean crit, double roll, double allyCharisma) {
		double base = move.power() * Stats.levelScale(attacker.level());
		double ratio = move.category() == Category.PHYSICAL
				? (attacker.eff(attacker.stats().attack()) + STAT_OFFSET) / (defender.eff(defender.stats().defense()) + STAT_OFFSET)
				: (attacker.eff(attacker.stats().magic()) + STAT_OFFSET) / (defender.eff(defender.stats().magic()) + STAT_OFFSET);
		double influence = move.influence().against(defender.influence());
		double same = move.influence() == attacker.influence() && move.influence() != Influence.NONE ? SAME_INFLUENCE_BONUS : 1;
		double support = 1 + allyCharisma * CHARISMA_SUPPORT_PER_POINT;
		// standing in water halves damage, except magic from a Water move
		boolean waterExempt = move.category() == Category.MAGIC && move.influence() == Influence.WATER;
		double water = attacker.inWater() && !waterExempt ? WATER_PENALTY : 1;
		double noCrit = base * ratio * influence * same * support * water * roll;
		int damage = Math.max(1, (int) Math.round(noCrit * (crit ? CRIT_MULTIPLIER : 1)));

		double luckA = attacker.eff(attacker.stats().luck()), luckD = defender.eff(defender.stats().luck());
		double hit = Math.clamp(move.accuracy() + (luckA - luckD) * HIT_PER_LUCK_DIFFERENCE, 0, 1);
		double critChance = Math.clamp(BASE_CRIT + luckA * CRIT_PER_ATTACKER_LUCK - luckD * CRIT_PER_DEFENDER_LUCK, 0, 1);
		double expected = hit * noCrit * (1 + critChance * (CRIT_MULTIPLIER - 1));
		return new Result(damage, influence, hit, critChance, expected);
	}
}
