package dev.ahrivaea.party;

import dev.ahrivaea.character.PlayerCharacter;
import dev.ahrivaea.creature.Influence;
import dev.ahrivaea.creature.Species;
import net.minecraft.world.entity.player.Player;

import java.util.List;

/**
 * Who you can take on: a creature no rarer and no higher level than the one you lead with (the first in your party,
 * the one you start a battle with). With no creatures, only Commons up to your character level. Recruiting after a
 * battle costs creature XP: 100 per level, times 1 (Common), 1.5 (Uncommon), 2 (Rare), 3 (Super Rare) or 5
 * (Legendary). Catching with a Vessel follows the same rarity and level limits (the Vessel is its cost).
 */
public final class RecruitRules {
	private RecruitRules() {}

	public static final List<String> RARITIES = List.of("Common", "Uncommon", "Rare", "Super Rare", "Legendary");
	private static final double[] COST_MULTIPLIER = {1, 1.5, 2, 3, 5};
	public static final int COST_PER_LEVEL = 100;

	/** The limit set by the lead creature: its rarity (0-4) and level. */
	public record Cap(int rarity, int level, String lead) {}

	public static int rank(String rarity) {
		int i = RARITIES.indexOf(rarity);
		return i < 0 ? 0 : i;
	}

	public static String rarity(Species s, Influence influence) {
		// a form without its own rarity takes the species' other forms'
		return s.form(influence).rarity().or(() -> s.forms().values().stream().map(f -> f.rarity()).flatMap(java.util.Optional::stream).findFirst()).orElse("Common");
	}

	/** The limit for this player right now: their lead creature (party slot 1), or their character if they have none. */
	public static Cap capFor(Player p) {
		List<CaughtCreature> party = Party.get(p);
		if (!party.isEmpty()) {
			CaughtCreature c = party.getFirst();
			var s = Species.get(c.species());
			if (s.isPresent()) return new Cap(rank(rarity(s.get(), c.influence())), c.level(), s.get().nameFor(c.influence()));
		}
		return new Cap(0, PlayerCharacter.get(p).level(), "you");
	}

	/** Why it can't be taken (or null if it can). */
	public static String whyNot(Cap cap, Species s, Influence influence, int level) {
		String r = rarity(s, influence);
		String lead = cap.lead().equals("you") ? "you (no creatures)" : "your lead " + cap.lead();
		if (rank(r) > cap.rarity()) return r + " is rarer than " + lead + " (" + RARITIES.get(cap.rarity()) + ")";
		if (level > cap.level()) return "Lv." + level + " is above " + lead + " (Lv." + cap.level() + ")";
		return null;
	}

	public static int cost(Species s, Influence influence, int level) {
		return (int) Math.round(COST_PER_LEVEL * Math.max(1, level) * COST_MULTIPLIER[rank(rarity(s, influence))]);
	}
}
