package dev.ahrivaea.battle;

import dev.ahrivaea.creature.Influence;
import dev.ahrivaea.creature.Species;
import dev.ahrivaea.creature.Stats;

import java.util.List;
import java.util.UUID;

/** One piece on the battlefield: the player, one of their creatures, or an enemy. */
public final class Unit {
	public enum Side { PLAYER, ENEMY }

	public final int id;
	public final Side side;
	public final boolean isPlayer;
	public final String name;
	public final Stats stats;
	public final int level;
	public final Influence influence;
	public final Species.Movement movement;
	public final List<BattleMove> moves;
	public final int maxHp;
	public int hp;
	public int x, z;
	public boolean acted;
	public double rally = 1;
	/** The entity standing for this unit in the world (a creature, or the player). */
	public UUID entity;
	/** For creatures: which party slot (player side) or species id (enemy side). */
	public final String speciesId;
	public final int partySlot;

	public Unit(int id, Side side, boolean isPlayer, String name, Stats stats, int level, Influence influence, Species.Movement movement,
			List<BattleMove> moves, int maxHp, int hp, String speciesId, int partySlot) {
		this.id = id;
		this.side = side;
		this.isPlayer = isPlayer;
		this.name = name;
		this.stats = stats;
		this.level = level;
		this.influence = influence;
		this.movement = movement;
		this.moves = moves;
		this.maxHp = maxHp;
		this.hp = hp;
		this.speciesId = speciesId;
		this.partySlot = partySlot;
	}

	public boolean alive() {
		return hp > 0;
	}

	public int squares() {
		return stats.squaresPerTurn();
	}

	public boolean flies() {
		return movement == Species.Movement.FLY;
	}

	public DamageCalc.Fighter fighter(boolean inWater) {
		return new DamageCalc.Fighter(stats, level, influence, inWater, isPlayer);
	}

	/** Charisma as it counts this battle (scaled by level for creatures). */
	public double charisma() {
		return isPlayer ? stats.charisma() : stats.effective(stats.charisma(), level);
	}
}
