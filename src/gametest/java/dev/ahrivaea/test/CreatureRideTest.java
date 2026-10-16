package dev.ahrivaea.test;

import dev.ahrivaea.creature.CreatureEntity;
import dev.ahrivaea.creature.Mounts;
import dev.ahrivaea.party.CaughtCreature;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Riding creatures for real: a small one can't be saddled; a Horse creature is saddled and fed treats by hand,
 * ridden flat out at its tier's speed, and keeps its training when called back; a Pegasus takes off and flies; an
 * Orca dives with its rider, who keeps breathing.
 */
public class CreatureRideTest implements FabricClientGameTest {
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
			server.runCommand("weather clear");
			server.runCommand("gamerule advance_time false");
			server.runCommand("gamerule spawn_mobs false");
			var pos = context.computeOnClient(c -> c.player.blockPosition());
			int x0 = pos.getX(), y = pos.getY(), z0 = pos.getZ();
			server.runCommand("forceload add %d %d %d %d".formatted(x0 - 16, z0 - 16, x0 + 300, z0 + 80));
			server.runCommand("kill @e[type=ahrivaea:creature]");
			context.waitTicks(20);
			context.runOnClient(c -> {
				if (c.gui.hud.isHidden()) c.gui.hud.toggle(); // test worlds start with the HUD hidden
			});

