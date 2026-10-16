package dev.ahrivaea.creature;

import dev.ahrivaea.Ahrivaea;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;

/**
 * Riding creatures: the Swift Horses rules for every creature of medium size or bigger. Put a saddle on your own
 * creature, right-click to ride; flying creatures fly and swimming ones swim. Treats raise its speed tier 0-3 like
 * horse feed; tiers 4 and 5 come from its Speed stat instead of breeding (creatures don't breed).
 */
public final class Mounts {
	private Mounts() {}

	/** Medium and bigger: everything human-sized and up (the smallest is about 2; small creatures are under 1.6). */
	public static final float MIN_SIZE = 1.9f;
	/** Speed multiplier for each tier, the same as Swift Horses. */
	public static final double[] MULTIPLIER = {1.5, 1.76, 2.02, 2.28, 2.54, 2.8};
	public static final int MAX_TIER = MULTIPLIER.length - 1;
	/** Treats raise a creature up to this tier. */
	public static final int MAX_FED_TIER = 3;
	/** A creature fed to tier 3 is tier 4 if its Speed (at its level) is at least this, and tier 5 at the next. */
	public static final int TIER_4_SPEED = 60, TIER_5_SPEED = 80;
	/** Movement speed (attribute units) to blocks per second for a ridden mount on the ground. */
	public static final double BLOCKS_PER_SECOND = 42.16;

	public static final Item SWIFT_TREAT = item("swift_treat", Rarity.COMMON);
	public static final Item GOLDEN_TREAT = item("golden_treat", Rarity.UNCOMMON);
	public static final Item RACING_TREAT = item("racing_treat", Rarity.RARE);

	private static Item item(String name, Rarity rarity) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Ahrivaea.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key).rarity(rarity)));
	}

	public static void init() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FOOD_AND_DRINKS).register(out -> {
			out.accept(SWIFT_TREAT);
			out.accept(GOLDEN_TREAT);
			out.accept(RACING_TREAT);
		});
	}

	public record Step(Item treat, int amount) {}

	/** What it takes to go from tier n to n + 1 with treats (null above tier 2). */
	public static Step stepFrom(int tier) {
		return switch (tier) {
			case 0 -> new Step(SWIFT_TREAT, 8);
			case 1 -> new Step(GOLDEN_TREAT, 8);
			case 2 -> new Step(RACING_TREAT, 4);
			default -> null;
		};
	}

	public static boolean isTreat(ItemStack s) {
		return s.is(SWIFT_TREAT) || s.is(GOLDEN_TREAT) || s.is(RACING_TREAT);
	}

	public static boolean rideable(float size) {
		return size >= MIN_SIZE;
	}

	/** The tier it rides at: its fed tier, or 4 or 5 once fed to 3 if it's fast enough. */
	public static int tier(int fedTier, double speed) {
		if (fedTier < MAX_FED_TIER) return Math.max(0, fedTier);
		return speed >= TIER_5_SPEED ? 5 : speed >= TIER_4_SPEED ? 4 : MAX_FED_TIER;
	}

	/** Top speed in blocks per second: from its Speed stat (at its level), times its tier's multiplier. */
	public static double blocksPerSecond(double speed, int tier) {
		return (5 + 0.09 * speed) * MULTIPLIER[Math.clamp(tier, 0, MAX_TIER)];
	}
}
