package dev.ahrivaea.creature;

import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.ahrivaea.Ahrivaea;
import net.minecraft.core.Holder;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Which influence a creature gets from where and when it spawns. Loaded from every
 * data/<namespace>/influence_rules/*.json (each a list of rules). Several rules can match; one is
 * picked by weight among those the species has a form for. If none match, the creature is None.
 */
public final class InfluenceRules {
	private InfluenceRules() {}

	public enum Time implements StringRepresentable {
		ANY, DAY, NIGHT, DAWN, DUSK;
		public static final Codec<Time> CODEC = StringRepresentable.fromEnum(Time::values);

		@Override
		public String getSerializedName() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}

		/** Overworld clock: 0 is sunrise; a day is 24000 ticks. */
		public boolean matches(long clock) {
			long t = Math.floorMod(clock, 24000L);
			return switch (this) {
				case ANY -> true;
				case DAWN -> t >= 22500 || t < 1000;
				case DAY -> t >= 1000 && t < 12000;
				case DUSK -> t >= 12000 && t < 13500;
				case NIGHT -> t >= 13500 && t < 22500;
			};
		}
	}

	public record Rule(List<String> biomes, Time time, Influence influence, int weight) {
		public static final Codec<Rule> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.listOf().fieldOf("biomes").forGetter(Rule::biomes),
				Time.CODEC.optionalFieldOf("time", Time.ANY).forGetter(Rule::time),
				Influence.CODEC.fieldOf("influence").forGetter(Rule::influence),
				Codec.INT.optionalFieldOf("weight", 1).forGetter(Rule::weight)
		).apply(i, Rule::new));
	}

	private static List<Rule> rules = List.of();

	public static void reload(ResourceManager manager) {
		List<Rule> loaded = new ArrayList<>();
		manager.listResources("influence_rules", path -> path.getPath().endsWith(".json")).forEach((path, resource) -> {
			try (Reader reader = resource.openAsReader()) {
				loaded.addAll(Rule.CODEC.listOf().parse(JsonOps.INSTANCE, JsonParser.parseReader(reader)).getOrThrow());
			} catch (Exception e) {
				Ahrivaea.LOGGER.error("Could not load influence rules {}: {}", path, e.getMessage());
			}
		});
		rules = List.copyOf(loaded);
		Ahrivaea.LOGGER.info("Loaded {} influence rules", rules.size());
	}

	/** The influence for a spawn here and now, limited to the forms the species has. Empty if it has none that fit. */
	public static Optional<Influence> pick(Holder<Biome> biome, Level level, Set<Influence> available, RandomSource random) {
		long clock = level.getOverworldClockTime();
		List<Rule> matching = new ArrayList<>();
		int total = 0;
		for (Rule r : rules) {
			if (available.contains(r.influence) && r.time.matches(clock) && BiomeList.matches(r.biomes, biome)) {
				matching.add(r);
				total += Math.max(1, r.weight);
			}
		}
		if (total > 0) {
			int roll = random.nextInt(total);
			for (Rule r : matching) {
				roll -= Math.max(1, r.weight);
				if (roll < 0) return Optional.of(r.influence);
			}
		}
		return available.contains(Influence.NONE) ? Optional.of(Influence.NONE) : Optional.empty();
	}
}
