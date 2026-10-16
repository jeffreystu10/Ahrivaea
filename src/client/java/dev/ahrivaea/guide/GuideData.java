package dev.ahrivaea.guide;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * What the guide shows, read from assets/ahrivaea_guide/guide.json (made by the build from mods.json and each
 * mod's recipe files). Read fresh each time the guide opens, after the world's tags have arrived, so ingredients
 * like "any log" can be shown.
 */
public final class GuideData {
	public record Mod(String key, String id, String name, String summary, List<String> guide, List<String> features,
			String commands, String added, String install, List<ItemStack> items, List<Recipe> recipes) {}

	public enum Station {
		CRAFTING("Crafting Table", Items.CRAFTING_TABLE), STONECUTTER("Stonecutter", Items.STONECUTTER),
		SMITHING("Smithing Table", Items.SMITHING_TABLE), OTHER("Other", Items.BARRIER);

		public final String title;
		public final Item icon;

		Station(String title, Item icon) {
			this.title = title;
			this.icon = icon;
		}
	}

	/**
	 * One recipe: its slots (9 for crafting, in a 3 by 3 grid; 1 for the stonecutter; template, base and addition for
	 * smithing), each a list of the items that can go there (empty for a blank slot), and what it makes.
	 */
	public record Recipe(String id, Mod mod, Station station, List<List<ItemStack>> slots, ItemStack result, String searchText) {
		public boolean makes(Item item) {
			return result.is(item);
		}

		public boolean uses(Item item) {
			for (List<ItemStack> s : slots) for (ItemStack st : s) if (st.is(item)) return true;
			return false;
		}
	}

	public final List<Mod> mods;
	/** Which mod adds each item (by the mod's name). */
	public final Map<Item, String> addedBy;

	private GuideData(List<Mod> mods, Map<Item, String> addedBy) {
		this.mods = mods;
		this.addedBy = addedBy;
	}

	public List<Recipe> allRecipes() {
		List<Recipe> out = new ArrayList<>();
		for (Mod m : mods) out.addAll(m.recipes());
		return out;
	}

	public static GuideData load() {
		List<Mod> mods = new ArrayList<>();
		Map<Item, String> addedBy = new HashMap<>();
		var resource = Minecraft.getInstance().getResourceManager().getResource(Identifier.fromNamespaceAndPath("ahrivaea_guide", "guide.json"));
		if (resource.isEmpty()) return new GuideData(mods, addedBy);
		try (Reader reader = resource.get().openAsReader()) {
			JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
			for (JsonElement e : root.getAsJsonArray("mods")) {
				JsonObject m = e.getAsJsonObject();
				List<ItemStack> items = new ArrayList<>();
				for (JsonElement i : m.getAsJsonArray("items")) {
					ItemStack st = stack(i.getAsString(), 1);
					items.add(st);
					if (!st.is(Items.BARRIER)) addedBy.put(st.getItem(), m.get("name").getAsString());
				}
				List<Recipe> recipes = new ArrayList<>();
				Mod mod = new Mod(m.get("key").getAsString(), m.get("id").getAsString(), m.get("name").getAsString(), m.get("summary").getAsString(),
						strings(m.getAsJsonArray("guide")), strings(m.getAsJsonArray("features")), m.get("commands").getAsString(),
						m.get("added").getAsString(), m.get("install").getAsString(), items, recipes);
				for (JsonElement r : m.getAsJsonArray("recipes")) {
					try {
						Recipe recipe = recipe(mod, r.getAsJsonObject().get("id").getAsString(), r.getAsJsonObject().getAsJsonObject("recipe"));
						if (recipe != null) recipes.add(recipe);
					} catch (RuntimeException ex) {
						GuideClient.LOGGER.warn("Guide: couldn't read recipe {}: {}", r, ex.toString());
					}
				}
				// results of a mod's recipes are "added by" it too unless they're vanilla items
				for (Recipe r : recipes) {
					Identifier rid = BuiltInRegistries.ITEM.getKey(r.result().getItem());
					if (!rid.getNamespace().equals("minecraft")) addedBy.putIfAbsent(r.result().getItem(), mod.name());
				}
				mods.add(mod);
			}
		} catch (Exception ex) {
			GuideClient.LOGGER.error("Guide: couldn't read guide.json: {}", ex.toString());
		}
		return new GuideData(mods, addedBy);
	}

	private static List<String> strings(JsonArray a) {
		List<String> out = new ArrayList<>();
		if (a != null) for (JsonElement e : a) out.add(e.getAsString());
		return out;
	}

	private static Recipe recipe(Mod mod, String id, JsonObject r) {
		String type = r.get("type").getAsString();
		List<List<ItemStack>> slots = new ArrayList<>();
		Station station;
		switch (type) {
			case "minecraft:crafting_shaped" -> {
				station = Station.CRAFTING;
				for (int i = 0; i < 9; i++) slots.add(List.of());
				JsonArray pattern = r.getAsJsonArray("pattern");
				JsonObject key = r.getAsJsonObject("key");
				for (int row = 0; row < pattern.size() && row < 3; row++) {
					String line = pattern.get(row).getAsString();
					for (int col = 0; col < line.length() && col < 3; col++) {
						String c = String.valueOf(line.charAt(col));
						if (key.has(c)) slots.set(row * 3 + col, ingredient(key.get(c)));
					}
				}
			}
			case "minecraft:crafting_shapeless" -> {
				station = Station.CRAFTING;
				for (JsonElement e : r.getAsJsonArray("ingredients")) slots.add(ingredient(e));
				while (slots.size() < 9) slots.add(List.of());
			}
			case "minecraft:stonecutting" -> {
				station = Station.STONECUTTER;
				slots.add(ingredient(r.get("ingredient")));
			}
			case "minecraft:smithing_transform" -> {
				station = Station.SMITHING;
				slots.add(r.has("template") ? ingredient(r.get("template")) : List.of());
				slots.add(r.has("base") ? ingredient(r.get("base")) : List.of());
				slots.add(r.has("addition") ? ingredient(r.get("addition")) : List.of());
			}
			default -> {
				return null;
			}
		}
		ItemStack result = result(r.get("result"));
		StringBuilder text = new StringBuilder(result.getHoverName().getString()).append(' ').append(station.title).append(' ').append(mod.name());
		for (List<ItemStack> s : slots) if (!s.isEmpty()) text.append(' ').append(s.get(0).getHoverName().getString());
		return new Recipe(id, mod, station, slots, result, text.toString().toLowerCase(Locale.ROOT));
	}

	private static ItemStack result(JsonElement e) {
		if (e.isJsonPrimitive()) return stack(e.getAsString(), 1);
		JsonObject o = e.getAsJsonObject();
		String id = o.has("id") ? o.get("id").getAsString() : o.get("item").getAsString();
		return stack(id, o.has("count") ? o.get("count").getAsInt() : 1);
	}

	/** An ingredient: "minecraft:stick", "#minecraft:logs", a list of those, or the older {"item": ...}/{"tag": ...}. */
	private static List<ItemStack> ingredient(JsonElement e) {
		List<ItemStack> out = new ArrayList<>();
		if (e == null || e.isJsonNull()) return out;
		if (e.isJsonArray()) {
			for (JsonElement x : e.getAsJsonArray()) out.addAll(ingredient(x));
			return dedupe(out);
		}
		String s;
		if (e.isJsonObject()) {
			JsonObject o = e.getAsJsonObject();
			s = o.has("tag") ? "#" + o.get("tag").getAsString() : o.has("item") ? o.get("item").getAsString() : null;
			if (s == null) return out;
		} else s = e.getAsString();
		if (s.startsWith("#")) {
			TagKey<Item> tag = TagKey.create(Registries.ITEM, Identifier.parse(s.substring(1)));
			for (Holder<Item> h : BuiltInRegistries.ITEM.getTagOrEmpty(tag)) out.add(new ItemStack(h));
			if (out.isEmpty()) out.add(missing(s));
		} else out.add(stack(s, 1));
		return out;
	}

	private static List<ItemStack> dedupe(List<ItemStack> list) {
		Map<Item, ItemStack> seen = new LinkedHashMap<>();
		for (ItemStack s : list) seen.putIfAbsent(s.getItem(), s);
		return new ArrayList<>(seen.values());
	}

	private static ItemStack stack(String id, int count) {
		Identifier rid = Identifier.tryParse(id);
		var item = rid == null ? java.util.Optional.<Item>empty() : BuiltInRegistries.ITEM.getOptional(rid);
		if (item.isEmpty() || item.get() == Items.AIR) return missing(id);
		return new ItemStack(item.get(), count);
	}

	/** Something the game doesn't have (an item from a mod that isn't installed here). */
	private static ItemStack missing(String id) {
		ItemStack st = new ItemStack(Items.BARRIER);
		st.set(DataComponents.CUSTOM_NAME, Component.literal(id + " (not installed here)"));
		return st;
	}
}
