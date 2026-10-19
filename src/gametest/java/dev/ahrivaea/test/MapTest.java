package dev.ahrivaea.test;

import dev.ahrivaea.creature.CreatureEntity;
import dev.ahrivaea.map.Markers;
import dev.ahrivaea.map.client.MapClient;
import dev.ahrivaea.map.client.MapScreen;
import dev.ahrivaea.map.client.MapTiles;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * The map: what's around the player gets drawn and saved; creatures nearby (one of them yours) and the player
 * come through as markers; the full map and the minimap are screenshotted; the show_creatures game rule hides them.
 */
public class MapTest implements FabricClientGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("ahrivaea");

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
			// something to see: a pond, a road, a sand patch and a house
			var at = context.computeOnClient(c -> c.player.blockPosition());
			int gx = at.getX(), gy = at.getY() - 1, gz = at.getZ();
			server.runCommand("fill %d %d %d %d %d %d minecraft:water".formatted(gx - 30, gy - 2, gz - 20, gx - 14, gy, gz - 6));
			server.runCommand("fill %d %d %d %d %d %d minecraft:stone_bricks".formatted(gx - 60, gy, gz + 3, gx + 60, gy, gz + 5));
			server.runCommand("fill %d %d %d %d %d %d minecraft:sand".formatted(gx + 14, gy, gz - 24, gx + 30, gy, gz - 12));
			server.runCommand("fill %d %d %d %d %d %d minecraft:oak_planks".formatted(gx - 12, gy + 1, gz + 10, gx - 4, gy + 4, gz + 18));
			server.runCommand("fill %d %d %d %d %d %d minecraft:red_terracotta".formatted(gx - 12, gy + 5, gz + 10, gx - 4, gy + 5, gz + 18));
			context.waitTicks(100);
			int explored = context.computeOnClient(c -> MapTiles.explored());
			LOG.info("MapTest: {} columns explored", explored);
			if (explored < 2000) failures.add("the area around the player should be on the map: " + explored + " columns");

			// three creatures nearby (there may be wild ones too), one of them tamed by the player
			var pos = context.computeOnClient(c -> c.player.blockPosition());
			for (int i = 0; i < 3; i++) server.runCommand("summon ahrivaea:creature %d %d %d".formatted(pos.getX() + 6 + i * 5, pos.getY() + 1, pos.getZ() - 8 + i * 6));
			context.waitTicks(10);
			server.runOnServer(s -> {
				var p = s.getPlayerList().getPlayers().getFirst();
				var list = s.overworld().getEntitiesOfClass(CreatureEntity.class, p.getBoundingBox().inflate(40), e -> true);
				for (var e : list) e.setNoAi(true);
				if (!list.isEmpty()) list.getFirst().tame(p);
			});
			context.waitTicks(30);
			String seen = context.computeOnClient(c -> {
				var m = MapClient.markers;
				long yours = m.creatures().stream().filter(x -> x.kind() == Markers.YOURS).count();
				boolean me = m.players().stream().anyMatch(p -> p.id().equals(c.player.getUUID()));
				String names = String.join(" / ", m.creatures().stream().map(Markers.CreatureMark::name).toList());
				return "creatures " + m.creatures().size() + ", yours " + yours + ", me " + me + " (" + names + ")";
			});
			LOG.info("MapTest: markers: {}", seen);
			if (!seen.matches("creatures ([3-9]|\\d\\d+), yours 1, me true.*")) failures.add("the three creatures (one yours) and the player should be on the map: " + seen);

			context.runOnClient(c -> {
				MapScreen s = new MapScreen();
				s.scale = 4;
				c.gui.setScreen(s);
			});
			context.waitTicks(10);
			context.takeScreenshot(TestScreenshotOptions.of("map_1_full").withSize(1600, 900));
			context.runOnClient(c -> ((MapScreen) c.gui.screen()).scale = 0.5f);
			context.waitTicks(5);
			context.takeScreenshot(TestScreenshotOptions.of("map_2_zoomed_out").withSize(1600, 900));
			context.runOnClient(c -> {
				c.gui.setScreen(null);
				if (c.gui.hud.isHidden()) c.gui.hud.toggle();
			});
			context.waitTicks(10);
			context.takeScreenshot(TestScreenshotOptions.of("map_3_minimap").withSize(1600, 900));

			// saved to disk
			long files = context.computeOnClient(c -> {
				MapTiles.saveAll();
				try (var list = Files.list(MapTiles.dir())) {
					return list.filter(f -> f.toString().endsWith(".png")).count();
				} catch (Exception e) {
					return -1L;
				}
			});
			LOG.info("MapTest: {} tiles saved", files);
			if (files < 1) failures.add("the map should be saved as PNG tiles: " + files);

			// the server can hide creatures
			server.runCommand("gamerule ahrivaea_map:show_creatures false");
			context.waitTicks(30);
			int hidden = context.computeOnClient(c -> MapClient.markers.creatures().size());
			if (hidden != 0) failures.add("show_creatures false should hide the creatures: " + hidden);

			server.runOnServer(s -> {
				var p = s.getPlayerList().getPlayers().getFirst();
				for (var e : s.overworld().getEntitiesOfClass(CreatureEntity.class, p.getBoundingBox().inflate(40), e -> true)) e.discard();
			});
			context.waitTicks(5);
		}
		if (!failures.isEmpty()) throw new AssertionError(String.join("; ", failures));
	}
}
