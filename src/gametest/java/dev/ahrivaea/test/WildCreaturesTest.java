package dev.ahrivaea.test;

import dev.ahrivaea.creature.CreatureEntity;
import dev.ahrivaea.creature.Drops;
import dev.ahrivaea.creature.Influence;
import dev.ahrivaea.creature.Species;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * The creature list in the game: every species loads, wild ones appear around the player on their own, each form's
 * drops come out at the listed rates, and a lineup of placeholder creatures (tinted by influence) is photographed.
 */
public class WildCreaturesTest implements FabricClientGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("ahrivaea");
	private static final String[][] LINEUP = {{"mephit", "fire"}, {"mephit", "water"}, {"axe_beak", "fire"}, {"giant_frog", "water"},
			{"warrior", "storm"}, {"treant", "nature"}, {"phoenix", "radiant"}, {"aboleth", "earth"}, {"angel", "radiant"},
			{"assassin_vine", "nature"}, {"cow", "none"}, {"wolf", "none"}};

	@Override
	public void runTest(ClientGameTestContext context) {
		List<String> failures = new ArrayList<>();
		try (TestSingleplayerContext sp = context.worldBuilder()
				.adjustSettings(c -> c.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE)).create()) {
			sp.getConnection().waitForChunksRender();
			context.getInput().resizeWindow(1600, 900);
			var server = sp.getServer();
			server.runCommand("time set noon");
			server.runCommand("gamerule advance_time false");
			server.runCommand("gamerule spawn_mobs true"); // test worlds start with spawning off

			int species = server.computeOnServer(s -> Species.all().size());
			int forms = server.computeOnServer(s -> Species.all().stream().mapToInt(x -> x.forms().size()).sum());
			LOG.info("WildCreaturesTest: {} species, {} forms", species, forms);
			if (species < 200) failures.add("the creature list should load about 205 species, got " + species);

			// drop rates: a Fire Mephit drops flint 65% of the time
			double flint = server.computeOnServer(s -> {
				Species m = Species.get("mephit").orElseThrow();
				RandomSource r = RandomSource.create(42);
				int hits = 0;
				for (int i = 0; i < 2000; i++) if (Drops.roll(m, Influence.FIRE, 10, r, s.registryAccess()).stream().anyMatch(st -> st.is(Items.FLINT))) hits++;
				return hits / 2000.0;
			});
			LOG.info("WildCreaturesTest: Fire Mephit dropped flint {}% of the time", Math.round(flint * 100));
			if (Math.abs(flint - 0.65) > 0.04) failures.add("Fire Mephit should drop flint about 65% of the time, got " + flint);

			var pos = context.computeOnClient(c -> c.player.blockPosition());

			// a lineup of different creatures (placeholder model, tinted by influence, sized by species)
			server.runCommand("gamerule spawn_mobs false");
			server.runCommand("kill @e[type=ahrivaea:creature]");
			for (int i = 0; i < LINEUP.length; i++) {
				server.runCommand("execute positioned %d %d %d run ahrivaea spawn %s %s 10".formatted(pos.getX() - 11 + i * 2, pos.getY(), pos.getZ() + 8, LINEUP[i][0], LINEUP[i][1]));
			}
			server.runCommand("execute as @e[type=ahrivaea:creature] run data merge entity @s {NoAI:1b}");
			server.runCommand("tp @p %d %d %d 0 20".formatted(pos.getX(), pos.getY() + 3, pos.getZ() - 4));
			context.runOnClient(c -> {
				if (!c.gui.hud.isHidden()) c.gui.hud.toggle();
				c.player.getAbilities().flying = true;
			});
			context.waitTicks(30);
			context.takeScreenshot(TestScreenshotOptions.of("creatures_lineup"));

			// vanilla mobs don't stay: a cow becomes a Cow creature, others become creatures of the biome (or just go)
			server.runCommand("kill @e[type=ahrivaea:creature]");
			server.runCommand("summon minecraft:cow %d %d %d".formatted(pos.getX() + 3, pos.getY(), pos.getZ() + 3));
			server.runCommand("summon minecraft:zombie %d %d %d".formatted(pos.getX() - 20, pos.getY(), pos.getZ() + 3));
			server.runCommand("summon minecraft:villager %d %d %d".formatted(pos.getX() + 20, pos.getY(), pos.getZ() + 3));
			context.waitTicks(10);
			String after = server.computeOnServer(s -> {
				var box = new AABB(pos).inflate(40);
				long vanilla = s.overworld().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, box, m -> !(m instanceof CreatureEntity)).size();
				var creatures = s.overworld().getEntitiesOfClass(CreatureEntity.class, box);
				return vanilla + " vanilla; creatures: " + String.join(", ", creatures.stream().map(CreatureEntity::displayName).toList());
			});
			LOG.info("WildCreaturesTest: after summoning a cow, a zombie and a villager: {}", after);
			if (!after.startsWith("0 vanilla")) failures.add("vanilla mobs should be removed: " + after);
			if (!after.contains("Cow")) failures.add("a cow should become a Cow creature: " + after);
			server.runCommand("gamerule ahrivaea:vanilla_mobs true");
			server.runCommand("summon minecraft:cow %d %d %d".formatted(pos.getX() + 5, pos.getY(), pos.getZ() + 3));
			context.waitTicks(10);
			int cows = server.computeOnServer(s -> s.overworld().getEntitiesOfClass(net.minecraft.world.entity.animal.cow.Cow.class, new AABB(pos).inflate(40)).size());
			if (cows != 1) failures.add("with ahrivaea:vanilla_mobs true, a vanilla cow should stay (found " + cows + ")");
			server.runCommand("gamerule ahrivaea:vanilla_mobs false");
			server.runCommand("kill @e[type=minecraft:cow]");

			// Enraged and Mutated: the same creature in other colours, named for it
			server.runCommand("kill @e[type=ahrivaea:creature]");
			String[] variants = {"none", "enraged", "mutated"};
			for (int i = 0; i < 3; i++)
				server.runCommand("execute positioned %d %d %d run ahrivaea spawn horse none 10 %s".formatted(pos.getX() - 4 + i * 4, pos.getY(), pos.getZ() + 8, variants[i]));
			server.runCommand("execute as @e[type=ahrivaea:creature] run data merge entity @s {NoAI:1b,Rotation:[0f,0f]}");
			server.runCommand("tp @p %.1f %d %.1f 180 15".formatted(pos.getX() + 0.5, pos.getY() + 2, pos.getZ() + 16.5));
			context.waitTicks(30);
			context.takeScreenshot(TestScreenshotOptions.of("creatures_variants"));
			String names = server.computeOnServer(s -> String.join(", ", s.overworld().getEntitiesOfClass(CreatureEntity.class, new AABB(pos).inflate(20))
					.stream().map(CreatureEntity::displayName).sorted().toList()));
			if (!names.equals("Enraged Horse, Horse, Mutated Horse")) failures.add("variant names should be Enraged Horse, Horse, Mutated Horse: " + names);
			// how often: 1 in 10,000, Enraged and Mutated equally
			int[] rolls = server.computeOnServer(s -> {
				RandomSource r = RandomSource.create(5);
				int[] n = new int[3];
				for (int i = 0; i < 400_000; i++) n[dev.ahrivaea.creature.Variant.roll(s.overworld(), r).ordinal()]++;
				return n;
			});
			LOG.info("WildCreaturesTest: in 400,000 wild creatures, {} Enraged and {} Mutated", rolls[1], rolls[2]);
			if (rolls[1] + rolls[2] < 20 || rolls[1] + rolls[2] > 65 || rolls[1] < 8 || rolls[2] < 8) failures.add("about 1 in 10,000 should be Enraged or Mutated, half each: " + rolls[1] + " / " + rolls[2] + " in 400,000");

			// influence tiers: None : elemental : rare = 4 : 3 : 2 where all three live
			String tiers = server.computeOnServer(s -> {
				RandomSource r = RandomSource.create(9);
				int[] n = new int[4], m = new int[4];
				for (int i = 0; i < 90_000; i++) {
					n[Species.pickTier(new long[]{0, 500, 70, 9}, r)]++;
					m[Species.pickTier(new long[]{0, 10, 10, 0}, r)]++;
				}
				String out = "";
				if (Math.abs(n[1] / 90000.0 - 4 / 9.0) > 0.01 || Math.abs(n[2] / 90000.0 - 3 / 9.0) > 0.01 || Math.abs(n[3] / 90000.0 - 2 / 9.0) > 0.01)
					out += "all tiers: " + n[1] + "/" + n[2] + "/" + n[3] + "; ";
				if (m[3] != 0 || Math.abs(m[1] / 90000.0 - 4 / 7.0) > 0.01) out += "no tier 3: " + m[1] + "/" + m[2] + "/" + m[3];
				// and what actually spawns in a few biomes
				var biomes = s.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BIOME);
				for (var key : List.of(net.minecraft.world.level.biome.Biomes.PLAINS, net.minecraft.world.level.biome.Biomes.FOREST, net.minecraft.world.level.biome.Biomes.DESERT,
						net.minecraft.world.level.biome.Biomes.DARK_FOREST, net.minecraft.world.level.biome.Biomes.SNOWY_PLAINS)) {
					int[] t = new int[4];
					for (int i = 0; i < 3000; i++) Species.pickFor(biomes.getOrThrow(key), s.overworld(), r).ifPresent(p -> t[p.influence().spawnTier()]++);
					LOG.info("WildCreaturesTest: tiers in {} out of 3000: none {}, elemental {}, rare {}", key.identifier(), t[1], t[2], t[3]);
				}
				return out;
			});
			if (!tiers.isEmpty()) failures.add("influence tiers should be picked 4 : 3 : 2 among the tiers present: " + tiers);

			// a 1000 block journey: about 15 creatures turn up (one per 67 blocks travelled), a few at a time
			server.runCommand("kill @e[type=ahrivaea:creature]");
			server.runCommand("gamerule spawn_mobs true");
			context.runOnClient(c -> c.player.getAbilities().flying = true);
			int spawnedBefore = server.computeOnServer(s -> dev.ahrivaea.creature.WildSpawner.FAILURES.getOrDefault("spawned", 0));
			int most = 0;
			for (int step = 1; step <= 34; step++) {
				server.runCommand("tp @p %d %d %d -90 10".formatted(pos.getX() + step * 30, pos.getY() + 1, pos.getZ()));
				context.waitTicks(20);
				int near = server.computeOnServer(s -> {
					var pl = s.getPlayerList().getPlayers().getFirst();
					return s.overworld().getEntitiesOfClass(CreatureEntity.class, pl.getBoundingBox().inflate(80), c -> !c.isTame()).size();
				});
				most = Math.max(most, near);
			}
			context.waitTicks(40);
			int spawned = server.computeOnServer(s -> dev.ahrivaea.creature.WildSpawner.FAILURES.getOrDefault("spawned", 0)) - spawnedBefore;
			String why = server.computeOnServer(s -> dev.ahrivaea.creature.WildSpawner.FAILURES.toString());
			LOG.info("WildCreaturesTest: 1000 blocks travelled, {} wild creatures appeared, at most {} nearby at once; attempts: {}", spawned, most, why);
			if (spawned < 8 || spawned > 22) failures.add("about 15 wild creatures should turn up over 1000 blocks, got " + spawned + "; attempts: " + why);
			if (most > dev.ahrivaea.creature.WildSpawner.CAP) failures.add("no more than " + dev.ahrivaea.creature.WildSpawner.CAP + " wild creatures should be near at once, saw " + most);
		}
		if (!failures.isEmpty()) throw new AssertionError(String.join("; ", failures));
	}
}
