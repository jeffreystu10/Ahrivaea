package dev.ahrivaea.test;

import dev.ahrivaea.battle.Arena;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;

import java.util.Locale;

/** Builds one battle arena of every theme and photographs it from the south edge (arena_<theme>.png). */
public class ArenaScreenshots implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext sp = context.worldBuilder()
				.adjustSettings(c -> c.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE)).create()) {
			sp.getConnection().waitForChunksRender();
			context.getInput().resizeWindow(1600, 900);
			var server = sp.getServer();
			server.runCommand("gamerule doMobSpawning false");
			context.runOnClient(c -> {
				if (!c.gui.hud.isHidden()) c.gui.hud.toggle();
			});
			for (Arena.Theme theme : Arena.Theme.values()) {
				String name = theme.name().toLowerCase(Locale.ROOT);
				server.runCommand("execute as @p run battle arena " + name);
				for (int t = 0; t < 400 && !context.computeOnClient(c -> c.level != null && c.level.dimension().equals(Arena.DIMENSION) && c.gui.screen() == null); t++) context.waitTick();
				String where = context.computeOnClient(c -> "dimension " + (c.level == null ? "none" : c.level.dimension().identifier()) + ", screen " + (c.gui.screen() == null ? "none" : c.gui.screen().getClass().getName()) + ", at " + (c.player == null ? "?" : c.player.blockPosition().toShortString()));
				if (!where.contains("battle_arena") || !where.contains("screen none")) throw new AssertionError("arena " + name + " didn't open: " + where);
				context.waitTicks(10);
				sp.getConnection().waitForChunksRender();
				context.waitTicks(10);
				context.takeScreenshot(TestScreenshotOptions.of("arena_" + name));
			}
			server.runCommand("execute as @p run battle home");
			context.waitFor(c -> c.level != null && !c.level.dimension().equals(Arena.DIMENSION), 400);
			context.waitTicks(20);
			// the arenas can't be visited outside a battle: someone teleported in is sent straight back
			server.runCommand("execute in ahrivaea:battle_arena run tp @p 0 80 0");
			boolean arrived = server.computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().level().dimension().equals(Arena.DIMENSION));
			context.waitTicks(60);
			boolean still = server.computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().level().dimension().equals(Arena.DIMENSION));
			if (!arrived) throw new AssertionError("the /tp into the arena dimension didn't happen, so the lock-out wasn't tested");
			if (still) throw new AssertionError("a player who teleported into the arenas without a battle should be sent back");
		}
	}
}
