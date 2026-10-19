package dev.ahrivaea.test;

import dev.ahrivaea.guide.GuideData;
import dev.ahrivaea.guide.GuideScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/** The Ahrivaea Guide: every mod and every recipe is there and readable, and its pages are photographed. */
public class GuideTest implements FabricClientGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("ahrivaea");

	@Override
	public void runTest(ClientGameTestContext context) {
		List<String> failures = new ArrayList<>();
		// not on any server (like a vanilla server, nothing announces itself): every mod's client side stays off
		context.waitTicks(5);
		String off = context.computeOnClient(c -> "guide " + GuideData.load().forThisServer().mods.size()
				+ ", horses " + dev.ahrivaea.horses.client.HorsesClient.serverHasMod() + ", minecarts " + dev.ahrivaea.minecarts.MinecartsMod.serverHasMod);
		LOG.info("GuideTest: with no server: {}", off);
		if (!off.equals("guide 0, horses false, minecarts false")) failures.add("with no server, the mods should be off: " + off);
		try (TestSingleplayerContext sp = context.worldBuilder()
				.adjustSettings(c -> c.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE)).create()) {
			sp.getConnection().waitForChunksRender();
			context.getInput().resizeWindow(1600, 900);
			String summary = context.computeOnClient(c -> {
				GuideData d = GuideData.load();
				int recipes = d.allRecipes().size();
				long broken = d.allRecipes().stream().filter(r -> r.result().is(Items.BARRIER)
						|| r.slots().stream().anyMatch(s -> s.stream().anyMatch(st -> st.is(Items.BARRIER)))).count();
				StringBuilder sb = new StringBuilder();
				for (GuideData.Mod m : d.mods) sb.append(m.key()).append('=').append(m.recipes().size()).append('/').append(m.items().size()).append(' ');
				return d.mods.size() + " mods, " + recipes + " recipes, " + broken + " with unknown items; recipes/items per mod: " + sb;
			});
			LOG.info("GuideTest: {}", summary);
			if (!summary.startsWith("10 mods")) failures.add("the guide should list 10 mods: " + summary);
			if (!summary.contains(" 0 with unknown items")) failures.add("every recipe's items should be known: " + summary);
			if (!summary.contains("woodcutting=256/")) failures.add("woodcutting should have 256 recipes: " + summary);
			if (!summary.contains("stellar=9/")) failures.add("stellar should have 9 smithing recipes: " + summary);

			// single player: the built-in server has every mod, so they're all on
			String on = context.computeOnClient(c -> "guide " + GuideData.load().forThisServer().mods.size()
					+ ", horses " + dev.ahrivaea.horses.client.HorsesClient.serverHasMod() + ", minecarts " + dev.ahrivaea.minecarts.MinecartsMod.serverHasMod);
			LOG.info("GuideTest: in single player: {}", on);
			if (!on.equals("guide 10, horses true, minecarts true")) failures.add("in single player, every mod should be on: " + on);
			context.runOnClient(c -> c.gui.setScreen(new GuideScreen(GuideData.load(), null)));
			context.waitTicks(10);
			context.takeScreenshot(TestScreenshotOptions.of("guide_1_all_mods").withSize(1600, 900));

			context.runOnClient(c -> c.gui.setScreen(new GuideScreen(GuideData.load(), "creatures")));
			context.waitTicks(10);
			context.takeScreenshot(TestScreenshotOptions.of("guide_2_creatures_about").withSize(1600, 900));

			context.runOnClient(c -> ((GuideScreen) c.gui.screen()).showRecipes());
			context.waitTicks(10);
			context.takeScreenshot(TestScreenshotOptions.of("guide_3_creatures_recipes").withSize(1600, 900));

			int doors = context.computeOnClient(c -> {
				c.gui.setScreen(new GuideScreen(GuideData.load(), "woodcutting"));
				GuideScreen s = (GuideScreen) c.gui.screen();
				s.showRecipes();
				s.search("door");
				return s.shownCount();
			});
			LOG.info("GuideTest: woodcutting recipes matching \"door\": {}", doors);
			if (doors < 10) failures.add("searching woodcutting for door should find the door and trapdoor recipes, found " + doors);
			context.waitTicks(10);
			context.takeScreenshot(TestScreenshotOptions.of("guide_4_woodcutting_search").withSize(1600, 900));

			int vessel = context.computeOnClient(c -> {
				c.gui.setScreen(new GuideScreen(GuideData.load(), null));
				GuideScreen s = (GuideScreen) c.gui.screen();
				s.showRecipesFor(BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("ahrivaea", "vessel")));
				return s.shownCount();
			});
			LOG.info("GuideTest: recipes with a Vessel: {}", vessel);
			if (vessel != 1) failures.add("the Vessel is made by one recipe (the Base Core that used it has no recipe now), found " + vessel);
			context.waitTicks(10);
			context.takeScreenshot(TestScreenshotOptions.of("guide_5_vessel").withSize(1600, 900));

			context.runOnClient(c -> c.gui.setScreen(new GuideScreen(GuideData.load(), "stellar")));
			context.runOnClient(c -> ((GuideScreen) c.gui.screen()).showRecipes());
			context.waitTicks(10);
			context.takeScreenshot(TestScreenshotOptions.of("guide_6_stellar_recipes").withSize(1600, 900));
			context.runOnClient(c -> c.gui.setScreen(null));
		}
		if (!failures.isEmpty()) throw new AssertionError(String.join("; ", failures));
	}
}
