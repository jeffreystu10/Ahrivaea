package dev.ahrivaea.base;

import dev.ahrivaea.battle.DamageCalc;
import dev.ahrivaea.creature.CreatureEntity;
import dev.ahrivaea.creature.Influence;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Real-time fighting at a base (raiders against defenders), using the battle damage formula: each creature uses
 * whichever of its moves would do more, Strike (melee) or its influence Bolt (magic, reaches 6 blocks).
 */
public final class Combat {
	private Combat() {}

	public static final double MELEE = 2.8, BOLT = 6;
	private static final DamageCalc.Move STRIKE = new DamageCalc.Move(40, DamageCalc.Category.PHYSICAL, Influence.NONE, 0.95);

	private static DamageCalc.Move bolt(CreatureEntity c) {
		return new DamageCalc.Move(35, DamageCalc.Category.MAGIC, c.getInfluence(), 0.90);
	}

	private static DamageCalc.Fighter fighter(CreatureEntity c) {
		return new DamageCalc.Fighter(c.getStats(), c.getCreatureLevel(), c.getInfluence(), c.isInWater(), false);
	}

	/** How far this creature can hit from (its better move's reach). */
	public static double reach(CreatureEntity a, CreatureEntity target) {
		var strike = DamageCalc.calculate(fighter(a), fighter(target), STRIKE, false, 1, 0);
		var magic = DamageCalc.calculate(fighter(a), fighter(target), bolt(a), false, 1, 0);
		return magic.expected() > strike.expected() ? BOLT : MELEE;
	}

	/** One attack. Returns the damage done (0 for a miss). */
	public static int hit(ServerLevel level, CreatureEntity a, CreatureEntity target) {
		var strike = DamageCalc.calculate(fighter(a), fighter(target), STRIKE, false, 1, 0);
		var magicMove = bolt(a);
		var magic = DamageCalc.calculate(fighter(a), fighter(target), magicMove, false, 1, 0);
		boolean useMagic = magic.expected() > strike.expected() && a.distanceTo(target) <= BOLT;
		DamageCalc.Move move = useMagic ? magicMove : STRIKE;
		var plan = useMagic ? magic : strike;
		if (level.getRandom().nextDouble() >= plan.hitChance()) {
			level.sendParticles(ParticleTypes.CLOUD, target.getX(), target.getY() + 1, target.getZ(), 4, 0.3, 0.3, 0.3, 0.02);
			return 0;
		}
		boolean crit = level.getRandom().nextDouble() < plan.critChance();
		int dmg = DamageCalc.calculate(fighter(a), fighter(target), move, crit, 0.9 + level.getRandom().nextDouble() * 0.2, 0).damage();
		target.hurtServer(level, level.damageSources().mobAttack(a), dmg);
		level.sendParticles(useMagic ? ParticleTypes.ENCHANTED_HIT : ParticleTypes.CRIT, target.getX(), target.getY() + 1, target.getZ(), 8, 0.3, 0.4, 0.3, 0.1);
		level.playSound(null, target.blockPosition(), useMagic ? SoundEvents.EVOKER_CAST_SPELL : SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.HOSTILE, 0.8f, 1.1f);
		return dmg;
	}
}
