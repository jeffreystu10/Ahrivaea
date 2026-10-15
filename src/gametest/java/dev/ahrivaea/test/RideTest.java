package dev.ahrivaea.test;

import dev.ahrivaea.horses.Feeds;
import dev.ahrivaea.horses.HorseSpeed;
import dev.ahrivaea.horses.HorsesMod;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.equine.Horse;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Fast Minecarts and Swift Horses for real: rides a minecart down a long powered line and round a corner at speed,
 * trains a horse with feed, rides a top-tier horse flat out, and breeds two trained horses.
 */
public class RideTest implements FabricClientGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("ahrivaea");

	@Override
	public void runTest(ClientGameTestContext context) {
		List<String> failures = new ArrayList<>();
		try (TestSingleplayerContext sp = context.worldBuilder()
				.adjustSettings(c -> c.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE)).create()) {
			sp.getConnection().waitForChunksRender();
			context.getInput().resizeWindow(1600, 900);
			TestServerContext server = sp.getServer();
			server.runCommand("time set noon");
			server.runCommand("gamerule doDaylightCycle false");
			server.runCommand("gamerule doMobSpawning false");
			var pos = context.computeOnClient(c -> c.player.blockPosition());
			int x0 = pos.getX(), y = pos.getY(), z0 = pos.getZ() + 3;
			server.runCommand("forceload add %d %d %d %d".formatted(x0 - 16, z0 - 64, x0 + 400, z0 + 24));
			context.waitTicks(20);

			// ---- minecart: 200 blocks of powered rail, 30 plain, then a corner and 40 blocks north ----
			int corner = x0 + 231;
			server.runCommand("fill %d %d %d %d %d %d minecraft:redstone_block".formatted(x0, y - 1, z0, x0 + 200, y - 1, z0));
			server.runCommand("fill %d %d %d %d %d %d minecraft:powered_rail[shape=east_west,powered=true]".formatted(x0, y, z0, x0 + 200, y, z0));
			server.runCommand("fill %d %d %d %d %d %d minecraft:stone".formatted(x0 + 201, y - 1, z0, corner, y - 1, z0));
			server.runCommand("fill %d %d %d %d %d %d minecraft:rail[shape=east_west]".formatted(x0 + 201, y, z0, corner - 1, y, z0));
			server.runCommand("setblock %d %d %d minecraft:rail[shape=north_west]".formatted(corner, y, z0));
			server.runCommand("fill %d %d %d %d %d %d minecraft:stone".formatted(corner, y - 1, z0 - 40, corner, y - 1, z0 - 1));
			server.runCommand("fill %d %d %d %d %d %d minecraft:rail[shape=north_south]".formatted(corner, y, z0 - 40, corner, y, z0 - 1));
			server.runCommand("summon minecraft:minecart %d %d %d".formatted(x0, y, z0));
			context.waitTicks(5);
			server.runCommand("ride @p mount @e[type=minecraft:minecart,limit=1]");
			server.runCommand("execute as @e[type=minecraft:minecart] run data merge entity @s {Motion:[0.4d,0.0d,0.0d]}");
			double cartTop = 0;
			double[] prev = null;
			List<double[]> track = new ArrayList<>();
			for (int t = 0; t < 240; t++) {
				double[] at = server.computeOnServer(s -> {
					var carts = s.overworld().getEntitiesOfClass(AbstractMinecart.class, new AABB(x0 - 10, y - 10, z0 - 60, x0 + 300, y + 10, z0 + 10));
					return carts.isEmpty() ? null : new double[]{carts.getFirst().getX(), carts.getFirst().getY(), carts.getFirst().getZ(), s.getTickCount()};
				});
				if (at == null) break;
				track.add(at);
				// speed over the last 20 server ticks, along x only (the long straight before the corner)
				if (track.size() > 20) {
					double[] a = track.get(track.size() - 21);
					if (at[3] > a[3] && at[0] < corner - 1) cartTop = Math.max(cartTop, (at[0] - a[0]) * 20 / (at[3] - a[3]));
				}
				if (t == 110) context.takeScreenshot(TestScreenshotOptions.of("minecart_1_full_speed"));
				prev = at;
				context.waitTick();
			}
			context.takeScreenshot(TestScreenshotOptions.of("minecart_2_after_corner"));
			LOG.info("RideTest minecart: top speed {} blocks/sec, ended at {}", String.format("%.1f", cartTop), prev == null ? "?" : String.format("%.1f %.1f %.1f", prev[0], prev[1], prev[2]));
			if (cartTop < 20) failures.add("the minecart should reach well over vanilla's 8 blocks/sec, got " + String.format("%.1f", cartTop));
			if (cartTop > 31) failures.add("the minecart should top out at 30 blocks/sec, got " + String.format("%.1f", cartTop));
			if (prev == null || Math.abs(prev[0] - (corner + 0.5)) > 1 || prev[2] > z0 - 10 || Math.abs(prev[1] - y) > 1)
				failures.add("the minecart should have gone round the corner and north along the rails, ended at " + (prev == null ? "?" : prev[0] + " " + prev[1] + " " + prev[2]));
			server.runCommand("ride @p dismount");
			server.runCommand("kill @e[type=minecraft:minecart]");

			// ---- horse: a fast-bred horse (vanilla's best speed), tamed and saddled ----
			int z1 = z0 + 10;
			server.runCommand("tp @p %d %d %d -90 0".formatted(x0, y, z1 - 2));
			server.runCommand("summon minecraft:horse %d %d %d".formatted(x0, y, z1));
			context.waitTicks(2);
			server.runOnServer(s -> {
				ServerPlayer p = s.getPlayerList().getPlayers().getFirst();
				Horse h = s.overworld().getEntitiesOfClass(Horse.class, new AABB(x0 - 3, y - 3, z1 - 3, x0 + 3, y + 3, z1 + 3)).getFirst();
				h.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.3375);
				h.setTamed(true);
				h.setOwner(p);
				h.setItemSlot(EquipmentSlot.SADDLE, new ItemStack(Items.SADDLE));
				h.setPersistenceRequired();
				p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Feeds.SWIFT, 8));
			});
			context.waitTicks(10);
			double tier0 = server.computeOnServer(s -> HorseSpeed.blocksPerSecond(horse(s, x0, y, z1)));
			// feed it by hand (right-click) once, then the other seven
			context.runOnClient(c -> {
				var h = c.level.getEntitiesOfClass(Horse.class, c.player.getBoundingBox().inflate(6)).getFirst();
				c.gameMode.interact(c.player, h, new EntityHitResult(h), InteractionHand.MAIN_HAND);
			});
			context.waitTicks(5);
			int fedByHand = server.computeOnServer(s -> HorseSpeed.of(horse(s, x0, y, z1)).fed());
			if (fedByHand != 1) failures.add("right-clicking a tamed horse with Swift Feed should feed it (fed = " + fedByHand + ")");
			server.runOnServer(s -> {
				ServerPlayer p = s.getPlayerList().getPlayers().getFirst();
				Horse h = horse(s, x0, y, z1);
				for (int i = 0; i < 7; i++) HorsesMod.feed(p, h, p.getMainHandItem());
			});
			int[] t1 = server.computeOnServer(s -> new int[]{HorseSpeed.of(horse(s, x0, y, z1)).tier(), s.getPlayerList().getPlayers().getFirst().getMainHandItem().getCount()});
			if (t1[0] != 1) failures.add("8 Swift Feed should make tier 1, got tier " + t1[0]);
			context.takeScreenshot(TestScreenshotOptions.of("horse_1_fed_tier_1"));

			// tier 5 (as if bred), then ride it flat out east
			server.runOnServer(s -> HorseSpeed.set(horse(s, x0, y, z1), new HorseSpeed(HorseSpeed.MAX_TIER, 0)));
			double expected = server.computeOnServer(s -> HorseSpeed.blocksPerSecond(horse(s, x0, y, z1)));
			server.runCommand("ride @p mount @e[type=minecraft:horse,limit=1,sort=nearest]");
			context.waitTicks(10);
			context.runOnClient(c -> {
				c.player.setYRot(-90);
				c.player.setXRot(10);
			});
			context.getInput().holdKey(o -> o.keyUp);
			List<double[]> ride = new ArrayList<>();
			double horseTop = 0;
			for (int t = 0; t < 140; t++) {
				double[] at = server.computeOnServer(s -> {
					ServerPlayer p = s.getPlayerList().getPlayers().getFirst();
					return p.getVehicle() == null ? null : new double[]{p.getVehicle().getX(), p.getVehicle().getZ(), s.getTickCount()};
				});
				if (at == null) break;
				ride.add(at);
				if (ride.size() > 20) {
					double[] a = ride.get(ride.size() - 21);
					if (at[2] > a[2]) horseTop = Math.max(horseTop, Math.hypot(at[0] - a[0], at[1] - a[1]) * 20 / (at[2] - a[2]));
				}
				if (t == 100) context.takeScreenshot(TestScreenshotOptions.of("horse_2_tier_5_full_speed"));
				context.waitTick();
			}
			context.getInput().releaseKey(o -> o.keyUp);
			LOG.info("RideTest horse: tier 0 {} blocks/sec, tier 5 expected {} and measured {} blocks/sec", String.format("%.1f", tier0), String.format("%.1f", expected), String.format("%.1f", horseTop));
			if (Math.abs(tier0 - 0.3375 * 1.5 * HorseSpeed.BLOCKS_PER_SECOND) > 0.2) failures.add("an untrained top horse should be 1.5x vanilla: " + tier0);
			if (horseTop <= 30) failures.add("a tier 5 horse of the best bloodline should outrun a minecart (30): measured " + String.format("%.1f", horseTop) + " blocks/sec");
			server.runCommand("ride @p dismount");

			// ---- breeding two tier 3 horses: the foal is tier 3 or 4 ----
			server.runCommand("summon minecraft:horse %d %d %d".formatted(x0, y, z0 + 20));
			server.runCommand("summon minecraft:horse %d %d %d".formatted(x0 + 2, y, z0 + 20));
			context.waitTicks(2);
			server.runOnServer(s -> {
				ServerPlayer p = s.getPlayerList().getPlayers().getFirst();
				for (Horse h : s.overworld().getEntitiesOfClass(Horse.class, new AABB(x0 - 3, y - 3, z0 + 17, x0 + 5, y + 3, z0 + 23))) {
					h.setTamed(true);
					h.setOwner(p);
					HorseSpeed.set(h, new HorseSpeed(3, 0));
					h.setInLove(p);
				}
			});
			for (int t = 0; t < 600 && server.computeOnServer(s -> foal(s, x0, y, z0) == null); t += 10) context.waitTicks(10);
			int foalTier = server.computeOnServer(s -> {
				Horse f = foal(s, x0, y, z0);
				return f == null ? -1 : HorseSpeed.of(f).tier();
			});
			LOG.info("RideTest foal of two tier 3 horses: tier {}", foalTier);
			if (foalTier != 3 && foalTier != 4) failures.add("a foal of two tier 3 horses should be tier 3 or 4, got " + foalTier);
		}
		if (!failures.isEmpty()) throw new AssertionError(String.join("; ", failures));
	}

	private static Horse horse(net.minecraft.server.MinecraftServer s, int x, int y, int z) {
		return s.overworld().getEntitiesOfClass(Horse.class, new AABB(x - 400, y - 10, z - 400, x + 400, y + 10, z + 400), h -> !h.isBaby() && h.getAttributeBaseValue(Attributes.MOVEMENT_SPEED) == 0.3375).getFirst();
	}

	private static Horse foal(net.minecraft.server.MinecraftServer s, int x, int y, int z) {
		var found = s.overworld().getEntitiesOfClass(Horse.class, new AABB(x - 30, y - 10, z, x + 30, y + 10, z + 40), Horse::isBaby);
		return found.isEmpty() ? null : found.getFirst();
	}
}
