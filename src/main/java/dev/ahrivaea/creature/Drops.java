package dev.ahrivaea.creature;

import dev.ahrivaea.Ahrivaea;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Rolls a creature form's drops (from its creature file) into item stacks. */
public final class Drops {
	private Drops() {}

	public static List<ItemStack> roll(Species species, Influence influence, int level, RandomSource random, RegistryAccess registries) {
		List<ItemStack> out = new ArrayList<>();
		for (Species.Drop d : species.form(influence).drops()) {
			if (random.nextFloat() >= d.chance()) continue;
			var item = BuiltInRegistries.ITEM.getOptional(Identifier.parse(d.item()));
			if (item.isEmpty() || item.get() == Items.AIR) {
				Ahrivaea.LOGGER.warn("{} drops unknown item {}", species.nameFor(influence), d.item());
				continue;
			}
			int count = d.min() + (d.max() > d.min() ? random.nextInt(d.max() - d.min() + 1) : 0);
			ItemStack stack = new ItemStack(item.get(), Math.max(1, count));
			// enchanted books and gear: enchanted as if at an enchanting table, stronger for higher-level creatures
			if (d.enchant()) stack = EnchantmentHelper.enchantItem(random, stack, 5 + level / 2, registries, Optional.empty());
			out.add(stack);
		}
		return out;
	}

	/** "3 Flint" */
	public static String describe(ItemStack s) {
		return (s.getCount() > 1 ? s.getCount() + " " : "") + s.getHoverName().getString();
	}
}
