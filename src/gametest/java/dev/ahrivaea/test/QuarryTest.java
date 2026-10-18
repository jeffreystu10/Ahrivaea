package dev.ahrivaea.test;

import dev.ahrivaea.base.BaseCoreBlockEntity;
import dev.ahrivaea.base.Bases;
import dev.ahrivaea.base.PlacedBlocks;
import dev.ahrivaea.base.Quarry;
import dev.ahrivaea.creature.CreatureEntity;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * A quarry: a chunk-sized hill of stone (with iron ore, a lava pocket, water, a spawner, a gap in its side and a
 * block a player placed) and a Mining Post on top with a chest. Nine miners cut the staircase and clear it layer by
 * layer into the chest, leaving the wall, the spawner and the player's block. When the chest is full they stop and
 * say so; a second chest gets them going again.
 */
public class QuarryTest implements FabricClientGameTest {
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
			BlockPos pos = context.computeOnClient(c -> c.player.blockPosition());
			int y = pos.getY();
			// the quarry chunk: two chunks east of the player's; the base core in the player's chunk
			int bx = ((pos.getX() >> 4) + 2) * 16, bz = (pos.getZ() >> 4) * 16;
			BlockPos core = new BlockPos(pos.getX(), y, pos.getZ() + 3);
			BlockPos post = new BlockPos(bx + 8, y + 4, bz + 8), chest = post.east();
			server.runCommand("fill %d %d %d %d %d %d minecraft:stone".formatted(bx, y, bz, bx + 15, y + 3, bz + 15));
			server.runCommand("fill %d %d %d %d %d %d minecraft:iron_ore".formatted(bx + 5, y + 1, bz + 5, bx + 6, y + 2, bz + 6));
			server.runCommand("setblock %d %d %d minecraft:lava".formatted(bx + 10, y + 2, bz + 10));
			server.runCommand("setblock %d %d %d minecraft:water".formatted(bx + 3, y + 3, bz + 11));
			BlockPos spawner = new BlockPos(bx + 12, y, bz + 4), gap = new BlockPos(bx, y + 1, bz + 7), placed = new BlockPos(bx + 7, y, bz + 12);
			server.runCommand("setblock %d %d %d minecraft:spawner".formatted(spawner.getX(), spawner.getY(), spawner.getZ()));
			server.runCommand("setblock %d %d %d minecraft:lava".formatted(gap.getX(), gap.getY(), gap.getZ()));
			server.runCommand("setblock %d %d %d minecraft:oak_planks".formatted(placed.getX(), placed.getY(), placed.getZ()));
			server.runCommand("setblock %d %d %d ahrivaea:base_core".formatted(core.getX(), core.getY(), core.getZ()));
			server.runCommand("setblock %d %d %d ahrivaea:mining_post".formatted(post.getX(), post.getY(), post.getZ()));
			server.runCommand("setblock %d %d %d minecraft:chest".formatted(chest.getX(), chest.getY(), chest.getZ()));
			context.waitTicks(10);
			String assigned = server.computeOnServer(s -> {
				ServerLevel level = s.overworld();
				var p = s.getPlayerList().getPlayers().getFirst();
				BaseCoreBlockEntity be = (BaseCoreBlockEntity) level.getBlockEntity(core);
				be.setOwner(p);
				be.setBaseLevel(3);
				PlacedBlocks.placed(level, placed);
				String miner = null;
				for (Species sp2 : Species.all()) for (var f : sp2.forms().keySet()) if (miner == null && sp2.jobsFor(f).contains(Job.MINING)) miner = sp2.id() + "/" + f.getSerializedName();
				if (miner == null) return "no miner";
				String[] m = miner.split("/");
				StringBuilder sb = new StringBuilder(miner);
				for (int i = 0; i < 9; i++) {
					String why = Bases.assign(p, be, new CaughtCreature(m[0], Influence.byName(m[1]), 50, 0, -1), () -> {});
					if (why != null) sb.append(" ").append(why);
				}
				return sb.toString();
			});
			LOG.info("QuarryTest: miners {}", assigned);

