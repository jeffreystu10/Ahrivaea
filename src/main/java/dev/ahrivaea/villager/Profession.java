package dev.ahrivaea.villager;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.ahrivaea.Ahrivaea;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.Item;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A villager profession (data/ahrivaea/villager_profession/*.json, made by tools/villager_trades.py from
 * Villager_Trades.xlsx): its ranks, Novice to Master, each with the deed that earns it and the items it then sells.
 */
public record Profession(String id, String name, String description, String outfit, Item icon, int order, List<Rank> ranks) {
	public record Rank(String name, Deed deed, List<Offer> sells) {}

	/** count items for cost XP (from the creature XP pool); a particular potion or enchanted book if those are set. */
	public record Offer(Item item, int count, int cost, String potion, String enchantment, int level) {
		/** What the player gets. */
		public net.minecraft.world.item.ItemStack stack(net.minecraft.core.HolderLookup.Provider registries) {
			var stack = new net.minecraft.world.item.ItemStack(item, count);
			if (!potion.isEmpty()) {
				BuiltInRegistries.POTION.get(Identifier.parse(potion)).ifPresent(p ->
						stack.set(net.minecraft.core.component.DataComponents.POTION_CONTENTS, new net.minecraft.world.item.alchemy.PotionContents(p)));
			}
			if (!enchantment.isEmpty()) {
				var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ENCHANTMENT, Identifier.parse(enchantment));
				registries.lookup(net.minecraft.core.registries.Registries.ENCHANTMENT).flatMap(l -> l.get(key)).ifPresent(e -> {
					var book = new net.minecraft.world.item.enchantment.ItemEnchantments.Mutable(net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY);
					book.set(e, level);
					stack.set(net.minecraft.core.component.DataComponents.STORED_ENCHANTMENTS, book.toImmutable());
				});
			}
			return stack;
		}
	}

	private static Map<String, Profession> all = Map.of();

	public static Map<String, Profession> all() {
		return all;
	}

	public static List<Profession> sorted() {
		List<Profession> out = new ArrayList<>(all.values());
		out.sort(Comparator.comparingInt(Profession::order).thenComparing(Profession::name));
		return out;
	}

	public static Profession get(String id) {
		return all.get(id);
	}

	public static void reload(ResourceManager manager) {
		Map<String, Profession> loaded = new LinkedHashMap<>();
		int offers = 0;
		List<String> unknown = new ArrayList<>();
		for (var e : manager.listResources("villager_profession", p -> p.getPath().endsWith(".json")).entrySet()) {
			String id = e.getKey().getPath().substring("villager_profession/".length(), e.getKey().getPath().length() - ".json".length());
			try (Reader reader = e.getValue().openAsReader()) {
				JsonObject o = JsonParser.parseReader(reader).getAsJsonObject();
				List<Rank> ranks = new ArrayList<>();
				for (JsonElement re : o.getAsJsonArray("ranks")) {
					JsonObject r = re.getAsJsonObject();
					List<Offer> sells = new ArrayList<>();
					for (JsonElement se : r.getAsJsonArray("sells")) {
						JsonObject s = se.getAsJsonObject();
						var item = BuiltInRegistries.ITEM.getOptional(Identifier.parse(s.get("item").getAsString()));
						if (item.isEmpty()) {
							unknown.add(s.get("item").getAsString());
							continue;
						}
						sells.add(new Offer(item.get(), s.has("count") ? s.get("count").getAsInt() : 1, s.get("cost").getAsInt(),
								s.has("potion") ? s.get("potion").getAsString() : "", s.has("enchantment") ? s.get("enchantment").getAsString() : "",
								s.has("level") ? s.get("level").getAsInt() : 1));
					}
					offers += sells.size();
					ranks.add(new Rank(r.get("name").getAsString(), Deed.parse(r.getAsJsonObject("deed")), List.copyOf(sells)));
				}
				Item icon = BuiltInRegistries.ITEM.getOptional(Identifier.parse(o.has("icon") ? o.get("icon").getAsString() : "minecraft:emerald"))
						.orElse(net.minecraft.world.item.Items.EMERALD);
				loaded.put(id, new Profession(id, o.get("name").getAsString(), o.has("description") ? o.get("description").getAsString() : "",
						o.has("outfit") ? o.get("outfit").getAsString() : "", icon, o.has("order") ? o.get("order").getAsInt() : 99, List.copyOf(ranks)));
			} catch (Exception ex) {
				Ahrivaea.LOGGER.error("Could not load villager profession {}: {}", id, ex.toString());
			}
		}
		all = Map.copyOf(loaded);
		Ahrivaea.LOGGER.info("Loaded {} villager professions selling {} items{}", all.size(), offers,
				unknown.isEmpty() ? "" : "; unknown items skipped: " + String.join(", ", unknown));
	}
}
