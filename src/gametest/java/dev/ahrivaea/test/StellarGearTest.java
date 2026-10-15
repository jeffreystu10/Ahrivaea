package dev.ahrivaea.test;

import dev.ahrivaea.stellar.Stellar;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Tries every Stellar power for real in a flat world, checks the results and takes screenshots. */
public class StellarGearTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		List<String> failures = new ArrayList<>();
		try (TestSingleplayerContext sp = context.worldBuilder().create()) {
			sp.getConnection().waitForChunksRender();
			var server = sp.getServer();
			BlockPos p = context.computeOnClient(c -> c.player.blockPosition());
			int x = p.getX(), y = p.getY(), z = p.getZ();
			server.runCommand("time set noon");
			server.runCommand("gamerule advance_time false");
			server.runCommand("gamerule spawn_mobs false");
			server.runCommand("gamerule ahrivaea:vanilla_mobs true"); // the sword test hits a zombie, which the creatures mod would otherwise replace
			server.runCommand("gamerule random_tick_speed 0"); // dry farmland can turn back to dirt between tilling and counting
			server.runCommand("gamemode survival @a");
			server.runCommand("effect give @a minecraft:resistance 9999 4 true");
			server.runCommand("effect give @a minecraft:saturation 9999 0 true");
			context.runOnClient(c -> {
				if (!c.gui.hud.isHidden()) c.gui.hud.toggle();
			});

			// the upgrade recipes exist
			for (String piece : new String[]{"pickaxe", "shovel", "axe", "hoe", "sword", "helmet", "chestplate", "leggings", "boots"}) {
				boolean ok = server.computeOnServer(s -> s.getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE,
						dev.ahrivaea.Ahrivaea.id("stellar_" + piece + "_smithing"))).isPresent());
				if (!ok) failures.add("no smithing recipe for stellar_" + piece);
			}

			// ---- pickaxe: a 5x5 stone wall, mine the middle, a 3x3 hole appears ----
			server.runCommand("fill %d %d %d %d %d %d minecraft:stone".formatted(x + 3, y, z + 5, x + 7, y + 4, z + 5));
			server.runCommand("item replace entity @a weapon.mainhand with ahrivaea:stellar_pickaxe");
			server.runCommand("tp @a %d %d %d 0 0".formatted(x + 5, y + 1, z + 2));
			context.waitTicks(5);
			BlockPos wallCenter = new BlockPos(x + 5, y + 2, z + 5);
			server.runOnServer(s -> player(s).gameMode.destroyBlock(wallCenter));
			context.waitTicks(5);
			int holes = server.computeOnServer(s -> {
				int n = 0;
				for (BlockPos q : BlockPos.betweenClosed(wallCenter.offset(-1, -1, 0), wallCenter.offset(1, 1, 0))) if (s.overworld().getBlockState(q).isAir()) n++;
				return n;
			});
			boolean edgeKept = server.computeOnServer(s -> s.overworld().getBlockState(wallCenter.offset(2, 0, 0)).is(Blocks.STONE));
			if (holes != 9 || !edgeKept) failures.add("pickaxe: expected a 3x3 hole with the rest of the wall standing; " + holes + " of 9 mined, edge kept " + edgeKept);
			server.runCommand("tp @a %.1f %d %.1f facing %d %d %d".formatted(x + 5.5, y + 2, z - 1.5, x + 5, y + 2, z + 5));
			context.waitTicks(10);
			context.takeScreenshot(TestScreenshotOptions.of("stellar_1_pickaxe_3x3").withSize(1600, 900));

			// ---- axe: a natural oak with player-placed logs against it, and a bare log pillar ----
			BlockPos trunk = new BlockPos(x - 8, y, z + 8);
			server.runCommand("place feature minecraft:oak %d %d %d".formatted(trunk.getX(), trunk.getY(), trunk.getZ()));
			// make room next to the trunk (a short tree can have leaves there), then
			server.runCommand("fill %d %d %d %d %d %d minecraft:air".formatted(trunk.getX() + 1, trunk.getY(), trunk.getZ(), trunk.getX() + 1, trunk.getY() + 1, trunk.getZ()));
			// two logs placed by the player (through the normal placing code), leaning on the trunk
			server.runOnServer(s -> {
				ServerPlayer pl = player(s);
				for (int i = 0; i < 2; i++) {
					BlockPos below = trunk.offset(1, i - 1, 0);
					ItemStack logs = new ItemStack(Items.OAK_LOG, 4);
					pl.setItemInHand(InteractionHand.MAIN_HAND, logs);
					logs.useOn(new UseOnContext(pl, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(below).add(0, 0.5, 0), Direction.UP, below, false)));
				}
			});
			// a pillar made with commands (like a build from before the mod): no natural leaves, so the axe must leave it
			server.runCommand("fill %d %d %d %d %d %d minecraft:oak_log".formatted(x - 14, y, z + 8, x - 14, y + 3, z + 8));
			server.runCommand("item replace entity @a weapon.mainhand with ahrivaea:stellar_axe");
			context.waitTicks(5);
			int trunkBefore = countLogs(server, trunk.offset(0, 0, 0), trunk.offset(0, 8, 0));
			int placedBefore = countLogs(server, trunk.offset(1, 0, 0), trunk.offset(1, 1, 0));
			String marks = server.computeOnServer(s -> dev.ahrivaea.stellar.PlacedLogs.isPlaced(s.overworld(), trunk.offset(1, 0, 0)) + "/"
					+ dev.ahrivaea.stellar.PlacedLogs.isPlaced(s.overworld(), trunk.offset(1, 1, 0)));

			server.runOnServer(s -> player(s).gameMode.destroyBlock(trunk));
			server.runOnServer(s -> player(s).gameMode.destroyBlock(new BlockPos(x - 14, y, z + 8)));
			context.waitTicks(5);
			int trunkAfter = countLogs(server, trunk, trunk.offset(0, 8, 0));
			int placedLeft = countLogs(server, trunk.offset(1, 0, 0), trunk.offset(1, 1, 0));
			int pillarLeft = countLogs(server, new BlockPos(x - 14, y, z + 8), new BlockPos(x - 14, y + 3, z + 8));
			if (trunkBefore < 4 || trunkAfter != 0) failures.add("axe: the tree should be felled (trunk logs before " + trunkBefore + ", after " + trunkAfter + ")");
			if (placedLeft != 2) failures.add("axe: the 2 player-placed logs should remain, found " + placedLeft + " (placed " + placedBefore + ", remembered as placed: " + marks + ")");
			if (pillarLeft != 3) failures.add("axe: the leafless pillar should only lose the one log broken, found " + pillarLeft + " of 4 left");
			server.runCommand("tp @a %.1f %d %.1f facing %d %d %d".formatted(x - 10.5, y + 4, z - 2.5, x - 10, y + 2, z + 8));
			context.waitTicks(10);
			context.takeScreenshot(TestScreenshotOptions.of("stellar_2_axe_tree_felled").withSize(1600, 900));

			// ---- hoe: till 3x3, then harvest and replant 3x3 ----
			BlockPos field = new BlockPos(x + 5, y - 1, z - 5);
			server.runCommand("item replace entity @a weapon.mainhand with ahrivaea:stellar_hoe");
			server.runCommand("tp @a %.1f %d %.1f facing %d %d %d".formatted(x + 5.5, y, z - 2.5, field.getX(), field.getY(), field.getZ()));
			context.waitTicks(10);
			context.runOnClient(c -> c.gameMode.useItemOn(c.player, InteractionHand.MAIN_HAND,
					new BlockHitResult(Vec3.atCenterOf(field).add(0, 0.5, 0), Direction.UP, field, false)));
			context.waitTicks(5);
			int farmland = server.computeOnServer(s -> {
				int n = 0;
				for (BlockPos q : BlockPos.betweenClosed(field.offset(-1, 0, -1), field.offset(1, 0, 1))) if (s.overworld().getBlockState(q).is(Blocks.FARMLAND)) n++;
				return n;
			});
			if (farmland != 9) failures.add("hoe: expected 9 farmland, got " + farmland);
			server.runCommand("fill %d %d %d %d %d %d minecraft:wheat[age=7]".formatted(field.getX() - 1, y, field.getZ() - 1, field.getX() + 1, y, field.getZ() + 1));
			context.waitTicks(5);
			BlockPos crop = field.above();
			context.runOnClient(c -> c.gameMode.useItemOn(c.player, InteractionHand.MAIN_HAND,
					new BlockHitResult(Vec3.atCenterOf(crop), Direction.UP, crop, false)));
			context.waitTicks(10);
			int replanted = server.computeOnServer(s -> {
				int n = 0;
				for (BlockPos q : BlockPos.betweenClosed(crop.offset(-1, 0, -1), crop.offset(1, 0, 1))) {
					var st = s.overworld().getBlockState(q);
					if (st.is(Blocks.WHEAT) && ((CropBlock) st.getBlock()).getAge(st) == 0) n++;
				}
				return n;
			});
			if (replanted != 9) failures.add("hoe: expected 9 replanted wheat, got " + replanted);
			context.takeScreenshot(TestScreenshotOptions.of("stellar_3_hoe_harvest").withSize(1600, 900));

			// ---- sword: hitting heals ----
			server.runCommand("item replace entity @a weapon.mainhand with ahrivaea:stellar_sword");
			context.waitTicks(25);
			float[] health = server.computeOnServer(s -> {
				ServerPlayer pl = player(s);
				var zombie = EntityTypes.ZOMBIE.create(s.overworld(), EntitySpawnReason.COMMAND);
				zombie.snapTo(pl.getX() + 1, pl.getY(), pl.getZ());
				zombie.setNoAi(true);
				s.overworld().addFreshEntity(zombie);
				pl.setHealth(10f);
				pl.resetAttackStrengthTicker();
				float before = pl.getHealth();
				return new float[]{before, 0};
			});
			context.waitTicks(25);
			float after = server.computeOnServer(s -> {
				ServerPlayer pl = player(s);
				var zombie = s.overworld().getEntities(EntityTypes.ZOMBIE, e -> true).getFirst();
				pl.attack(zombie);
				return pl.getHealth();
			});
			if (!(after > health[0])) failures.add("sword: health should go up after a hit (" + health[0] + " -> " + after + ")");

			// ---- armor: flight with the full set, gone without it ----
			for (String slot : new String[]{"head:helmet", "chest:chestplate", "legs:leggings", "feet:boots"}) {
				String[] s2 = slot.split(":");
				server.runCommand("item replace entity @a armor." + s2[0] + " with ahrivaea:stellar_" + s2[1]);
			}
			server.runCommand("item replace entity @a weapon.mainhand with ahrivaea:stellar_pickaxe");
			context.waitTicks(15);
			boolean canFly = server.computeOnServer(s -> player(s).getAbilities().mayfly);
			if (!canFly) failures.add("armor: the full set should allow flight");
			context.runOnClient(c -> {
				c.player.getAbilities().flying = true;
				c.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
			});
			server.runCommand("tp @a %.1f %d %.1f 180 10".formatted(x + 0.5, y + 3, z + 0.5));
			context.waitTicks(10);
			context.takeScreenshot(TestScreenshotOptions.of("stellar_4_armor_flight").withSize(1600, 900));
			context.runOnClient(c -> c.options.setCameraType(CameraType.FIRST_PERSON));
			server.runCommand("item replace entity @a armor.head with minecraft:air");
			context.waitTicks(15);
			boolean stillFly = server.computeOnServer(s -> player(s).getAbilities().mayfly);
			if (stillFly) failures.add("armor: flight should end when a piece comes off");
		}
		if (!failures.isEmpty()) throw new AssertionError(String.join("; ", failures));
	}

	private static ServerPlayer player(net.minecraft.server.MinecraftServer s) {
		return s.getPlayerList().getPlayers().getFirst();
	}

	private static int countLogs(net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server, BlockPos a, BlockPos b) {
		return server.computeOnServer(s -> {
			int n = 0;
			for (BlockPos q : BlockPos.betweenClosed(a, b)) if (s.overworld().getBlockState(q).is(net.minecraft.tags.BlockTags.LOGS)) n++;
			return n;
		});
	}
}
