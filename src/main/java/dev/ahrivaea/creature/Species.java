package dev.ahrivaea.creature;

import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.ahrivaea.Ahrivaea;
import net.minecraft.core.Holder;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A kind of creature, loaded from data/<namespace>/creature/<id>.json in the mod or any data pack.
 * The file name is the creature's id; /reload picks up changes. See docs/creatures.md.
 *
 * @param forms one entry per influence the species can have; each may rename it and override its stats.
 *              Its model and texture are found by convention: geo/creature/<id>/<influence>.geo.json and
 *              textures/entity/creature/<id>/<influence>.png in a resource pack.
 */
public record Species(String name, Stats stats, Map<Influence, Form> forms, float size, Movement movement,
		Optional<Spawn> spawn, String id) {

	public enum Movement implements net.minecraft.util.StringRepresentable {
		WALK, FLY, SWIM;
		public static final Codec<Movement> CODEC = net.minecraft.util.StringRepresentable.fromEnum(Movement::values);

		@Override
		public String getSerializedName() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	public record Form(Optional<String> name, Optional<Stats> stats) {
		public static final Codec<Form> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.optionalFieldOf("name").forGetter(Form::name),
				Stats.CODEC.optionalFieldOf("stats").forGetter(Form::stats)
		).apply(i, Form::new));
	}

	/** Where a creature appears in the wild. Biomes are ids ("minecraft:plains") or tags ("#minecraft:is_forest"). */
	public record Spawn(List<String> biomes, int minLevel, int maxLevel, int weight) {
		public static final Codec<Spawn> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.listOf().fieldOf("biomes").forGetter(Spawn::biomes),
				Codec.intRange(0, Stats.MAX_LEVEL).optionalFieldOf("min_level", 2).forGetter(Spawn::minLevel),
				Codec.intRange(0, Stats.MAX_LEVEL).optionalFieldOf("max_level", 10).forGetter(Spawn::maxLevel),
				Codec.INT.optionalFieldOf("weight", 10).forGetter(Spawn::weight)
		).apply(i, Spawn::new));
	}

	public static final Codec<Species> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("name").forGetter(Species::name),
			Stats.CODEC.fieldOf("stats").forGetter(Species::stats),
			Codec.unboundedMap(Influence.CODEC, Form.CODEC).optionalFieldOf("forms", Map.of(Influence.NONE, new Form(Optional.empty(), Optional.empty()))).forGetter(Species::forms),
			Codec.FLOAT.optionalFieldOf("size", 1f).forGetter(Species::size),
			Movement.CODEC.optionalFieldOf("movement", Movement.WALK).forGetter(Species::movement),
			Spawn.CODEC.optionalFieldOf("spawn").forGetter(Species::spawn),
			Codec.STRING.optionalFieldOf("id", "").forGetter(Species::id)
	).apply(i, Species::new));

	private static Map<String, Species> all = Map.of();

	public static void reload(ResourceManager manager) {
		Map<String, Species> loaded = new LinkedHashMap<>();
		manager.listResources("creature", path -> path.getPath().endsWith(".json")).forEach((path, resource) -> {
			String id = path.getPath().substring("creature/".length(), path.getPath().length() - ".json".length());
			try (Reader reader = resource.openAsReader()) {
				Species s = CODEC.parse(JsonOps.INSTANCE, JsonParser.parseReader(reader)).getOrThrow();
				s = new Species(s.name, s.stats, s.forms, s.size, s.movement, s.spawn, id);
				for (Influence inf : s.forms.keySet()) {
					int total = s.statsFor(inf).total();
					if (total < Stats.MIN_TOTAL || total > Stats.MAX_TOTAL) {
						Ahrivaea.LOGGER.warn("{} ({}) has a stat total of {}; it should be {}–{}", s.nameFor(inf), id, total, Stats.MIN_TOTAL, Stats.MAX_TOTAL);
					}
				}
				loaded.put(id, s);
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

	/** A wild creature for this biome, weighted by spawn weight, with the influence it gets here and now. */
	public record Pick(Species species, Influence influence) {}

	public static Optional<Pick> pickFor(Holder<Biome> biome, Level level, RandomSource random) {
		List<Pick> candidates = new ArrayList<>();
		List<Integer> weights = new ArrayList<>();
		int total = 0;
		for (Species s : all.values()) {
			if (s.spawn.isEmpty() || !BiomeList.matches(s.spawn.get().biomes(), biome)) continue;
			var inf = InfluenceRules.pick(biome, level, s.forms.keySet(), random);
			if (inf.isEmpty()) continue;
			candidates.add(new Pick(s, inf.get()));
			weights.add(s.spawn.get().weight());
			total += s.spawn.get().weight();
		}
		if (total <= 0) return Optional.empty();
		int roll = random.nextInt(total);
		for (int k = 0; k < candidates.size(); k++) {
			roll -= weights.get(k);
			if (roll < 0) return Optional.of(candidates.get(k));
		}
		return Optional.of(candidates.getLast());
	}

	/** The form's own stats, or the species' if the form doesn't override them. */
	public Stats statsFor(Influence influence) {
		Form f = forms.get(influence);
		return f != null && f.stats().isPresent() ? f.stats().get() : stats;
	}

	/** "Ashen Dragon" if the form has its own name, otherwise "Fire Dragon" (or just "Dragon" for None). */
	public String nameFor(Influence influence) {
		Form f = forms.get(influence);
		if (f != null && f.name().isPresent()) return f.name().get();
		return influence == Influence.NONE ? name : influence.displayName() + " " + name;
	}

	/** The form to use for an influence: that one if the species has it, else None, else any. */
	public Influence formOrDefault(Influence influence) {
		if (forms.containsKey(influence)) return influence;
		if (forms.containsKey(Influence.NONE)) return Influence.NONE;
		return forms.keySet().iterator().next();
	}

	/** Walking speed in Minecraft units, from the Speed stat. */
	public float walkSpeed(Influence influence) {
		return 0.2f + statsFor(influence).speed() / 1000f;
	}
}
