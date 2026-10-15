package dev.ahrivaea.item;

import dev.ahrivaea.Ahrivaea;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;

import java.util.function.UnaryOperator;

/** Creature drops that Minecraft doesn't have (from the creature list's "mod item" drops). */
public final class DropItems {
	private DropItems() {}

	public static final Item BANANA = item("banana", p -> p.food(new FoodProperties.Builder().nutrition(4).saturationModifier(0.3f).build()));
	public static final Item RAW_RAT = item("raw_rat", p -> p.food(new FoodProperties.Builder().nutrition(2).saturationModifier(0.1f).build()));
	public static final Item IVORY = item("ivory", p -> p);
	public static final Item PURPUR_SHARD = item("purpur_shard", p -> p);

	private static Item item(String name, UnaryOperator<Item.Properties> props) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Ahrivaea.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, new Item(props.apply(new Item.Properties().setId(key))));
	}

	public static void init() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FOOD_AND_DRINKS).register(out -> {
			out.accept(BANANA);
			out.accept(RAW_RAT);
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(out -> {
			out.accept(IVORY);
			out.accept(PURPUR_SHARD);
		});
	}
}
