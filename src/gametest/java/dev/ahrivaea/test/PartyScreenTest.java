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
			// food heals outside battle: the horse that's out eats cooked beef from our hand (36% of 116 HP), and
			// the Cat in the party eats bread from the party screen (20% of its 71 HP)
			server.runCommand("gamemode survival @a");
			server.runCommand("item replace entity @a weapon.mainhand with minecraft:cooked_beef 4");
			context.waitTicks(3);
			context.runOnClient(c -> {
				var e = c.level.getEntitiesOfClass(dev.ahrivaea.creature.CreatureEntity.class, c.player.getBoundingBox().inflate(10), x -> x.isTame());
				if (!e.isEmpty()) c.gameMode.interact(c.player, e.getFirst(), new net.minecraft.world.phys.EntityHitResult(e.getFirst()), net.minecraft.world.InteractionHand.MAIN_HAND);
			});
			context.waitTicks(5);
			float horseHp = server.computeOnServer(s -> {
				var p = s.getPlayerList().getPlayers().getFirst();
				var e = s.overworld().getEntitiesOfClass(dev.ahrivaea.creature.CreatureEntity.class, p.getBoundingBox().inflate(10), x -> x.isTame());
				return e.isEmpty() ? -1f : e.getFirst().getHealth();
			});
			if (Math.abs(horseHp - (30 + Math.round(116 * 0.36f))) > 1) failures.add("cooked beef should heal the Horse that's out by 36% (to 72), it has " + horseHp);
			server.runCommand("item replace entity @a weapon.mainhand with minecraft:bread 2");
			context.waitTicks(3);
			context.runOnClient(c -> c.player.connection.sendCommand("party feed 4"));
			context.waitTicks(10);
			float catHp = server.computeOnServer(s -> Party.get(s.getPlayerList().getPlayers().getFirst()).get(3).health());
			int breadLeft = server.computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().getMainHandItem().getCount());
			if (Math.abs(catHp - (12 + Math.round(71 * 0.2f))) > 1 || breadLeft != 1) failures.add("bread should heal the Cat in the party by 20% (to 26) and be used up: hp " + catHp + ", bread left " + breadLeft);
			context.runOnClient(c -> c.gui.setScreen(new PartyScreen()));
			context.waitTicks(5);
			context.takeScreenshot(TestScreenshotOptions.of("party_4_fed").withSize(1600, 900));
			context.runOnClient(c -> c.gui.setScreen(null));
			context.waitTicks(3);

			// with the F3 debug text on: the summary moves below it, and the party screen starts a little lower
			context.runOnClient(c -> c.getDebugOverlay().debugEntries.setOverlayVisible(true));
			context.waitTicks(5);
			context.takeScreenshot(TestScreenshotOptions.of("party_5_with_f3").withSize(1600, 900));
			context.runOnClient(c -> c.gui.setScreen(new PartyScreen()));
			context.waitTicks(5);
			context.takeScreenshot(TestScreenshotOptions.of("party_6_screen_with_f3").withSize(1600, 900));
			context.runOnClient(c -> {
				c.gui.setScreen(null);
				c.getDebugOverlay().debugEntries.setOverlayVisible(false);
			});
			context.waitTicks(3);

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
