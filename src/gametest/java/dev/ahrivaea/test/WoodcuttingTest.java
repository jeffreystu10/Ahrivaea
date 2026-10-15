package dev.ahrivaea.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.gui.screens.inventory.StonecutterScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Puts an oak log in a real stonecutter: it should offer the woodcutting recipes, and cut a stripped log. */
public class WoodcuttingTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		List<String> failures = new ArrayList<>();
		try (TestSingleplayerContext sp = context.worldBuilder().create()) {
			sp.getConnection().waitForChunksRender();
			context.getInput().resizeWindow(1600, 900);
			var server = sp.getServer();
			server.runCommand("gamerule spawn_mobs false");
			BlockPos pos = context.computeOnClient(c -> c.player.blockPosition());
			BlockPos cutter = pos.north(2);
			server.runCommand("setblock %d %d %d minecraft:stonecutter".formatted(cutter.getX(), cutter.getY(), cutter.getZ()));
			server.runCommand("clear @a");
			server.runCommand("item replace entity @a hotbar.0 with minecraft:oak_log 16");
			context.waitTicks(5);
			context.runOnClient(c -> c.gameMode.useItemOn(c.player, InteractionHand.MAIN_HAND,
					new BlockHitResult(Vec3.atCenterOf(cutter).add(0, 0.25, 0), Direction.UP, cutter, false)));
			context.waitFor(c -> c.gui.screen() instanceof StonecutterScreen, 100);
			// shift-click the logs into the stonecutter (hotbar slot 0 is menu slot 29)
			context.runOnClient(c -> c.gameMode.handleContainerInput(c.player.containerMenu.containerId, 29, 0, ContainerInput.QUICK_MOVE, c.player));
			context.waitTicks(5);
			int options = context.computeOnClient(c -> ((StonecutterMenu) c.player.containerMenu).getNumberOfVisibleRecipes());
			if (options != 14) failures.add("an oak log should offer 14 things to cut, got " + options);
			context.takeScreenshot(TestScreenshotOptions.of("woodcutting_1_oak_log"));
			// pick the stripped oak log and take it
			int index = server.computeOnServer(s -> {
				ServerPlayer p = s.getPlayerList().getPlayers().getFirst();
				if (!(p.containerMenu instanceof StonecutterMenu m)) return -1;
				var entries = m.getVisibleRecipes().entries();
				for (int i = 0; i < entries.size(); i++) {
					var holder = entries.get(i).recipe().recipe();
					if (holder.isPresent() && holder.get().id().identifier().getPath().equals("woodcutting/stripped_oak_log")) return i;
				}
				return -1;
			});
			if (index < 0) failures.add("the stonecutter should offer a stripped oak log");
			else {
				context.runOnClient(c -> c.gameMode.handleInventoryButtonClick(c.player.containerMenu.containerId, index));
				context.waitTicks(5);
				context.takeScreenshot(TestScreenshotOptions.of("woodcutting_2_stripped_log"));
				context.runOnClient(c -> c.gameMode.handleContainerInput(c.player.containerMenu.containerId, 1, 0, ContainerInput.QUICK_MOVE, c.player));
				context.waitTicks(5);
				int stripped = server.computeOnServer(s -> {
					var inv = s.getPlayerList().getPlayers().getFirst().getInventory();
					int n = 0;
					for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).is(Items.STRIPPED_OAK_LOG)) n += inv.getItem(i).getCount();
					return n;
				});
				if (stripped < 1) failures.add("cutting should give stripped oak logs, have " + stripped);
			}
			context.runOnClient(c -> c.gui.setScreen(null));
		}
		if (!failures.isEmpty()) throw new AssertionError(String.join("; ", failures));
	}
}
