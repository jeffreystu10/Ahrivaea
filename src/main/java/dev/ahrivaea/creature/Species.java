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

	/**
	 * One influence's version of the species. Each may have its own name and stats, and (for creatures from the
	 * creature list) its own rarity, where it spawns, what it drops when defeated and what work it does at a base.
	 */
	public record Form(Optional<String> name, Optional<Stats> stats, Optional<String> rarity, Optional<Spawn> spawn,
			List<Drop> drops, List<JobEntry> jobs, Optional<Float> size) {
		public static final Codec<Form> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.optionalFieldOf("name").forGetter(Form::name),
				Stats.CODEC.optionalFieldOf("stats").forGetter(Form::stats),
				Codec.STRING.optionalFieldOf("rarity").forGetter(Form::rarity),
				Spawn.CODEC.optionalFieldOf("spawn").forGetter(Form::spawn),
				Drop.CODEC.listOf().optionalFieldOf("drops", List.of()).forGetter(Form::drops),
				JobEntry.CODEC.listOf().optionalFieldOf("jobs", List.of()).forGetter(Form::jobs),
				Codec.FLOAT.optionalFieldOf("size").forGetter(Form::size)
		).apply(i, Form::new));

		public static final Form EMPTY = new Form(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), List.of(), List.of(), Optional.empty());
	}

	/** Something a defeated creature may drop: the item, the chance (0-1) and how many. "enchant" makes it a random enchanted book or item. */
	public record Drop(String item, float chance, int min, int max, boolean enchant) {
		public static final Codec<Drop> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.fieldOf("item").forGetter(Drop::item),
				Codec.floatRange(0, 1).fieldOf("chance").forGetter(Drop::chance),
				Codec.INT.optionalFieldOf("min", 1).forGetter(Drop::min),
				Codec.INT.optionalFieldOf("max", 1).forGetter(Drop::max),
				Codec.BOOL.optionalFieldOf("enchant", false).forGetter(Drop::enchant)
		).apply(i, Drop::new));
	}

	/** A job the creature can do at a base, with the creature list's wording of it. */
	public record JobEntry(Job job, String text) {
		public static final Codec<JobEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
				Job.CODEC.fieldOf("job").forGetter(JobEntry::job),
				Codec.STRING.optionalFieldOf("text", "").forGetter(JobEntry::text)
		).apply(i, JobEntry::new));
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
			Codec.unboundedMap(Influence.CODEC, Form.CODEC).optionalFieldOf("forms", Map.of(Influence.NONE, Form.EMPTY)).forGetter(Species::forms),
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
		return pickFor(biome, level, random, (s, inf) -> true);
	}

	/**
	 * A wild creature for this biome, weighted by spawn weight. Forms with their own spawn are chosen directly (with
	 * that influence); a species-wide spawn gets its influence from the biome and time of day ({@link InfluenceRules}).
	 */
	public static Optional<Pick> pickFor(Holder<Biome> biome, Level level, RandomSource random, java.util.function.BiPredicate<Species, Influence> allowed) {
		List<Pick> candidates = new ArrayList<>();
		List<Integer> weights = new ArrayList<>();
		int total = 0;
		for (Species s : all.values()) {
			for (var e : s.forms.entrySet()) {
				var sp = e.getValue().spawn();
				if (sp.isEmpty() || !allowed.test(s, e.getKey()) || !BiomeList.matches(sp.get().biomes(), biome)) continue;
				candidates.add(new Pick(s, e.getKey()));
				weights.add(sp.get().weight());
				total += sp.get().weight();
			}
			if (s.spawn.isEmpty() || !BiomeList.matches(s.spawn.get().biomes(), biome)) continue;
			var inf = InfluenceRules.pick(biome, level, s.forms.keySet(), random);
			if (inf.isEmpty() || !allowed.test(s, inf.get())) continue;
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

	/** A wild creature and its level. */
	public record LevelledPick(Species species, Influence influence, int level) {}

	/**
	 * A wild creature for this biome whose level range reaches [lo, hi] (others are left out), at a level in both.
	 * Empty if nothing living here is in that range.
	 */
	public static Optional<LevelledPick> pickForLevels(Holder<Biome> biome, Level level, RandomSource random, int lo, int hi,
			java.util.function.BiPredicate<Species, Influence> allowed) {
		var pick = pickFor(biome, level, random, (s, inf) -> {
			int[] r = s.levelRange(inf);
			return r[0] <= hi && r[1] >= lo && allowed.test(s, inf);
		});
		return pick.map(p -> {
			int[] r = p.species().levelRange(p.influence());
			int a = Math.max(r[0], lo), b = Math.min(r[1], hi);
			return new LevelledPick(p.species(), p.influence(), a + random.nextInt(Math.max(1, b - a + 1)));
		});
	}

	/** Wild creatures appear within this many levels of the nearest player's character level. */
	public static final int WILD_LEVEL_SPREAD = 5;

	public Form form(Influence influence) {
		return forms.getOrDefault(influence, Form.EMPTY);
	}

	/** Level range for a wild one of this form: the form's spawn, else the species', else 2-10. */
	public int[] levelRange(Influence influence) {
		var sp = form(influence).spawn().or(() -> spawn);
		return sp.map(x -> new int[] {x.minLevel(), x.maxLevel()}).orElse(new int[] {2, 10});
	}

	/** The jobs this form can do at a base. */
	public List<Job> jobsFor(Influence influence) {
		return form(influence).jobs().stream().map(JobEntry::job).distinct().toList();
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

	/** How big the form is (1 = the standard 0.7 x 0.8 block creature): its own size, from its model, or the species'. */
	public float sizeFor(Influence influence) {
		Form f = forms.get(influence);
		return f != null && f.size().isPresent() ? f.size().get() : size;
	}

	/** Walking speed in Minecraft units, from the Speed stat. */
	public float walkSpeed(Influence influence) {
		return 0.2f + statsFor(influence).speed() / 1000f;
	}
}
