package dev.pokecraft.item;

import dev.pokecraft.creature.CreatureEntity;
import dev.pokecraft.party.Party;
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

/** Use on a wild creature to try to catch it. Weaker creatures are easier to catch. */
public class CaptureBallItem extends Item {
	public CaptureBallItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
		if (!(target instanceof CreatureEntity creature)) return InteractionResult.PASS;
		if (!(player.level() instanceof ServerLevel level)) return InteractionResult.SUCCESS;
		String name = creature.getSpecies().displayName();
		if (creature.isTame()) {
			player.displayClientMessage(Component.literal(name + " already has a trainer."), true);
			return InteractionResult.FAIL;
		}
		if (Party.get(player).size() >= Party.MAX) {
			player.displayClientMessage(Component.literal("Your party is full (6). Release one with /party release <slot>."), true);
			return InteractionResult.FAIL;
		}
		if (!player.getAbilities().instabuild) stack.shrink(1);
		float hpLeft = creature.getHealth() / creature.getMaxHealth();
		float chance = creature.getSpecies().catchRate() * (1.6f - hpLeft);
		boolean caught = level.getRandom().nextFloat() < chance;
		double x = creature.getX(), y = creature.getY() + creature.getBbHeight() / 2, z = creature.getZ();
		if (caught) {
			Party.add(player, creature.toCaught());
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, x, y, z, 20, 0.4, 0.4, 0.4, 0.1);
			level.playSound(null, x, y, z, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, 1.4f);
			creature.discard();
			player.displayClientMessage(Component.literal("Caught " + name + " (Lv." + creature.getCreatureLevel() + ")!"), false);
		} else {
			level.sendParticles(ParticleTypes.SMOKE, x, y, z, 15, 0.3, 0.3, 0.3, 0.02);
			level.playSound(null, x, y, z, SoundEvents.ITEM_BREAK.value(), SoundSource.PLAYERS, 0.8f, 1.2f);
			player.displayClientMessage(Component.literal(name + " broke free! (" + Math.round(chance * 100) + "% chance)"), true);
		}
		return InteractionResult.SUCCESS;
	}
}
