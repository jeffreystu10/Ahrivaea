package dev.ahrivaea.battle;

import dev.ahrivaea.creature.Influence;

/** A move as used on the grid: the damage side ({@link DamageCalc.Move}) plus its name and range in squares. */
public record BattleMove(String name, DamageCalc.Move move, int minRange, int maxRange) {
	public boolean reaches(int distance) {
		return distance >= minRange && distance <= maxRange;
	}

	/** Every creature's moves until there's a move list: a melee Strike and a ranged bolt of its own influence. */
	public static java.util.List<BattleMove> forCreature(Influence influence) {
		String bolt = influence == Influence.NONE ? "Force Bolt" : influence.displayName() + " Bolt";
		return java.util.List.of(
				new BattleMove("Strike", new DamageCalc.Move(40, DamageCalc.Category.PHYSICAL, Influence.NONE, 0.95), 1, 1),
				new BattleMove(bolt, new DamageCalc.Move(35, DamageCalc.Category.MAGIC, influence, 0.90), 1, 2));
	}

	/** The player's attack with what they're holding: a bow or crossbow shoots 2–3 squares, anything else is melee. */
	public static BattleMove forWeapon(String itemName, double attackDamage, boolean ranged) {
		if (ranged) return new BattleMove(itemName, new DamageCalc.Move(35, DamageCalc.Category.PHYSICAL, Influence.NONE, 0.85), 2, 3);
		int power = (int) Math.round(20 + 4 * attackDamage);
		return new BattleMove(itemName, new DamageCalc.Move(power, DamageCalc.Category.PHYSICAL, Influence.NONE, 0.95), 1, 1);
	}
}
