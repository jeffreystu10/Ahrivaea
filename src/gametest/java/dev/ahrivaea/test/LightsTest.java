package dev.ahrivaea.test;

import dev.ahrivaea.lights.HeldLights;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Handheld lights: in a dark stone room, holding a torch lights the room up; walking moves the light; putting the
 * torch away takes it away; a lava bucket underwater gives a waterlogged light. Screenshots of the room dark and lit.
 */
public class LightsTest implements FabricClientGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("ahrivaea");

	@Override
	public void runTest(ClientGameTestContext context) {
		List<String> failures = new ArrayList<>();
		try (TestSingleplayerContext sp = context.worldBuilder()
				.adjustSettings(c -> c.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL)).create()) {
			sp.getConnection().waitForChunksRender();
			context.getInput().resizeWindow(1600, 900);
			var server = sp.getServer();
			server.runCommand("time set midnight");
			server.runCommand("gamerule advance_time false");
			server.runCommand("gamerule spawn_mobs false");
			server.runCommand("effect give @a minecraft:resistance 600 4 true");
			BlockPos pos = context.computeOnClient(c -> c.player.blockPosition());
			// a dark stone room 9x9, 4 high, with a few things in it to see
			BlockPos a = pos.offset(20, 0, 0);
			server.runCommand("fill %d %d %d %d %d %d minecraft:stone hollow".formatted(a.getX() - 1, a.getY() - 1, a.getZ() - 1, a.getX() + 9, a.getY() + 4, a.getZ() + 9));
			server.runCommand("setblock %d %d %d minecraft:crafting_table".formatted(a.getX() + 6, a.getY(), a.getZ() + 6));
			server.runCommand("setblock %d %d %d minecraft:chest".formatted(a.getX() + 7, a.getY(), a.getZ() + 3));
			server.runCommand("clear @a");
			server.runCommand("tp @a %d %d %d -45 20".formatted(a.getX() + 1, a.getY(), a.getZ() + 1));
			context.runOnClient(c -> {
				if (!c.gui.hud.isHidden()) c.gui.hud.toggle();
			});
			context.waitTicks(40);
			context.takeScreenshot(TestScreenshotOptions.of("lights_1_dark").withSize(1600, 900));
			server.runCommand("item replace entity @a weapon.mainhand with minecraft:torch");
			context.waitTicks(20);
			String lit = server.computeOnServer(s -> {
				var p = s.getPlayerList().getPlayers().getFirst();
				BlockPos at = HeldLights.litAt(p);
				var lv = s.overworld();
				return at == null ? "no light" : lv.getBlockState(at).getBlock().getName().getString() + " " + lv.getBlockState(at).getValue(LightBlock.LEVEL)
						+ ", block light 3 away " + lv.getBrightness(LightLayer.BLOCK, at.offset(3, 0, 0));
			});
			LOG.info("LightsTest: holding a torch: {}", lit);
			if (!lit.startsWith("Light 14") || !lit.endsWith(" 11")) failures.add("holding a torch should light the room (14 at you, 11 three blocks away): " + lit);
			context.takeScreenshot(TestScreenshotOptions.of("lights_2_torch").withSize(1600, 900));

			// walking: the light moves, the old spot is dark again
			BlockPos first = server.computeOnServer(s -> HeldLights.litAt(s.getPlayerList().getPlayers().getFirst()));
			server.runCommand("tp @a %d %d %d".formatted(a.getX() + 5, a.getY(), a.getZ() + 2));
			context.waitTicks(10);
			String moved = server.computeOnServer(s -> {
				var p = s.getPlayerList().getPlayers().getFirst();
				return "old " + s.overworld().getBlockState(first).getBlock().getName().getString() + ", new " + HeldLights.litAt(p);
			});
			LOG.info("LightsTest: after moving: {}", moved);
			if (!moved.startsWith("old Air") || moved.endsWith("null")) failures.add("the light should move with the player: " + moved);

			// put away: gone
			server.runCommand("clear @a");
			context.waitTicks(10);
			String gone = server.computeOnServer(s -> {
				var p = s.getPlayerList().getPlayers().getFirst();
				int lights = 0;
				for (BlockPos b : BlockPos.betweenClosed(a, a.offset(8, 3, 8))) if (s.overworld().getBlockState(b).is(Blocks.LIGHT)) lights++;
				return HeldLights.litAt(p) + ", " + lights + " light blocks";
			});
			if (!gone.equals("null, 0 light blocks")) failures.add("putting the torch away should take the light away: " + gone);

			// underwater with a lava bucket: a waterlogged light, and the water comes back after
			BlockPos w = pos.offset(-20, 0, 0);
			server.runCommand("fill %d %d %d %d %d %d minecraft:glass hollow".formatted(w.getX() - 1, w.getY() - 1, w.getZ() - 1, w.getX() + 3, w.getY() + 4, w.getZ() + 3));
			server.runCommand("fill %d %d %d %d %d %d minecraft:water".formatted(w.getX(), w.getY(), w.getZ(), w.getX() + 2, w.getY() + 3, w.getZ() + 2));
			server.runCommand("tp @a %d %d %d".formatted(w.getX() + 1, w.getY() + 1, w.getZ() + 1));
			server.runCommand("item replace entity @a weapon.offhand with minecraft:lava_bucket");
			context.waitTicks(20);
			String water = server.computeOnServer(s -> {
				BlockPos at = HeldLights.litAt(s.getPlayerList().getPlayers().getFirst());
				var st = at == null ? null : s.overworld().getBlockState(at);
				return st == null ? "no light" : st.getBlock().getName().getString() + " " + st.getValue(LightBlock.LEVEL) + " waterlogged " + st.getValue(LightBlock.WATERLOGGED);
			});
			BlockPos wet = server.computeOnServer(s -> HeldLights.litAt(s.getPlayerList().getPlayers().getFirst()));
			server.runCommand("clear @a");
			context.waitTicks(10);
			String after = wet == null ? "none" : server.computeOnServer(s -> s.overworld().getBlockState(wet).getBlock().getName().getString());
			LOG.info("LightsTest: underwater: {}; after: {}", water, after);
			if (!water.equals("Light 15 waterlogged true") || !after.equals("Water")) failures.add("underwater the light should be waterlogged and the water come back: " + water + "; " + after);
		}
		if (!failures.isEmpty()) throw new AssertionError(String.join("; ", failures));
	}
}
