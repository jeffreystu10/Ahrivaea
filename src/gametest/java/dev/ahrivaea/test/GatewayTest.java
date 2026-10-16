package dev.ahrivaea.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * The Ahrivaea Gateway datapack with a real player: build a portal, step in, get offered the trip, and asking to go
 * from outside the portal is refused. (Actually transferring would need a second server.)
 */
public class GatewayTest implements FabricClientGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("ahrivaea");

	@Override
	public void runTest(ClientGameTestContext context) {
		List<String> failures = new ArrayList<>();
		try (TestSingleplayerContext sp = context.worldBuilder()
				.adjustSettings(c -> c.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE)).create()) {
			sp.getConnection().waitForChunksRender();
			context.getInput().resizeWindow(1600, 900);
			var server = sp.getServer();
			// install the datapack into this world
			String copied = server.computeOnServer(s -> {
				Path to = s.getWorldPath(LevelResource.DATAPACK_DIR).resolve("ahrivaea_gateway");
				Path from = findPack();
				if (from == null) return "datapacks/ahrivaea_gateway not found from " + Path.of("").toAbsolutePath();
				try (Stream<Path> files = Files.walk(from)) {
					for (Path p : (Iterable<Path>) files::iterator) {
						Path dest = to.resolve(from.relativize(p).toString());
						if (Files.isDirectory(p)) Files.createDirectories(dest);
						else Files.copy(p, dest);
					}
				} catch (IOException e) {
					return e.toString();
				}
				return "ok";
			});
			if (!copied.equals("ok")) throw new AssertionError("couldn't install the datapack: " + copied);
			server.runCommand("reload");
			context.waitTicks(40);
			server.runCommand("datapack enable \"file/ahrivaea_gateway\"");
			context.waitTicks(40);
			server.runCommand("time set noon");
			server.runCommand("gamerule advance_time false");
			server.runCommand("function gateway:setup {name:\"Ahrivaea\",host:\"localhost\",port:1,note:\"You need the Ahrivaea modpack.\"}");

			// build a portal 3 blocks south of the player (facing south), wait out the join cooldown, then walk in
			var pos = context.computeOnClient(c -> c.player.blockPosition());
			server.runCommand("tp @p %d %d %d 0 0".formatted(pos.getX(), pos.getY(), pos.getZ()));
			context.waitTicks(5);
			server.runCommand("execute as @p at @s run function gateway:build");
			boolean built = server.computeOnServer(s -> !s.overworld().getEntitiesOfClass(net.minecraft.world.entity.Marker.class,
					new net.minecraft.world.phys.AABB(pos).inflate(8), m -> m.entityTags().contains("gateway.portal")).isEmpty());
			if (!built) failures.add("gateway:build should make a portal");
			context.runOnClient(c -> {
				if (c.gui.hud.isHidden()) c.gui.hud.toggle(); // show chat
			});
			context.waitTicks(110);
			server.runCommand("tp @p %.1f %d %.1f 180 5".formatted(pos.getX() + 0.5, pos.getY(), pos.getZ() + 11.5));
			context.waitTicks(10);
			context.takeScreenshot(TestScreenshotOptions.of("gateway_1_portal").withSize(1600, 900));
			server.runCommand("tp @p %.1f %d %.1f 0 0".formatted(pos.getX() + 0.5, pos.getY(), pos.getZ() + 3.5));
			context.waitTicks(10);
			boolean prompted = server.computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().entityTags().contains("gateway.prompted"));
			LOG.info("GatewayTest: portal built {}, player offered the trip {}", built, prompted);
			if (!prompted) failures.add("stepping into the portal should offer the trip");
			server.runCommand("tp @p %.1f %d %.1f 180 5".formatted(pos.getX() + 0.5, pos.getY(), pos.getZ() + 9.5));
			context.waitTicks(10);
			context.takeScreenshot(TestScreenshotOptions.of("gateway_2_offer").withSize(1600, 900));
			// asking to go while standing outside the portal is refused (and doesn't start a transfer)
			server.runCommand("execute as @p run trigger gateway.go");
			context.waitTicks(5);
			int leaving = server.computeOnServer(s -> {
				var p = s.getPlayerList().getPlayers().getFirst();
				var obj = s.getScoreboard().getObjective("gateway.leaving");
				var score = obj == null ? null : s.getScoreboard().getPlayerScoreInfo(p, obj);
				return score == null ? 0 : score.value();
			});
			if (leaving != 0) failures.add("asking to travel from outside the portal should be refused");
			server.runCommand("tp @p %.1f %d %.1f 180 5".formatted(pos.getX() + 0.5, pos.getY(), pos.getZ() + 6.5));
			context.waitTicks(2);
			server.runCommand("execute as @p at @s run function gateway:remove");
			context.waitTicks(5);
			boolean removed = server.computeOnServer(s -> s.overworld().getEntitiesOfClass(net.minecraft.world.entity.Marker.class,
					new net.minecraft.world.phys.AABB(pos).inflate(12), m -> m.entityTags().contains("gateway.portal")).isEmpty());
			if (!removed) failures.add("gateway:remove should remove the portal");
		}
		if (!failures.isEmpty()) throw new AssertionError(String.join("; ", failures));
	}

	/** The datapack in the repository, looked for from the game's working directory upwards. */
	private static Path findPack() {
		Path dir = Path.of("").toAbsolutePath();
		for (int i = 0; i < 4 && dir != null; i++, dir = dir.getParent()) {
			Path p = dir.resolve("datapacks/ahrivaea_gateway");
			if (Files.isDirectory(p)) return p;
		}
		return null;
	}
}
