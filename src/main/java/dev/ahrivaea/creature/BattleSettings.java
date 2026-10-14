package dev.ahrivaea.creature;

/** Tuning numbers for stats and damage. They match the "Formula Settings" tab of the balance spreadsheet. */
public final class BattleSettings {
	private BattleSettings() {}

	public static final double LEVEL0_FRACTION = 0.25;
	public static final double HP_FLAT = 20, HP_MULTIPLIER = 2;
	public static final double STAT_OFFSET = 10;
	public static final double SAME_INFLUENCE_BONUS = 1.2;
	public static final double CRIT_MULTIPLIER = 1.5;
	public static final double BASE_CRIT = 0.05, CRIT_PER_ATTACKER_LUCK = 0.0015, CRIT_PER_DEFENDER_LUCK = 0.0005;
	public static final double HIT_PER_LUCK_DIFFERENCE = 0.0025;
	public static final double CHARISMA_SUPPORT_PER_POINT = 0.001;
	public static final double WATER_PENALTY = 0.5;
	public static final int MOVE_BASE = 3, SPEED_PER_SQUARE = 25;

	public static final int XP_PER_DEFEAT = 10;
	public static final int XP_PER_LEVEL = 100;
	public static final int RECRUIT_XP_PER_LEVEL = 10;
}
