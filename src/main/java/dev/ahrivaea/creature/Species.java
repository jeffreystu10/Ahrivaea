package dev.ahrivaea.creature;

import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.ahrivaea.Ahrivaea;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;

import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A kind of creature, loaded from data/<namespace>/creature/<id>.json in the mod or any data pack.
 * The file name is the creature's id; reloading data packs (/reload) picks up changes.
 */
public record Species(String name, List<CreatureType> types, BaseStats baseStats, float catchRate, float scale,
		Optional<Spawn> spawn, Optional<Evolution> evolution, String id) {

	public record BaseStats(int hp, int attack, int defense, int specialAttack, int specialDefense, int speed) {
		public static final Codec<BaseStats> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.INT.fieldOf("hp").forGetter(BaseStats::hp),
				Codec.INT.fieldOf("attack").forGetter(BaseStats::attack),
				Codec.INT.fieldOf("defense").forGetter(BaseStats::defense),
				Codec.INT.fieldOf("special_attack").forGetter(BaseStats::specialAttack),
				Codec.INT.fieldOf("special_defense").forGetter(BaseStats::specialDefense),
				Codec.INT.fieldOf("speed").forGetter(BaseStats::speed)
		).apply(i, BaseStats::new));
	}

	/** Where and how a creature appears in the wild. Biomes are ids ("minecraft:plains") or tags ("#minecraft:is_forest"). */
	public record Spawn(List<String> biomes, int minLevel, int maxLevel, int weight) {
		public static final Codec<Spawn> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.listOf().fieldOf("biomes").forGetter(Spawn::biomes),
				Codec.INT.optionalFieldOf("min_level", 2).forGetter(Spawn::minLevel),
				Codec.INT.optionalFieldOf("max_level", 10).forGetter(Spawn::maxLevel),
				Codec.INT.optionalFieldOf("weight", 10).forGetter(Spawn::weight)
		).apply(i, Spawn::new));

		public boolean matches(Holder<Biome> biome) {
			for (String b : biomes) {
				if (b.startsWith("#")) {
					if (biome.is(TagKey.create(Registries.BIOME, Identifier.parse(b.substring(1))))) return true;
				} else if (biome.is(Identifier.parse(b))) {
					return true;
				}
			}
			return false;
		}
	}

	public record Evolution(String into, int level) {
		public static final Codec<Evolution> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.fieldOf("into").forGetter(Evolution::into),
				Codec.INT.fieldOf("level").forGetter(Evolution::level)
		).apply(i, Evolution::new));
	}

	public static final Codec<Species> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("name").forGetter(Species::name),
			CreatureType.CODEC.listOf(1, 2).fieldOf("types").forGetter(Species::types),
			BaseStats.CODEC.fieldOf("base_stats").forGetter(Species::baseStats),
			Codec.FLOAT.optionalFieldOf("catch_rate", 0.45f).forGetter(Species::catchRate),
			Codec.FLOAT.optionalFieldOf("scale", 1f).forGetter(Species::scale),
			Spawn.CODEC.optionalFieldOf("spawn").forGetter(Species::spawn),
			Evolution.CODEC.optionalFieldOf("evolution").forGetter(Species::evolution),
			Codec.STRING.optionalFieldOf("id", "").forGetter(Species::id)
	).apply(i, Species::new));

	private static Map<String, Species> all = Map.of();

	public static void reload(ResourceManager manager) {
		Map<String, Species> loaded = new LinkedHashMap<>();
		manager.listResources("creature", path -> path.getPath().endsWith(".json")).forEach((path, resource) -> {
			String id = path.getPath().substring("creature/".length(), path.getPath().length() - ".json".length());
			try (Reader reader = resource.openAsReader()) {
				Species s = CODEC.parse(JsonOps.INSTANCE, JsonParser.parseReader(reader)).getOrThrow();
				loaded.put(id, new Species(s.name, s.types, s.baseStats, s.catchRate, s.scale, s.spawn, s.evolution, id));
			} catch (Exception e) {
				Ahrivaea.LOGGER.error("Could not load creature {}: {}", path, e.getMessage());
			}
		});
		all = Map.copyOf(loaded);
		Ahrivaea.LOGGER.info("Loaded {} creature species: {}", all.size(), String.join(", ", all.keySet()));
	}

	public static Optional<Species> get(String id) {
		return Optional.ofNullable(all.get(id));
	}

	public static List<Species> all() {
		return List.copyOf(all.values());
	}

	/** Picks a wild creature for a biome, weighted by each species' spawn weight; empty if none live there. */
	public static Optional<Species> pickFor(Holder<Biome> biome, RandomSource random) {
		List<Species> candidates = new ArrayList<>();
		int total = 0;
		for (Species s : all.values()) {
			if (s.spawn.isPresent() && s.spawn.get().matches(biome)) {
				candidates.add(s);
				total += s.spawn.get().weight();
			}
		}
		if (total <= 0) return Optional.empty();
		int roll = random.nextInt(total);
		for (Species s : candidates) {
			roll -= s.spawn.get().weight();
			if (roll < 0) return Optional.of(s);
		}
		return Optional.of(candidates.getLast());
	}

	public Identifier texture() {
		return Ahrivaea.id("textures/entity/creature/" + id + ".png");
	}

	public String typeLabel() {
		return String.join("/", types.stream().map(CreatureType::displayName).toList());
	}

	/** Max health at a level (follows the usual creature-game stat curve). */
	public float healthAt(int level) {
		return (2f * baseStats.hp * level) / 100f + level + 10f;
	}

	/** Any other stat at a level. */
	public static int statAt(int base, int level) {
		return (2 * base * level) / 100 + 5;
	}

	/** Walking speed in Minecraft units from the speed stat. */
	public float walkSpeed() {
		return 0.2f + baseStats.speed / 1000f;
	}
}
