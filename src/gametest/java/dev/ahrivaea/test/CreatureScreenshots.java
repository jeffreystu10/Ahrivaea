package dev.ahrivaea.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;

/**
 * Starts the real game (no window), lines up every dragon form on a flat world and takes screenshots,
 * so the models can be checked without anyone running Minecraft. CI publishes them to the
 * ci-screenshots branch.
 */
public class CreatureScreenshots implements FabricClientGameTest {
	private static final String[] FORMS = {"fire", "frost", "storm", "earth", "water", "nature", "void", "fae", "darkness", "radiant", "none"};

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext sp = context.worldBuilder()
				.adjustSettings(c -> c.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE)).create()) {
			sp.getConnection().waitForChunksRender();
			BlockPos p = context.computeOnClient(client -> client.player.blockPosition());
			int x = p.getX(), y = p.getY(), z = p.getZ();
			var server = sp.getServer();
			server.runCommand("time set noon");
			server.runCommand("weather clear");
			server.runCommand("gamerule doDaylightCycle false");
			server.runCommand("gamerule doMobSpawning false");
			for (int i = 0; i < FORMS.length; i++) {
				int dx = (i % 4) * 7 - 10, dz = 10 + (i / 4) * 8;
				server.runCommand("execute positioned %d %d %d run ahrivaea spawn dragon %s 30".formatted(x + dx, y, z + dz, FORMS[i]));
			}
			server.runCommand("execute as @e[type=ahrivaea:creature] run data merge entity @s {NoAI:1b}");
			context.runOnClient(client -> {
				if (!client.gui.hud.isHidden()) client.gui.hud.toggle();
				client.player.getAbilities().flying = true;
			});
			context.waitTicks(40);

			// all eleven from above and in front (the player faces south, towards them)
			view(context, server, x - 0.5, y + 11, z - 6, 0, 38);
			context.takeScreenshot(TestScreenshotOptions.of("dragons_overview").withSize(1600, 900));

			// Ashen Dragon close up, three-quarter front
			view(context, server, x - 10 + 4.5, y + 3, z + 10 + 5.5, 140, 18);
			context.takeScreenshot(TestScreenshotOptions.of("ashen_closeup").withSize(1600, 900));

			// side view, to check it stands upright and faces the right way
			view(context, server, x - 10 + 8, y + 1.5, z + 10, 90, 5);
			context.takeScreenshot(TestScreenshotOptions.of("ashen_side").withSize(1600, 900));

			// back row from behind
			view(context, server, x + 0.5, y + 7, z + 38, 180, 25);
			context.takeScreenshot(TestScreenshotOptions.of("dragons_back").withSize(1600, 900));
		}
	}

	private static void view(ClientGameTestContext context, net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server,
			double x, double y, double z, float yaw, float pitch) {
		server.runCommand("tp @a %.2f %.2f %.2f %.1f %.1f".formatted(x, y, z, yaw, pitch));
		context.waitTicks(10);
	}
}
