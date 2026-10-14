package dev.ahrivaea.test;

import dev.ahrivaea.challenge.Challenges;
import dev.ahrivaea.challenge.StrongholdTowers;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;

/**
 * In a normal world with the server's seed, builds the towers over the nearest strongholds and photographs
 * each one, to check they stand in the right place and take on their biome's blocks.
 */
public class StrongholdTowerScreenshots implements FabricClientGameTest {
	private static final int TOWERS = 6;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext sp = context.worldBuilder()
				.setUseConsistentSettings(false)
				.adjustSettings(c -> {
					c.setSeed("-447167");
					c.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
				}).create()) {
			sp.getConnection().waitForChunksRender();
			var server = sp.getServer();
			server.runCommand("time set noon");
			server.runCommand("gamerule doDaylightCycle false");
			server.runCommand("weather clear");
			context.runOnClient(client -> {
				if (!client.gui.hud.isHidden()) client.gui.hud.toggle();
			});
			for (int i = 0; i < TOWERS; i++) {
				final int n = i;
				server.runCommand("challenge generate " + n);
				var origin = server.computeOnServer(s -> Challenges.state(s, StrongholdTowers.towerId(n)).map(st -> st.origin).orElse(null));
				if (origin == null) throw new AssertionError("No tower was built for stronghold " + n);
				String palette = server.computeOnServer(s -> Challenges.state(s, StrongholdTowers.towerId(n)).map(st -> st.palette + "_" + st.challenge).orElse("?"));
				server.runCommand("tp @a %.1f %.1f %.1f -25 18".formatted(origin.getX() + 15.5, origin.getY() + 13.0, origin.getZ() - 14.0));
				context.runOnClient(client -> client.player.getAbilities().flying = true);
				context.waitTicks(20);
				sp.getConnection().waitForChunksRender();
				context.waitTicks(10);
				context.takeScreenshot(TestScreenshotOptions.of("stronghold_tower_" + (n + 1) + "_" + palette).withSize(1600, 900));
			}
		}
	}
}