			// ---- a Cat is too small to saddle ----
			server.runCommand("tp @p %d %d %d -90 20".formatted(x0, y, z0));
			server.runCommand("execute positioned %d %d %d run ahrivaea spawn cat none 20".formatted(x0 + 3, y, z0));
			context.waitTicks(5);
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				creature(s, "cat").tame(p);
				p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SADDLE));
			});
			context.waitTicks(5);
			rightClick(context, "cat");
			context.waitTicks(5);
			if (server.computeOnServer(s -> creature(s, "cat").hasSaddle())) failures.add("a Cat (small) shouldn't take a saddle");
			server.runCommand("kill @e[type=ahrivaea:creature]");

			// ---- a Horse creature: saddle it, feed it, ride it ----
			server.runCommand("execute positioned %d %d %d run ahrivaea spawn horse none 50".formatted(x0 + 3, y, z0));
			context.waitTicks(5);
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				creature(s, "horse").tame(p);
				p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SADDLE));
			});
			context.waitTicks(5);
			rightClick(context, "horse");
			context.waitTicks(5);
			if (!server.computeOnServer(s -> creature(s, "horse").hasSaddle())) failures.add("right-clicking your Horse creature with a saddle should saddle it");
			server.runOnServer(s -> player(s).setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Mounts.SWIFT_TREAT, 8)));
			context.waitTicks(2);
			rightClick(context, "horse");
			context.waitTicks(5);
			int fed = server.computeOnServer(s -> creature(s, "horse").getMountFed());
			if (fed != 1) failures.add("right-clicking a saddled creature with a Swift Treat should feed it (fed = " + fed + ")");
			// wrong treat for its tier is refused
			server.runOnServer(s -> player(s).setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Mounts.RACING_TREAT, 1)));
			context.waitTicks(2);
			rightClick(context, "horse");
			context.waitTicks(5);
			if (server.computeOnServer(s -> creature(s, "horse").getMountFed()) != 1) failures.add("a Racing Treat shouldn't count for a tier 0 creature");
			double tier0 = server.computeOnServer(s -> (double) creature(s, "horse").getTopSpeed());
			// fed to tier 3; its Speed (63 at level 50) makes that tier 4
			server.runOnServer(s -> creature(s, "horse").setMountTraining(3, 0));
			int[] tiers = server.computeOnServer(s -> new int[]{creature(s, "horse").getMountTier(), creature(s, "horse").getRideTier()});
			double expected = server.computeOnServer(s -> (double) creature(s, "horse").getTopSpeed());
			if (tiers[0] != 3 || tiers[1] != 4) failures.add("a Horse (Speed 63) fed to tier 3 should ride at tier 4, got fed " + tiers[0] + " ride " + tiers[1]);
			if (Math.abs(expected - (5 + 0.09 * 63) * Mounts.MULTIPLIER[4]) > 0.2) failures.add("tier 4 top speed should be (5 + 0.09 x 63) x 2.54, got " + expected);
			// empty hand: get on
			server.runOnServer(s -> player(s).setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY));
			context.waitTicks(2);
			rightClick(context, "horse");
			context.waitTicks(10);
			if (!server.computeOnServer(s -> player(s).getVehicle() instanceof CreatureEntity)) failures.add("right-clicking a saddled creature with an empty hand should ride it");
			context.runOnClient(c -> {
				c.player.setYRot(-90);
				c.player.setXRot(10);
			});
			double top = gallop(context, server, 140, "creature_ride_1_horse_full_speed");
			LOG.info("CreatureRideTest horse: tier 0 {} blocks/sec, tier 4 expected {} and measured {}", String.format("%.1f", tier0), String.format("%.1f", expected), String.format("%.1f", top));
			if (top < expected * 0.8 || top > expected * 1.2) failures.add("a tier 4 Horse creature should run about " + String.format("%.1f", expected) + " blocks/sec, measured " + String.format("%.1f", top));
			server.runCommand("ride @p dismount");
			context.waitTicks(5);
			CaughtCreature back = server.computeOnServer(s -> creature(s, "horse").toCaught());
			if (back.mountTier() != 3 || !back.saddled()) failures.add("calling a creature back should keep its training and saddle: " + back);
			server.runCommand("kill @e[type=ahrivaea:creature]");

			// ---- a Pegasus: jump to take off, fly up where you look ----
			var here = context.computeOnClient(c -> c.player.blockPosition());
			int x1 = here.getX(), z1 = here.getZ();
			server.runCommand("tp @p %d %d %d -90 0".formatted(x1, y, z1));
			server.runCommand("execute positioned %d %d %d run ahrivaea spawn pegasus fae 50".formatted(x1 + 4, y, z1));
			context.waitTicks(5);
			server.runOnServer(s -> {
				CreatureEntity e = creature(s, "pegasus");
				e.tame(player(s));
				e.setSaddle(true);
			});
			server.runCommand("ride @p mount @e[type=ahrivaea:creature,limit=1,sort=nearest]");
			context.waitTicks(10);
			double groundY = server.computeOnServer(s -> player(s).getVehicle() == null ? -999 : player(s).getVehicle().getY());
			context.runOnClient(c -> {
				c.player.setYRot(-90);
				c.player.setXRot(-25);
			});
			context.getInput().holdKey(o -> o.keyJump);
			context.waitTicks(6);
			context.getInput().releaseKey(o -> o.keyJump);
			gallop(context, server, 80, "creature_ride_2_pegasus_flying");
			double airY = server.computeOnServer(s -> player(s).getVehicle() == null ? -999 : player(s).getVehicle().getY());
			LOG.info("CreatureRideTest pegasus: from y {} to y {}", groundY, airY);
			if (airY < groundY + 8) failures.add("a ridden Pegasus should take off and climb where the rider looks: from y " + groundY + " to " + airY);
			// look down and land
			context.runOnClient(c -> c.player.setXRot(70));
			context.getInput().holdKey(o -> o.keyUp);
			context.waitTicks(80);
			context.getInput().releaseKey(o -> o.keyUp);
			context.waitTicks(10);
			boolean stillRiding = server.computeOnServer(s -> player(s).getVehicle() instanceof CreatureEntity);
			double landedY = server.computeOnServer(s -> player(s).getVehicle() == null ? -999 : player(s).getVehicle().getY());
			if (!stillRiding || landedY > groundY + 2) failures.add("looking down should bring the Pegasus back to the ground with its rider (riding " + stillRiding + ", y " + landedY + ")");
			server.runCommand("ride @p dismount");
			server.runCommand("kill @e[type=ahrivaea:creature]");

			// ---- an Orca in a lake: dive where you look, the rider keeps breathing ----
			int lx = x0 + 20, lz = z0 + 40;
			server.runCommand("fill %d %d %d %d %d %d minecraft:water".formatted(lx - 6, y - 12, lz - 6, lx + 60, y - 1, lz + 6));
			server.runCommand("tp @p %d %d %d -90 0".formatted(lx, y, lz));
			server.runCommand("execute positioned %d %d %d run ahrivaea spawn orca water 50".formatted(lx + 2, y - 3, lz));
			context.waitTicks(10);
			server.runOnServer(s -> {
				CreatureEntity e = creature(s, "orca");
				e.tame(player(s));
				e.setSaddle(true);
			});
			server.runCommand("ride @p mount @e[type=ahrivaea:creature,limit=1,sort=nearest]");
			context.waitTicks(10);
			double surfaceY = server.computeOnServer(s -> player(s).getVehicle() == null ? -999 : player(s).getVehicle().getY());
			context.runOnClient(c -> {
				c.player.setYRot(-90);
				c.player.setXRot(30);
			});
			double swim = gallop(context, server, 60, "creature_ride_3_orca_diving");
			double[] dive = server.computeOnServer(s -> {
				ServerPlayer p = player(s);
				return new double[]{p.getVehicle() == null ? -999 : p.getVehicle().getY(), p.hasEffect(MobEffects.WATER_BREATHING) ? 1 : 0};
			});
			LOG.info("CreatureRideTest orca: from y {} to y {}, {} blocks/sec, water breathing {}", surfaceY, dive[0], String.format("%.1f", swim), dive[1]);
			if (dive[0] > surfaceY - 3) failures.add("a ridden Orca should dive where the rider looks: from y " + surfaceY + " to " + dive[0]);
			if (dive[1] == 0) failures.add("riding a swimming creature should keep the rider breathing underwater");
			if (swim < 8) failures.add("a ridden Orca should swim at speed, measured " + String.format("%.1f", swim) + " blocks/sec");
			server.runCommand("ride @p dismount");
		}
		if (!failures.isEmpty()) throw new AssertionError(String.join("; ", failures));
	}

	/** Holds forward for some ticks and returns the best speed over 20 ticks (in 3D), taking a screenshot near the end. */
	private static double gallop(ClientGameTestContext context, TestServerContext server, int ticks, String shot) {
		context.getInput().holdKey(o -> o.keyUp);
		List<double[]> ride = new ArrayList<>();
		double top = 0;
		for (int t = 0; t < ticks; t++) {
			double[] at = server.computeOnServer(s -> {
				var v = player(s).getVehicle();
				return v == null ? null : new double[]{v.getX(), v.getY(), v.getZ(), s.getTickCount()};
			});
			if (at == null) break;
			ride.add(at);
			if (ride.size() > 20) {
				double[] a = ride.get(ride.size() - 21);
				if (at[3] > a[3]) top = Math.max(top, Math.sqrt(Math.pow(at[0] - a[0], 2) + Math.pow(at[1] - a[1], 2) + Math.pow(at[2] - a[2], 2)) * 20 / (at[3] - a[3]));
			}
			if (t == ticks - 20) context.takeScreenshot(TestScreenshotOptions.of(shot).withSize(1600, 900));
			context.waitTick();
		}
		context.getInput().releaseKey(o -> o.keyUp);
		return top;
	}

	private static void rightClick(ClientGameTestContext context, String species) {
		context.runOnClient(c -> {
			var found = c.level.getEntitiesOfClass(CreatureEntity.class, c.player.getBoundingBox().inflate(10), e -> e.getSpeciesId().equals(species));
			if (!found.isEmpty()) c.gameMode.interact(c.player, found.getFirst(), new EntityHitResult(found.getFirst()), InteractionHand.MAIN_HAND);
		});
	}

	private static ServerPlayer player(MinecraftServer s) {
		return s.getPlayerList().getPlayers().getFirst();
	}

	private static CreatureEntity creature(MinecraftServer s, String species) {
		ServerPlayer p = player(s);
		return s.overworld().getEntitiesOfClass(CreatureEntity.class, new AABB(p.blockPosition()).inflate(400, 64, 400), e -> e.getSpeciesId().equals(species)).getFirst();
	}
}
