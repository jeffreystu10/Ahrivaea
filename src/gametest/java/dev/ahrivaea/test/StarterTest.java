package dev.ahrivaea.test;

import dev.ahrivaea.client.StarterScreen;
import dev.ahrivaea.party.Party;
import dev.ahrivaea.party.Starters;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;

import java.util.ArrayList;
import java.util.List;

/**
 * Picking a starter: the starter screen (opened with /starter here; a real new player gets it on joining) lists the
 * Common creatures, choosing one puts it in the party at level 1, and you can only choose once.
 */
public class StarterTest implements FabricClientGameTest {
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
			int options = server.computeOnServer(s -> Starters.options().size());
			if (options < 30) failures.add("there should be a few dozen Common starters, got " + options);
			context.runOnClient(c -> c.player.connection.sendCommand("starter"));
			context.waitFor(c -> c.gui.screen() instanceof StarterScreen, 100);
			context.waitTicks(10);
			context.takeScreenshot(TestScreenshotOptions.of("starter_1_screen").withSize(1600, 900));
			context.runOnClient(c -> ((StarterScreen) c.gui.screen()).choose("wolf"));
			context.waitTicks(10);
			context.takeScreenshot(TestScreenshotOptions.of("starter_2_wolf").withSize(1600, 900));
			context.runOnClient(c -> ((StarterScreen) c.gui.screen()).confirm());
			context.waitTicks(20);
			var party = server.computeOnServer(s -> Party.get(s.getPlayerList().getPlayers().getFirst()));
			if (party.size() != 1 || !party.getFirst().species().equals("wolf") || party.getFirst().level() != 1)
				failures.add("choosing the Wolf should put a level 1 Wolf in the party, got " + party);
			// only once
			context.runOnClient(c -> c.player.connection.sendCommand("starter"));
			context.waitTicks(20);
			if (context.computeOnClient(c -> c.gui.screen() instanceof StarterScreen)) failures.add("/starter shouldn't open again after choosing");
			context.runOnClient(c -> {
				if (c.gui.hud.isHidden()) c.gui.hud.toggle();
			});
			context.waitTicks(20);
			context.takeScreenshot(TestScreenshotOptions.of("starter_3_in_party").withSize(1600, 900));
		}
		if (!failures.isEmpty()) throw new AssertionError(String.join("; ", failures));
	}
}
