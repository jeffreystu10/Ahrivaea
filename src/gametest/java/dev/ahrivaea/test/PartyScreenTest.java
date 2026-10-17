package dev.ahrivaea.test;

import dev.ahrivaea.client.PartyClient;
import dev.ahrivaea.client.PartyScreen;
import dev.ahrivaea.creature.Influence;
import dev.ahrivaea.party.CaughtCreature;
import dev.ahrivaea.party.Party;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;

import java.util.ArrayList;
import java.util.List;

/**
 * The party summary on screen and the party screen: a party of five (one hurt), the summary down the left, the
 * party key opens the screen, sending one out moves it to the Out tab, and calling it back brings it home.
 */
public class PartyScreenTest implements FabricClientGameTest {
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
			server.runCommand("gamerule spawn_mobs false");
			server.runOnServer(s -> {
				var p = s.getPlayerList().getPlayers().getFirst();
				Party.add(p, new CaughtCreature("horse", Influence.NONE, 24, 40, 30, 2, 3, true));
				Party.add(p, new CaughtCreature("dragon", Influence.FIRE, 45, 90, 400));
				Party.add(p, new CaughtCreature("pixie", Influence.FAE, 8, 0, 4));
				Party.add(p, new CaughtCreature("griffon", Influence.NONE, 30, 10, 60));
				Party.add(p, new CaughtCreature("cat", Influence.NONE, 3, 75, 12));
				Party.addToBox(p, new CaughtCreature("wolf", Influence.NONE, 12, 0, 25));
				Party.addXp(p, 250);
			});
			context.runOnClient(c -> {
				if (c.gui.hud.isHidden()) c.gui.hud.toggle();
			});
			context.waitFor(c -> PartyClient.OPEN != null && c.player != null, 100);
			context.waitTicks(30);
			context.takeScreenshot(TestScreenshotOptions.of("party_1_summary").withSize(1600, 900));
			// the party key opens the screen
			context.getInput().pressKey(o -> PartyClient.OPEN);
			context.waitFor(c -> c.gui.screen() instanceof PartyScreen, 60);
			context.waitTicks(5);
			context.takeScreenshot(TestScreenshotOptions.of("party_2_screen").withSize(1600, 900));
			// send the horse out (the screen's button runs this)
			context.runOnClient(c -> c.player.connection.sendCommand("party send 1"));
			context.waitTicks(30);
			int left = server.computeOnServer(s -> Party.get(s.getPlayerList().getPlayers().getFirst()).size());
			if (left != 4) failures.add("sending one out should leave 4 in the party, got " + left);
			context.runOnClient(c -> c.gui.setScreen(null));
			context.waitTicks(5);
			context.takeScreenshot(TestScreenshotOptions.of("party_3_summary_one_out").withSize(1600, 900));
			int out = context.computeOnClient(c -> PartyClient.outCount());
			if (out != 1) failures.add("the summary should show the creature that's out, got " + out);
			// call it back by its entity number, as the Out tab's button does
			context.runOnClient(c -> {
				var e = c.level.getEntitiesOfClass(dev.ahrivaea.creature.CreatureEntity.class, c.player.getBoundingBox().inflate(10), x -> x.isTame());
				if (!e.isEmpty()) c.player.connection.sendCommand("party recall " + e.getFirst().getId());
			});
			context.waitTicks(30);
			var back = server.computeOnServer(s -> Party.get(s.getPlayerList().getPlayers().getFirst()));
			if (back.size() != 5) failures.add("calling it back should make the party 5 again, got " + back.size());
			else {
				CaughtCreature h = back.getLast();
				if (h.xp() != 40 || h.mountTier() != 2 || h.mountFed() != 3 || !h.saddled())
					failures.add("a creature sent out and called back should keep its XP, training and saddle: " + h);
			}
		}
		if (!failures.isEmpty()) throw new AssertionError(String.join("; ", failures));
	}
}