			// let them dig for up to 4 minutes, checking as they go
			String state = "";
			for (int t = 0; t < 4800; t += 200) {
				context.waitTicks(200);
				state = server.computeOnServer(s -> describe(s.overworld(), core, post));
				LOG.info("QuarryTest: {}", state);
				if (state.contains("layer y=" + (y - 1)) || state.contains("Done")) break;
			}
			String check = server.computeOnServer(s -> {
				ServerLevel level = s.overworld();
				Container c = HopperBlockEntity.getContainerAt(level, chest);
				int stone = 0, iron = 0;
				for (int i = 0; i < c.getContainerSize(); i++) {
					if (c.getItem(i).is(Items.STONE)) stone += c.getItem(i).getCount();
					if (c.getItem(i).is(Items.IRON_ORE)) iron += c.getItem(i).getCount();
				}
				int fluids = 0, wallHoles = 0, solidTop = 0;
				for (BlockPos p : BlockPos.betweenClosed(bx, y, bz, bx + 15, y + 3, bz + 15)) {
					boolean wall = p.getX() == bx || p.getX() == bx + 15 || p.getZ() == bz || p.getZ() == bz + 15;
					var st = level.getBlockState(p);
					if (!st.getFluidState().isEmpty()) fluids++;
					if (wall && st.isAir()) wallHoles++;
					if (!wall && p.getY() == y + 3 && !st.isAir() && !Quarry.isStep(post, p)) solidTop++;
				}
				return "stone " + stone + ", iron ore " + iron + ", spawner " + level.getBlockState(spawner).is(Blocks.SPAWNER)
						+ ", planks " + level.getBlockState(placed).is(Blocks.OAK_PLANKS) + ", gap " + level.getBlockState(gap).getBlock().getName().getString()
						+ ", fluids " + fluids + ", wall holes " + wallHoles + ", top layer left " + solidTop;
			});
			LOG.info("QuarryTest: after digging: {}", check);
			if (!check.matches("stone \\d{3,}.*") || !check.contains("iron ore 8") || !check.contains("spawner true") || !check.contains("planks true")
					|| !check.contains("fluids 0") || !check.contains("wall holes 0") || !check.contains("top layer left 0"))
				failures.add("the quarry should be dug into the chest, keeping the wall, spawner and placed block: " + check + "; " + state);

			server.runCommand("tp @a %d %d %d -90 70".formatted(bx - 3, y + 14, bz + 8));
			context.runOnClient(c -> c.player.getAbilities().flying = true);
			context.waitTicks(30);
			context.takeScreenshot(TestScreenshotOptions.of("quarry_1_dug").withSize(1600, 900));

			// a full chest stops them, with a message; a second chest starts them again
			server.runOnServer(s -> {
				Container c = HopperBlockEntity.getContainerAt(s.overworld(), chest);
				for (int i = 0; i < c.getContainerSize(); i++) c.setItem(i, new ItemStack(Items.BEDROCK, 64));
			});
			context.waitTicks(200);
			String stopped = server.computeOnServer(s -> describe(s.overworld(), core, post));
			long minedBefore = server.computeOnServer(s -> ((BaseCoreBlockEntity) s.overworld().getBlockEntity(core)).quarry(post).mined);
			LOG.info("QuarryTest: with a full chest: {}", stopped);
			if (!stopped.contains("chests by the post are full") || !stopped.contains("needs another chest")) failures.add("a full chest should stop the miners: " + stopped);
			server.runCommand("setblock %d %d %d minecraft:barrel".formatted(post.getX(), post.getY(), post.getZ() - 1));
			context.waitTicks(300);
			long minedAfter = server.computeOnServer(s -> ((BaseCoreBlockEntity) s.overworld().getBlockEntity(core)).quarry(post).mined);
			LOG.info("QuarryTest: with a barrel added, {} -> {} blocks dug", minedBefore, minedAfter);
			if (minedAfter <= minedBefore) failures.add("a new chest should get the miners going again: " + minedBefore + " -> " + minedAfter);

			server.runOnServer(s -> {
				for (var e : s.overworld().getEntitiesOfClass(CreatureEntity.class, new net.minecraft.world.phys.AABB(core).inflate(80), e -> true)) e.discard();
			});
			context.waitTicks(5);
		}
		if (!failures.isEmpty()) throw new AssertionError(String.join("; ", failures));
	}

	private static String describe(ServerLevel level, BlockPos core, BlockPos post) {
		BaseCoreBlockEntity be = (BaseCoreBlockEntity) level.getBlockEntity(core);
		Quarry q = be.quarry(post);
		StringBuilder sb = new StringBuilder(q.describe(level, post)).append("; dug ").append(q.mined).append("; workers:");
		for (var w : be.workers()) {
			String st = level.getEntity(w.entity()) instanceof CreatureEntity e && e.brain() != null ? e.brain().status() : "?";
			sb.append(" [").append(st).append("]");
		}
		return sb.toString();
	}
}
