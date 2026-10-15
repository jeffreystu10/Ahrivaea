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
			if (species < 200) failures.add("the creature list should load about 206 species, got " + species);

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

			// wild creatures turn up on their own (this is a plains world)
			var pos = context.computeOnClient(c -> c.player.blockPosition());
			int wild = 0;
			for (int t = 0; t < 900 && wild < 4; t += 20) {
				context.waitTicks(20);
				wild = server.computeOnServer(s -> s.overworld().getEntitiesOfClass(CreatureEntity.class, new AABB(pos).inflate(90), c -> !c.isTame()).size());
			}
			String kinds = server.computeOnServer(s -> String.join(", ", s.overworld().getEntitiesOfClass(CreatureEntity.class, new AABB(pos).inflate(90), c -> !c.isTame())
					.stream().map(CreatureEntity::displayName).toList()));
			String why = server.computeOnServer(s -> dev.ahrivaea.creature.WildSpawner.FAILURES.toString());
			LOG.info("WildCreaturesTest: {} wild creatures appeared: {}; spawn attempts: {}", wild, kinds, why);
			if (wild < 4) failures.add("wild creatures should appear around the player, only " + wild + " did; attempts: " + why);

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
		}
		if (!failures.isEmpty()) throw new AssertionError(String.join("; ", failures));
	}
}
