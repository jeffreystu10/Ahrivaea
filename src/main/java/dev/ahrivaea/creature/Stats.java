package dev.ahrivaea.creature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * The seven stats, each 0–100 at level 50. A species' seven should add up to 500–600.
 * Speed is movement on the battle grid; Magic both deals and resists magic damage;
 * Luck helps dodge and land critical hits; Charisma supports nearby allies.
 */
public record Stats(int hp, int attack, int defense, int speed, int magic, int luck, int charisma) {
	public static final int MAX_LEVEL = 50;
	public static final int MIN_TOTAL = 500, MAX_TOTAL = 600;

	private static final Codec<Integer> STAT = Codec.intRange(0, 100);

	public static final Codec<Stats> CODEC = RecordCodecBuilder.create(i -> i.group(
			STAT.fieldOf("hp").forGetter(Stats::hp),
			STAT.fieldOf("attack").forGetter(Stats::attack),
			STAT.fieldOf("defense").forGetter(Stats::defense),
			STAT.fieldOf("speed").forGetter(Stats::speed),
			STAT.fieldOf("magic").forGetter(Stats::magic),
			STAT.fieldOf("luck").forGetter(Stats::luck),
			STAT.fieldOf("charisma").forGetter(Stats::charisma)
	).apply(i, Stats::new));

	public int total() {
		return hp + attack + defense + speed + magic + luck + charisma;
	}

	/** Share of its level-50 stats a creature has at this level: 25% at level 0 rising to 100% at 50. */
	public static double levelScale(int level) {
		return BattleSettings.LEVEL0_FRACTION + (1 - BattleSettings.LEVEL0_FRACTION) * Math.clamp(level, 0, MAX_LEVEL) / (double) MAX_LEVEL;
	}

	public double effective(int base, int level) {
		return base * levelScale(level);
	}

	public int maxHealth(int level) {
		return (int) Math.round(BattleSettings.HP_FLAT + BattleSettings.HP_MULTIPLIER * effective(hp, level));
	}

	/** Squares per turn on the battle grid (from the level-50 Speed, so it doesn't grow with level). */
	public int squaresPerTurn() {
		return BattleSettings.MOVE_BASE + speed / BattleSettings.SPEED_PER_SQUARE;
	}
}
