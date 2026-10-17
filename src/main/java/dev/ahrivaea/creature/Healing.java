package dev.ahrivaea.creature;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;

/**
 * Healing your creatures. Outside battle they eat food, like you do: the more filling the food, the more it heals.
 * In battle only brewed potions heal: Healing, Regeneration and their stronger versions; splash and lingering ones
 * heal everyone of yours next to the target too.
 */
public final class Healing {
	private Healing() {}

	/**
	 * Share of a creature's max HP a food heals (percent): 2.5 per hunger point it fills and 1.25 per point of
	 * saturation, between 3% and 60%. A steak heals 36%, bread 20%, an apple 13%, a cookie 6%. 0 if it isn't food.
	 */
	public static int foodPercent(ItemStack stack) {
		var food = stack.get(DataComponents.FOOD);
		if (food == null) return 0;
		return (int) Math.clamp(Math.round(2.5 * food.nutrition() + 1.25 * food.saturation()), 3, 60);
	}

	/** What's left in your hand after feeding it (a bowl after stew, a bottle after honey), or empty. */
	public static ItemStack remainder(ItemStack stack) {
		var r = stack.get(DataComponents.USE_REMAINDER);
		return r == null ? ItemStack.EMPTY : r.convertInto().create();
	}

	/** A potion that heals in battle: how much (percent of max HP), whether it splashes, and what to call it. */
	public record PotionHeal(int percent, boolean splash, String name) {}

	/** The healing a potion gives in battle, or null if it isn't a healing potion. */
	public static PotionHeal potion(ItemStack stack) {
		boolean splash = stack.is(Items.SPLASH_POTION) || stack.is(Items.LINGERING_POTION);
		if (!stack.is(Items.POTION) && !splash) return null;
		PotionContents c = stack.get(DataComponents.POTION_CONTENTS);
		if (c == null) return null;
		int pct;
		String name;
		if (c.is(Potions.STRONG_HEALING)) { pct = 70; name = "a Potion of Healing II"; }
		else if (c.is(Potions.HEALING)) { pct = 40; name = "a Potion of Healing"; }
		else if (c.is(Potions.STRONG_REGENERATION)) { pct = 50; name = "a Potion of Regeneration II"; }
		else if (c.is(Potions.LONG_REGENERATION)) { pct = 35; name = "a long Potion of Regeneration"; }
		else if (c.is(Potions.REGENERATION)) { pct = 30; name = "a Potion of Regeneration"; }
		else return null;
		if (splash) name = name.replace("a Potion", stack.is(Items.LINGERING_POTION) ? "a lingering Potion" : "a splash Potion");
		return new PotionHeal(pct, splash, name);
	}

	/** The first healing potion in the player's inventory (hotbar first), or empty. */
	public static ItemStack firstPotion(Player p) {
		var inv = p.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (potion(s) != null) return s;
		}
		return ItemStack.EMPTY;
	}

	/** Gives back what a used item leaves (a bottle, a bowl), dropping it if the inventory is full. */
	public static void giveBack(Player p, ItemStack left) {
		if (left.isEmpty() || p.getAbilities().instabuild) return;
		p.getInventory().placeItemBackInInventory(left, net.minecraft.util.Prediction.SERVER_ONLY);
	}

	/** Feeds the food in the player's main hand to a party creature (not during a battle). Returns what to tell them. */
	public static String feedParty(net.minecraft.server.level.ServerPlayer p, int slot) {
		if (dev.ahrivaea.battle.Battles.of(p) != null) return "Food doesn't work in battle: use a healing potion.";
		var party = dev.ahrivaea.party.Party.get(p);
		if (slot < 0 || slot >= party.size()) return "There's no creature in that slot.";
		ItemStack food = p.getMainHandItem();
		int pct = foodPercent(food);
		if (pct <= 0) return "Hold some food in your main hand to feed it.";
		var c = party.get(slot);
		var species = Species.get(c.species());
		if (species.isEmpty()) return "That creature's species no longer exists.";
		int max = species.get().statsFor(c.influence()).maxHealth(c.level());
		String name = species.get().nameFor(c.influence());
		if (c.health() >= max) return name + " is already at full health.";
		float healed = Math.min(max, c.health() + Math.max(1, Math.round(max * pct / 100f)));
		dev.ahrivaea.party.Party.set(p, slot, c.withHealth(healed));
		ItemStack left = remainder(food);
		String what = food.getHoverName().getString();
		food.consume(1, p);
		giveBack(p, left);
		return name + " ate the " + what + ": +" + Math.round(healed - c.health()) + " HP (" + Math.round(healed) + "/" + max + ")";
	}
}
