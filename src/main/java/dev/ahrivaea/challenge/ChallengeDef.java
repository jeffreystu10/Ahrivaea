package dev.ahrivaea.challenge;

import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.Reader;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A server building challenge, loaded from data/<namespace>/challenge/<id>.json. Players put items into
 * the offering chest; every {@code goal / stages} worth rebuilds the next slice of the finished structure,
 * from the bottom up. See docs/challenges.md.
 */
public record ChallengeDef(String name, String unit, Map<String, Integer> items, int goal, int stages,
		Identifier ruined, Identifier finished, BlockPos chest, List<Reward> rewards, String id) {

	/** Items for the donor at this rank (1 = top); rank 0 means everyone who gave anything. */
	public record Reward(int rank, List<RewardItem> items) {
		public static final Codec<Reward> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.INT.fieldOf("rank").forGetter(Reward::rank),
				RewardItem.CODEC.listOf().fieldOf("items").forGetter(Reward::items)
		).apply(i, Reward::new));
	}

	public record RewardItem(Identifier item, int count) {
		public static final Codec<RewardItem> CODEC = RecordCodecBuilder.create(i -> i.group(
				Identifier.CODEC.fieldOf("item").forGetter(RewardItem::item),
				Codec.INT.optionalFieldOf("count", 1).forGetter(RewardItem::count)
		).apply(i, RewardItem::new));
	}

	public static final Codec<ChallengeDef> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("name").forGetter(ChallengeDef::name),
			Codec.STRING.optionalFieldOf("unit", "items").forGetter(ChallengeDef::unit),
			Codec.unboundedMap(Codec.STRING, Codec.INT).fieldOf("items").forGetter(ChallengeDef::items),
			Codec.INT.fieldOf("goal").forGetter(ChallengeDef::goal),
			Codec.intRange(1, 64).optionalFieldOf("stages", 4).forGetter(ChallengeDef::stages),
			Identifier.CODEC.fieldOf("ruined").forGetter(ChallengeDef::ruined),
			Identifier.CODEC.fieldOf("finished").forGetter(ChallengeDef::finished),
			BlockPos.CODEC.fieldOf("chest").forGetter(ChallengeDef::chest),
			Reward.CODEC.listOf().optionalFieldOf("rewards", List.of()).forGetter(ChallengeDef::rewards),
			Codec.STRING.optionalFieldOf("id", "").forGetter(ChallengeDef::id)
	).apply(i, ChallengeDef::new));

	private static Map<String, ChallengeDef> all = Map.of();

	public static void reload(ResourceManager manager) {
		Map<String, ChallengeDef> loaded = new LinkedHashMap<>();
		manager.listResources("challenge", path -> path.getPath().endsWith(".json")).forEach((path, resource) -> {
			String id = path.getPath().substring("challenge/".length(), path.getPath().length() - ".json".length());
			try (Reader reader = resource.openAsReader()) {
				ChallengeDef d = CODEC.parse(JsonOps.INSTANCE, JsonParser.parseReader(reader)).getOrThrow();
				for (String item : d.items.keySet()) {
					if (!item.startsWith("#") && !net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(Identifier.parse(item))) {
						TowersMod.LOGGER.warn("Challenge {}: unknown item {}", id, item);
					}
				}
				for (Reward r : d.rewards) for (RewardItem it : r.items()) {
					if (!net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(it.item())) TowersMod.LOGGER.warn("Challenge {}: unknown reward item {}", id, it.item());
				}
				loaded.put(id, new ChallengeDef(d.name, d.unit, d.items, d.goal, d.stages, d.ruined, d.finished, d.chest, d.rewards, id));
			} catch (Exception e) {
				TowersMod.LOGGER.error("Could not load challenge {}: {}", path, e.getMessage());
			}
		});
		all = Map.copyOf(loaded);
		List<String> newOrder = new java.util.ArrayList<>();
		manager.getResourceStack(TowersMod.id("challenge_order.json")).forEach(resource -> {
			try (Reader reader = resource.openAsReader()) {
				var json = JsonParser.parseReader(reader).getAsJsonObject();
				newOrder.clear();
				json.getAsJsonArray("towers").forEach(e -> newOrder.add(e.getAsString()));
			} catch (Exception e) {
				TowersMod.LOGGER.error("Could not load challenge_order.json: {}", e.getMessage());
			}
		});
		for (String id : newOrder) if (!all.containsKey(id)) TowersMod.LOGGER.warn("challenge_order.json lists {}, which has no challenge file", id);
		order = List.copyOf(newOrder);
		TowersMod.LOGGER.info("Loaded {} challenges: {}; tower order: {}", all.size(), String.join(", ", all.keySet()), String.join(", ", order));
	}

	public static Optional<ChallengeDef> get(String id) {
		return Optional.ofNullable(all.get(id));
	}

	public static Map<String, ChallengeDef> all() {
		return all;
	}

	/** How much one of this item counts for (0 if it isn't accepted). Keys are item ids or #item tags. */
	public int worth(net.minecraft.world.item.ItemStack stack) {
		Integer direct = items.get(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
		if (direct != null) return direct;
		for (var e : items.entrySet()) {
			if (e.getKey().startsWith("#") && stack.is(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM, Identifier.parse(e.getKey().substring(1))))) {
				return e.getValue();
			}
		}
		return 0;
	}

	private static List<String> order = List.of();

	/**
	 * Which challenge each stronghold tower gets, from data/<namespace>/challenge_order.json
	 * ({"towers": ["coal", "iron", …]}). Tower n (counting from the nearest ring of strongholds outward)
	 * gets entry n, starting over when the list runs out.
	 */
	public static Optional<ChallengeDef> forTower(int index) {
		if (!order.isEmpty()) {
			var d = get(order.get(index % order.size()));
			if (d.isPresent()) return d;
		}
		if (all.isEmpty()) return Optional.empty();
		var list = List.copyOf(all.values());
		return Optional.of(list.get(index % list.size()));
	}

	public static List<String> order() {
		return order;
	}

	/** Progress needed to complete stage {@code k} (1..stages). */
	public int threshold(int k) {
		return (int) Math.ceil(goal * (double) k / stages);
	}
}
