package dev.ahrivaea.test;

import dev.ahrivaea.base.BaseCoreBlockEntity;
import dev.ahrivaea.base.Bases;
import dev.ahrivaea.base.Raids;
import dev.ahrivaea.creature.Influence;
import dev.ahrivaea.creature.Job;
import dev.ahrivaea.creature.Species;
import dev.ahrivaea.party.CaughtCreature;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Raids: a base guarded by five Defense workers fights off a raid, and a base with nobody to defend it gets its chest
 * robbed.
 */
public class RaidTest implements FabricClientGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("ahrivaea");

	@Override
	public void runTest(ClientGameTestContext context) {
		List<String> failures = new ArrayList<>();
		try (TestSingleplayerContext sp = context.worldBuilder()
				.adjustSettings(c -> c.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE)).create()) {
			sp.getConnection().waitForChunksRender();
			context.getInput().resizeWindow(1600, 900);
			var server = sp.getServer();
			server.runCommand("time set midnight"); // raiders give up at daybreak
			server.runCommand("gamerule advance_time false");
			server.runCommand("gamerule spawn_mobs false");
			BlockPos pos = context.computeOnClient(c -> c.player.blockPosition());
			int y = pos.getY();
			BlockPos guarded = pos.offset(0, 0, 10), open = pos.offset(0, 0, -50);
			for (BlockPos core : List.of(guarded, open)) {
				server.runCommand("setblock %d %d %d ahrivaea:base_core".formatted(core.getX(), y, core.getZ()));
				server.runCommand("setblock %d %d %d minecraft:chest".formatted(core.getX() + 1, y, core.getZ()));
			}
			context.waitTicks(10);
			String setup = server.computeOnServer(s -> {
				var level = s.overworld();
				var p = s.getPlayerList().getPlayers().getFirst();
				StringBuilder sb = new StringBuilder();
				String[] defender = firstWith(Job.DEFENSE), farmer = firstWith(Job.FARMING);
				for (BlockPos core : List.of(guarded, open)) {
					BaseCoreBlockEntity be = (BaseCoreBlockEntity) level.getBlockEntity(core);
					be.setOwner(p);
					((Container) level.getBlockEntity(core.offset(1, 0, 0))).setItem(0, new ItemStack(Items.DIRT, 64));
				}
				BaseCoreBlockEntity g = (BaseCoreBlockEntity) level.getBlockEntity(guarded);
				for (int i = 0; i < 5; i++) Bases.assign(p, g, new CaughtCreature(defender[0], Influence.byName(defender[1]), 50, 0, -1), () -> {});
				Bases.assign(p, (BaseCoreBlockEntity) level.getBlockEntity(open), new CaughtCreature(farmer[0], Influence.byName(farmer[1]), 20, 0, -1), () -> {});
				sb.append("defenders: ").append(defender[0]).append('/').append(defender[1]).append(", raiders: ");
				sb.append(Raids.start(level, g)).append(" and ").append(Raids.start(level, (BaseCoreBlockEntity) level.getBlockEntity(open)));
				return sb.toString();
			});
			LOG.info("RaidTest: {}", setup);
			server.runCommand("tp @p %d %d %d 0 55".formatted(guarded.getX(), y + 12, guarded.getZ() - 12));
			boolean shot = false;
			String guardedResult = "still going", openResult = "nothing stolen";
			for (int t = 0; t < 3600; t += 20) {
				context.waitTicks(20);
				if (!shot && t >= 200) {
					context.takeScreenshot(TestScreenshotOptions.of("raid_1_defenders_fighting"));
					shot = true;
				}
				guardedResult = server.computeOnServer(s -> Raids.at(guarded) == null ? "over: " + Raids.lastResult(guarded) : Raids.at(guarded).raiders().size() + " raiders left");
				openResult = server.computeOnServer(s -> {
					var r = Raids.at(open);
					int left = ((Container) s.overworld().getBlockEntity(open.offset(1, 0, 0))).getItem(0).getCount();
					return r == null ? "raid over, chest has " + left : "stolen " + r.stolen()[0] + ", chest has " + left;
				});
				if (guardedResult.startsWith("over") && !openResult.contains("has 64")) break;
			}
			LOG.info("RaidTest: guarded base {}; undefended base {}", guardedResult, openResult);
			if (!guardedResult.startsWith("over: defeated")) failures.add("the defenders should have beaten the raid: " + guardedResult);
			if (openResult.contains("has 64")) failures.add("raiders at an undefended base should steal from its chest: " + openResult);
			int knocked = server.computeOnServer(s -> {
				var be = (BaseCoreBlockEntity) s.overworld().getBlockEntity(guarded);
				return be.workers().size();
			});
			if (knocked != 5) failures.add("no defender should be lost (they get knocked out, not killed): " + knocked + " workers left");
		}
		if (!failures.isEmpty()) throw new AssertionError(String.join("; ", failures));
	}

	private static String[] firstWith(Job job) {
		for (Species s : Species.all()) {
			for (var e : s.forms().entrySet()) {
				var jobs = s.jobsFor(e.getKey());
				if (!jobs.isEmpty() && jobs.getFirst() == job) return new String[]{s.id(), e.getKey().getSerializedName()};
			}
		}
		return null;
	}
}
