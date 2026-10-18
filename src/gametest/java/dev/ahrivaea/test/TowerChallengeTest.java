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

			// a stand-in End portal room (a stone box with a doorway) away from the tower: sealed until it's rebuilt
			BlockPos room0 = origin.offset(-100, 1, 0), room1 = room0.offset(8, 5, 8);
			server.runCommand("fill %d %d %d %d %d %d minecraft:stone_bricks hollow".formatted(room0.getX(), room0.getY(), room0.getZ(), room1.getX(), room1.getY(), room1.getZ()));
			BlockPos door = room0.offset(4, 1, 0);
			server.runCommand("fill %d %d %d %d %d %d minecraft:air".formatted(door.getX(), door.getY(), door.getZ(), door.getX(), door.getY() + 1, door.getZ()));
			server.runCommand("setblock %d %d %d minecraft:end_portal_frame".formatted(room0.getX() + 4, room0.getY() + 1, room0.getZ() + 4));
			server.runOnServer(s -> Challenges.sealPortalRoom(s.overworld(), "coal",
					new net.minecraft.world.level.levelgen.structure.BoundingBox(room0.getX(), room0.getY(), room0.getZ(), room1.getX(), room1.getY(), room1.getZ())));
			String sealed = server.computeOnServer(s -> {
				var lv = s.overworld();
				var p0 = s.getPlayerList().getPlayers().getFirst();
				p0.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
				String out = (lv.getBlockState(door).is(net.minecraft.world.level.block.Blocks.REINFORCED_DEEPSLATE) ? "door sealed" : "door open")
						+ ", wall " + (Challenges.protectedFrom(lv, p0, room0.offset(0, 2, 3), false) != null ? "protected" : "open")
						+ ", frame " + (Challenges.protectedFrom(lv, p0, room0.offset(4, 1, 4), false) != null ? "protected" : "open");
				p0.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
				return out;
			});
			if (!sealed.equals("door sealed, wall protected, frame protected")) throw new AssertionError("an unfinished tower's End portal room should be sealed: " + sealed);
			server.runCommand("tp @a %.1f %d %.1f 0 10".formatted(door.getX() + 0.5, door.getY() + 1, door.getZ() - 5.5));
			context.waitTicks(20);
			context.takeScreenshot(TestScreenshotOptions.of("tower_portal_room_sealed").withSize(1600, 900));

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

			// unfinished, the tower and the land 64 blocks around it are protected from everyone in survival
			BlockPos near = origin.offset(-20, -1, -20);
			boolean placedBefore = placeDirt(context, server, near);
			if (placedBefore) throw new AssertionError("Nobody should be able to build next to an unfinished tower");

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
			// finished: the top donor (this player) claims it and can build around it
			String owner = server.computeOnServer(s -> Challenges.state(s, "coal").map(st -> st.controller()).orElse(""));
			String me = server.computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().getUUID().toString());
			if (!owner.equals(me)) throw new AssertionError("The top donor should claim the finished tower, owner is " + owner);
			if (!placeDirt(context, server, near)) throw new AssertionError("The tower's owner should be able to build next to it");

			// finished: the portal room's doorway is open, and nothing there is protected any more
			String opened = server.computeOnServer(s -> {
				var lv = s.overworld();
				var p0 = s.getPlayerList().getPlayers().getFirst();
				p0.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
				String out = (lv.getBlockState(door).isAir() ? "door open" : "door " + lv.getBlockState(door))
						+ ", frame " + (Challenges.protectedFrom(lv, p0, room0.offset(4, 1, 4), false) != null ? "protected" : "open");
				p0.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
				return out;
			});
			if (!opened.equals("door open, frame open")) throw new AssertionError("a finished tower's End portal room should be open: " + opened);
			server.runCommand("tp @a %.1f %d %.1f 0 10".formatted(door.getX() + 0.5, door.getY() + 1, door.getZ() - 5.5));
			context.waitTicks(20);
			context.takeScreenshot(TestScreenshotOptions.of("tower_portal_room_open").withSize(1600, 900));

			// 750 + 30 blocks x 9 = 1020 > 1000: 28 blocks are taken and 2 come back at once; when it's complete,
			// everything offered (300 coal, 28 coal blocks) comes back too
			int coalBack = context.computeOnClient(client -> count(client.player.getInventory(), Items.COAL));
			if (!complete || diamonds != 8 || blocksBack != 30 || coalBack != 300) {
				throw new AssertionError("Expected the tower complete, 8 diamonds for first place and all 30 coal blocks and 300 coal back; got "
						+ complete + ", " + diamonds + ", " + blocksBack + ", " + coalBack);
			}

			// the offering chest is now the owner's Base Core, which stays put
			String base = server.computeOnServer(s -> {
				var lv = s.overworld();
				var p0 = s.getPlayerList().getPlayers().getFirst();
				String out = lv.getBlockEntity(chest) instanceof dev.ahrivaea.base.BaseCoreBlockEntity core
						? "base of " + (p0.getUUID().equals(core.owner()) ? "me" : String.valueOf(core.owner())) : "no base: " + lv.getBlockState(chest);
				p0.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
				p0.gameMode.destroyBlock(chest);
				p0.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
				return out + (lv.getBlockEntity(chest) instanceof dev.ahrivaea.base.BaseCoreBlockEntity ? ", kept" : ", broken");
			});
			if (!base.equals("base of me, kept")) throw new AssertionError("a finished tower's chest should become its owner's base, and stay: " + base);
			server.runCommand("tp @a %.1f %.1f %.1f 0 30".formatted(chest.getX() + 0.5, chest.getY() + 1.0, chest.getZ() - 2.5));
			context.waitTicks(20);
			context.takeScreenshot(TestScreenshotOptions.of("tower_base_core").withSize(1600, 900));

			// guilds: when Bob owns the tower, this player may build there only while in Bob's guild (Bob is offline)
			String guild = server.computeOnServer(s -> {
				var lv = s.overworld();
				var p0 = s.getPlayerList().getPlayers().getFirst();
				var st = Challenges.state(s, "coal").orElseThrow();
				String mine = st.owner;
				st.owner = st.names.entrySet().stream().filter(e -> e.getValue().equals("Bob")).findFirst().orElseThrow().getKey();
				p0.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
				String before = Challenges.protectedFrom(lv, p0, near, false) != null ? "protected" : "open";
				String made = dev.ahrivaea.guilds.Guilds.create(p0, "Testers");
				var team = s.getScoreboard().getPlayerTeam("guild_testers");
				s.getScoreboard().addPlayerToTeam("Bob", team);
				String during = Challenges.protectedFrom(lv, p0, near, false) != null ? "protected" : "open";
				dev.ahrivaea.guilds.Guilds.remove(s, "Bob", "");
				String after = Challenges.protectedFrom(lv, p0, near, false) != null ? "protected" : "open";
				st.owner = mine;
				p0.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
				return before + ", " + made + ", " + during + ", " + after + ", friendly fire " + team.isAllowFriendlyFire()
						+ ", " + p0.getTeam().getName();
			});
			if (!guild.equals("protected, null, open, protected, friendly fire false, guild_testers"))
				throw new AssertionError("a tower owner's guild should be able to build there: " + guild);
			outside(context, server, origin);
			context.runOnClient(client -> client.player.connection.sendCommand("guild"));
			context.waitTicks(40);
			context.takeScreenshot(TestScreenshotOptions.of("guild_info").withSize(1600, 900));
		}
	}

	/** In survival, right-click dirt onto the ground at pos's top; returns whether a dirt block appeared above it. */
	private static boolean placeDirt(ClientGameTestContext context, TestServerContext server, BlockPos ground) {
		server.runCommand("gamemode survival @a");
		server.runCommand("setblock %d %d %d minecraft:stone".formatted(ground.getX(), ground.getY(), ground.getZ()));
		server.runCommand("setblock %d %d %d minecraft:air".formatted(ground.getX(), ground.getY() + 1, ground.getZ()));
		server.runCommand("tp @a %.1f %d %.1f 0 60".formatted(ground.getX() + 0.5, ground.getY() + 1, ground.getZ() - 1.5));
		server.runCommand("item replace entity @a weapon.mainhand with minecraft:dirt 4");
		context.waitTicks(10);
		context.runOnClient(client -> client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND,
				new BlockHitResult(Vec3.atCenterOf(ground).add(0, 0.5, 0), Direction.UP, ground, false)));
		context.waitTicks(10);
		boolean placed = server.computeOnServer(s -> s.overworld().getBlockState(ground.above()).is(net.minecraft.world.level.block.Blocks.DIRT));
		server.runCommand("gamemode creative @a");
		return placed;
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
