package dev.ahrivaea;

import dev.ahrivaea.battle.DamageCalc;
import dev.ahrivaea.creature.Influence;
import dev.ahrivaea.creature.Species;
import dev.ahrivaea.creature.Stats;

import java.util.function.Consumer;

/**
 * Checks the battle maths against the worked example in the balance spreadsheet (Ashen vs Briar Dragon,
 * both level 30, a 40-power Fire magic move: 50 damage, 88% to hit, 10% to crit) and the favor chart's balance.
 * Run with /ahrivaea selftest; the automatic server test runs it too.
 */
public final class SelfTest {
	private SelfTest() {}

	public static boolean run(Consumer<String> out) {
		boolean ok = true;
		Consumer<String> say = msg -> {
			out.accept(msg);
			Ahrivaea.LOGGER.info("AHRIVAEA_TEST {}", msg);
		};

		// favor chart: every influence but None has 2 strengths and 2 weaknesses
		for (Influence a : Influence.values()) {
			int strong = 0, weak = 0;
			for (Influence d : Influence.values()) {
				if (a.against(d) == 2) strong++;
				if (d.against(a) == 2) weak++;
			}
			int want = a == Influence.NONE ? 0 : 2;
			if (strong != want || weak != want) {
				say.accept("FAIL favor chart: " + a + " strong " + strong + " weak " + weak);
				ok = false;
			}
		}

		var dragon = Species.get("dragon");
		if (dragon.isEmpty()) {
			say.accept("FAIL no dragon species loaded");
			return false;
		}
		Stats ashen = dragon.get().statsFor(Influence.FIRE), briar = dragon.get().statsFor(Influence.NATURE);
		var r = DamageCalc.calculate(
				new DamageCalc.Fighter(ashen, 30, Influence.FIRE, false),
				new DamageCalc.Fighter(briar, 30, Influence.NATURE, false),
				new DamageCalc.Move(40, DamageCalc.Category.MAGIC, Influence.FIRE, 0.9), false, 1.0, 0);
		String line = String.format("damage %d x%.1f hit %.1f%% crit %.1f%% (spreadsheet: 50, x2.0, 88.1%%, 10.2%%) %s vs %s, max HP %d",
				r.damage(), r.multiplier(), r.hitChance() * 100, r.critChance() * 100,
				dragon.get().nameFor(Influence.FIRE), dragon.get().nameFor(Influence.NATURE), briar.maxHealth(30));
		boolean match = r.damage() == 50 && Math.abs(r.hitChance() - 0.88075) < 1e-6 && Math.abs(r.critChance() - 0.10215) < 1e-6;
		say.accept((match ? "PASS " : "FAIL ") + line);
		ok &= match;

		// standing in water halves damage, except a Water magic move
		var dry = DamageCalc.calculate(new DamageCalc.Fighter(ashen, 50, Influence.WATER, false), new DamageCalc.Fighter(briar, 50, Influence.FIRE, false),
				new DamageCalc.Move(40, DamageCalc.Category.PHYSICAL, Influence.NONE, 1), false, 1.0, 0).damage();
		var wet = DamageCalc.calculate(new DamageCalc.Fighter(ashen, 50, Influence.WATER, true), new DamageCalc.Fighter(briar, 50, Influence.FIRE, false),
				new DamageCalc.Move(40, DamageCalc.Category.PHYSICAL, Influence.NONE, 1), false, 1.0, 0).damage();
		var wetMagic = DamageCalc.calculate(new DamageCalc.Fighter(ashen, 50, Influence.WATER, true), new DamageCalc.Fighter(briar, 50, Influence.FIRE, false),
				new DamageCalc.Move(40, DamageCalc.Category.MAGIC, Influence.WATER, 1), false, 1.0, 0).damage();
		var dryMagic = DamageCalc.calculate(new DamageCalc.Fighter(ashen, 50, Influence.WATER, false), new DamageCalc.Fighter(briar, 50, Influence.FIRE, false),
				new DamageCalc.Move(40, DamageCalc.Category.MAGIC, Influence.WATER, 1), false, 1.0, 0).damage();
		boolean water = Math.abs(wet - dry / 2.0) <= 1 && wetMagic == dryMagic;
		say.accept((water ? "PASS " : "FAIL ") + "water: physical " + dry + " -> " + wet + " in water; water magic " + dryMagic + " -> " + wetMagic);
		ok &= water;

		say.accept(ok ? "selftest passed" : "selftest FAILED");
		return ok;
	}
}
