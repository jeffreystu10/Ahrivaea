package dev.ahrivaea.test;

import dev.ahrivaea.challenge.Challenges;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Plays the tower challenge for real: places the tower, puts coal (and some dirt) in the offering chest
 * through the chest screen, checks it was counted and the dirt came back, builds the remaining stages and
 * checks the rewards. Takes a screenshot at each step.
 */
public class TowerChallengeTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext sp = context.worldBuilder()
				.adjustSettings(c -> c.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE)).create()) {
			sp.getConnection().waitForChunksRender();
			TestServerContext server = sp.getServer();
			BlockPos p = context.computeOnClient(client -> client.player.blockPosition());
			BlockPos origin = p.offset(-5, -1, 8);
			BlockPos chest = origin.offset(5, 1, 5);
			server.runCommand("time set noon");
			server.runCommand("gamerule advance_time false");
			server.runCommand("gamerule spawn_mobs false");
			server.runCommand("execute positioned %d %d %d run challenge place coal".formatted(origin.getX(), origin.getY(), origin.getZ()));
			context.runOnClient(client -> {
				if (!client.gui.hud.isHidden()) client.gui.hud.toggle();
			});
			context.waitTicks(20);
			outside(context, server, origin);
			context.takeScreenshot(TestScreenshotOptions.of("tower_0_ruined").withSize(1600, 900));

			// a real offering: 300 coal and 10 dirt through the chest screen
			server.runCommand("clear @a");
			server.runCommand("give @a minecraft:coal 300");
			server.runCommand("give @a minecraft:dirt 10");
			server.runCommand("effect give @a minecraft:resistance 600 4 true");
			server.runCommand("tp @a %.1f %.1f %.1f 0 30".formatted(chest.getX() + 0.5, chest.getY() + 1.0, chest.getZ() - 2.5));
			context.waitTicks(20);
			context.runOnClient(client -> client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND,
					new BlockHitResult(Vec3.atCenterOf(chest), Direction.NORTH, chest, false)));
			context.waitForScreen(ContainerScreen.class);
			context.runOnClient(client -> {
				var menu = client.player.containerMenu;
				for (int i = 27; i < menu.slots.size(); i++) {
					var item = menu.slots.get(i).getItem();
					if (item.is(Items.COAL) || item.is(Items.DIRT)) {
						client.gameMode.handleContainerInput(menu.containerId, i, 0, ContainerInput.QUICK_MOVE, client.player);
					}
				}
			});
			context.waitTicks(5);
			context.takeScreenshot(TestScreenshotOptions.of("tower_offering_chest").withSize(1600, 900));
			context.runOnClient(client -> client.player.closeContainer());
			context.waitTicks(10);

			int total = server.computeOnServer(s -> Challenges.state(s, "coal").map(st -> st.total).orElse(-1));
			int coalLeft = context.computeOnClient(client -> count(client.player.getInventory(), Items.COAL));
			int dirtBack = context.computeOnClient(client -> count(client.player.getInventory(), Items.DIRT));
			if (total != 300 || coalLeft != 0 || dirtBack != 10) {
				throw new AssertionError("Expected 300 coal counted, none left and the dirt back; got total " + total + ", coal " + coalLeft + ", dirt " + dirtBack);
			}

			outside(context, server, origin);
			context.takeScreenshot(TestScreenshotOptions.of("tower_1_quarter").withSize(1600, 900));
			server.runCommand("challenge credit coal Bob 250");
			context.waitTicks(10);
			context.takeScreenshot(TestScreenshotOptions.of("tower_2_half").withSize(1600, 900));
			server.runCommand("challenge credit coal Cara 200");
			context.waitTicks(10);
			context.takeScreenshot(TestScreenshotOptions.of("tower_3_three_quarters").withSize(1600, 900));

			// finish it with a second real offering, then look at the celebration and the leaderboard
			server.runCommand("give @a minecraft:coal_block 30");
			server.runCommand("tp @a %.1f %.1f %.1f 0 30".formatted(chest.getX() + 0.5, chest.getY() + 1.0, chest.getZ() - 2.5));
			context.waitTicks(10);
			context.runOnClient(client -> client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND,
					new BlockHitResult(Vec3.atCenterOf(chest), Direction.NORTH, chest, false)));
			context.waitForScreen(ContainerScreen.class);
			context.runOnClient(client -> {
				var menu = client.player.containerMenu;
				for (int i = 27; i < menu.slots.size(); i++) {
					if (menu.slots.get(i).getItem().is(Items.COAL_BLOCK)) {
						client.gameMode.handleContainerInput(menu.containerId, i, 0, ContainerInput.QUICK_MOVE, client.player);
					}
				}
				client.player.closeContainer();
			});
			context.waitTicks(5);
			outside(context, server, origin);
			context.runOnClient(client -> client.gui.hud.toggle());
			context.waitTicks(25);
			context.takeScreenshot(TestScreenshotOptions.of("tower_4_complete").withSize(1600, 900));
			context.runOnClient(client -> client.player.connection.sendCommand("challenge"));
			context.waitTicks(80);
			context.takeScreenshot(TestScreenshotOptions.of("tower_leaderboard").withSize(1600, 900));

			boolean complete = server.computeOnServer(s -> Challenges.state(s, "coal").map(st -> st.complete).orElse(false));
			int diamonds = context.computeOnClient(client -> count(client.player.getInventory(), Items.DIAMOND));
			int blocksBack = context.computeOnClient(client -> count(client.player.getInventory(), Items.COAL_BLOCK));
			// 750 + 30 blocks x 9 = 1020 > 1000: 28 blocks are needed, 2 come back
			if (!complete || diamonds != 8 || blocksBack != 2) {
				throw new AssertionError("Expected the tower complete, 8 diamonds for first place and 2 coal blocks back; got " + complete + ", " + diamonds + ", " + blocksBack);
			}
		}
	}

	private static int count(net.minecraft.world.Container inv, net.minecraft.world.item.Item item) {
		int n = 0;
		for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).is(item)) n += inv.getItem(i).getCount();
		return n;
	}

	private static void outside(ClientGameTestContext context, TestServerContext server, BlockPos origin) {
		server.runCommand("tp @a %.1f %.1f %.1f 0 22".formatted(origin.getX() + 5.5, origin.getY() + 12.0, origin.getZ() - 20.0));
		context.runOnClient(client -> client.player.getAbilities().flying = true);
		context.waitTicks(15);
	}
}
