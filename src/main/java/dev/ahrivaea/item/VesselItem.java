package dev.ahrivaea.item;

import dev.ahrivaea.creature.CreatureEntity;
import dev.ahrivaea.party.Party;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** A Mithrile-bound vessel. Use it on a wild creature to try to bind it; weakened creatures are easier to catch. */
public class VesselItem extends Item {
	public VesselItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
		if (!(target instanceof CreatureEntity creature)) return InteractionResult.PASS;
		if (!(player.level() instanceof ServerLevel level)) return InteractionResult.SUCCESS;
		String name = creature.displayName();
		if (creature.isTame()) {
			player.sendOverlayMessage(Component.literal(name + " already has a trainer."));
			return InteractionResult.FAIL;
		}
		if (Party.get(player).size() >= Party.MAX) {
			player.sendOverlayMessage(Component.literal("Your party is full (6). Release one with /party release <slot>."));
			return InteractionResult.FAIL;
		}
		if (!player.getAbilities().instabuild) stack.shrink(1);
		float hpLeft = creature.getHealth() / creature.getMaxHealth();
		float chance = 0.45f * (1.6f - hpLeft);
		boolean caught = level.getRandom().nextFloat() < chance;
		double x = creature.getX(), y = creature.getY() + creature.getBbHeight() / 2, z = creature.getZ();
		if (caught) {
			Party.add(player, creature.toCaught());
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, x, y, z, 20, 0.4, 0.4, 0.4, 0.1);
			level.playSound(null, x, y, z, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, 1.4f);
			creature.discard();
			player.sendSystemMessage(Component.literal("Bound " + name + " (Lv." + creature.getCreatureLevel() + ")!"));
		} else {
			level.sendParticles(ParticleTypes.SMOKE, x, y, z, 15, 0.3, 0.3, 0.3, 0.02);
			level.playSound(null, x, y, z, SoundEvents.ITEM_BREAK.value(), SoundSource.PLAYERS, 0.8f, 1.2f);
			player.sendOverlayMessage(Component.literal(name + " broke free of the vessel! (" + Math.round(chance * 100) + "% chance)"));
		}
		return InteractionResult.SUCCESS;
	}
}
