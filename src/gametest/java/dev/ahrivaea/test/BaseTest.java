package dev.ahrivaea.test;

import dev.ahrivaea.base.BaseCoreBlockEntity;
import dev.ahrivaea.base.Bases;
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
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * A working base: a Base Core with a farm, a tree, a furnace of raw iron (no fuel), a Mining Post, a pond, chests and a
 * sapling, and one worker for each job. After a while the wheat should be harvested and replanted, the tree felled
 * and replanted, the iron smelted, mined and fished things made, and loose items carried to the chests.
 */
public class BaseTest implements FabricClientGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("ahrivaea");
	private static final Job[] JOBS = {Job.FARMING, Job.LOGGING, Job.MINING, Job.SMELTING, Job.TRANSPORT, Job.FISHING, Job.GROWTH, Job.RANCHING,
			Job.BREWING, Job.ENCHANTING, Job.REDSTONE, Job.CRAFTING, Job.ICE, Job.LIGHTING};

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
			BlockPos core = pos.offset(0, 0, 8);
			int cx = core.getX(), cz = core.getZ();
			// the base
			server.runCommand("setblock %d %d %d ahrivaea:base_core".formatted(cx, y, cz));
			server.runCommand("setblock %d %d %d minecraft:chest".formatted(cx + 2, y, cz));
			server.runCommand("setblock %d %d %d minecraft:chest".formatted(cx + 2, y, cz + 2));
			server.runCommand("setblock %d %d %d minecraft:furnace".formatted(cx - 2, y, cz));
			// a farm of ripe wheat with water
			server.runCommand("fill %d %d %d %d %d %d minecraft:farmland[moisture=7]".formatted(cx + 4, y - 1, cz + 3, cx + 8, y - 1, cz + 5));
			server.runCommand("setblock %d %d %d minecraft:water".formatted(cx + 6, y - 1, cz + 4));
			server.runCommand("fill %d %d %d %d %d %d minecraft:wheat[age=7] replace minecraft:air".formatted(cx + 4, y, cz + 3, cx + 8, y, cz + 5));
			server.runCommand("setblock %d %d %d minecraft:air".formatted(cx + 6, y, cz + 4));
			// a tree, a sapling, a mining post with a chest, a pond
			server.runCommand("place feature minecraft:oak %d %d %d".formatted(cx - 8, y, cz + 6));
			server.runCommand("setblock %d %d %d minecraft:oak_sapling".formatted(cx + 6, y, cz - 6));
			// the mining post's quarry is its own chunk, two chunks away so the base above isn't dug out
			server.runCommand("setblock %d %d %d ahrivaea:mining_post".formatted(((cx >> 4) + 2) * 16 + 8, y, ((cz >> 4) + 2) * 16 + 8));
			server.runCommand("setblock %d %d %d minecraft:chest".formatted(((cx >> 4) + 2) * 16 + 9, y, ((cz >> 4) + 2) * 16 + 8));
			server.runCommand("fill %d %d %d %d %d %d minecraft:water".formatted(cx - 8, y - 1, cz - 8, cx - 6, y - 1, cz - 6));
			// phase 2 workstations: a brewing stand, an enchanting table, a Creature Dynamo by a lamp, a crafter, torches
			server.runCommand("setblock %d %d %d minecraft:brewing_stand".formatted(cx - 3, y, cz - 2));
			server.runCommand("setblock %d %d %d minecraft:enchanting_table".formatted(cx + 3, y, cz - 3));
			server.runCommand("setblock %d %d %d ahrivaea:creature_dynamo".formatted(cx - 4, y, cz + 2));
			server.runCommand("setblock %d %d %d minecraft:redstone_lamp".formatted(cx - 5, y, cz + 2));
			server.runCommand("setblock %d %d %d minecraft:crafter".formatted(cx + 4, y, cz - 1));
			server.runCommand("setblock %d %d %d minecraft:barrel".formatted(cx - 2, y, cz - 4));
			context.waitTicks(10);
			int treeLogs = server.computeOnServer(s -> countLogs(s.overworld(), new BlockPos(cx - 8, y, cz + 6)));
			LOG.info("BaseTest: tree has {} logs", treeLogs);

			// a furnace of raw iron with no fuel, and one worker per job
			String assigned = server.computeOnServer(s -> {
				ServerLevel level = s.overworld();
				var p = s.getPlayerList().getPlayers().getFirst();
				BaseCoreBlockEntity be = (BaseCoreBlockEntity) level.getBlockEntity(core);
				be.setOwner(p);
				be.setBaseLevel(5);
				((AbstractFurnaceBlockEntity) level.getBlockEntity(new BlockPos(cx - 2, y, cz))).setItem(0, new ItemStack(Items.RAW_IRON, 8));
				var stand = (net.minecraft.world.level.block.entity.BrewingStandBlockEntity) level.getBlockEntity(new BlockPos(cx - 3, y, cz - 2));
				for (int i = 0; i < 3; i++) stand.setItem(i, net.minecraft.world.item.alchemy.PotionContents.createItemStack(Items.POTION, net.minecraft.world.item.alchemy.Potions.WATER));
				stand.setItem(3, new ItemStack(Items.NETHER_WART));
				var crafter = (net.minecraft.world.level.block.entity.CrafterBlockEntity) level.getBlockEntity(new BlockPos(cx + 4, y, cz - 1));
				for (int i : new int[]{0, 1, 3, 4}) crafter.setItem(i, new ItemStack(Items.OAK_PLANKS));
				var barrel = (net.minecraft.world.Container) level.getBlockEntity(new BlockPos(cx - 2, y, cz - 4));
				barrel.setItem(0, new ItemStack(Items.TORCH, 3));
				StringBuilder sb = new StringBuilder();
				for (Job job : JOBS) {
					String[] pick = firstWith(job);
					if (pick == null) {
						sb.append(job).append(": nobody! ");
						continue;
					}
					String problem = Bases.assign(p, be, new CaughtCreature(pick[0], Influence.byName(pick[1]), 30, 0, -1), () -> {});
					sb.append(job).append(": ").append(pick[0]).append('/').append(pick[1]).append(problem == null ? "" : " (" + problem + ")").append("; ");
				}
				return sb.toString();
			});
			LOG.info("BaseTest workers: {}", assigned);

			// watch it work for up to 2 minutes
			Map<String, Object> result = Map.of();
			for (int t = 0; t < 2400; t += 100) {
				context.waitTicks(100);
				result = server.computeOnServer(s -> check(s, core, y));
				if (t == 600) {
					server.runCommand("tp @p %d %d %d 0 50".formatted(cx, y + 14, cz - 14));
					context.waitTicks(20);
					context.takeScreenshot(TestScreenshotOptions.of("base_1_working"));
				}
				if ((boolean) result.get("done")) break;
			}
			LOG.info("BaseTest result: {}", result);
			for (var e : result.entrySet()) if (e.getValue() instanceof String why) failures.add(e.getKey() + ": " + why);
			context.takeScreenshot(TestScreenshotOptions.of("base_2_after"));

			// sorting: a chest with wheat in an item frame on it is the wheat chest. 10 wheat and some cobblestone put in an
			// unlabelled chest get sorted: the wheat moves to the wheat chest, the cobblestone stays
			BlockPos labelled = new BlockPos(cx + 7, y, cz - 2), plain = new BlockPos(cx + 2, y, cz);
			server.runCommand("setblock %d %d %d minecraft:chest".formatted(labelled.getX(), labelled.getY(), labelled.getZ()));
			server.runOnServer(s -> {
				ServerLevel level = s.overworld();
				var frame = new net.minecraft.world.entity.decoration.ItemFrame(level, labelled.south(), net.minecraft.core.Direction.SOUTH);
				frame.setItem(new ItemStack(Items.WHEAT));
				level.addFreshEntity(frame);
				var c = (net.minecraft.world.Container) level.getBlockEntity(plain);
				c.setItem(20, new ItemStack(Items.WHEAT, 10));
				c.setItem(21, new ItemStack(Items.COBBLESTONE, 5));
			});
			String sorted = "";
			for (int t = 0; t < 2400; t += 100) {
				context.waitTicks(100);
				sorted = server.computeOnServer(s -> {
					ServerLevel level = s.overworld();
					var wheatChest = (net.minecraft.world.Container) level.getBlockEntity(labelled);
					var other = (net.minecraft.world.Container) level.getBlockEntity(plain);
					int wheat = 0, strays = 0, left = 0, cobble = 0;
					for (int i = 0; i < wheatChest.getContainerSize(); i++) {
						ItemStack st = wheatChest.getItem(i);
						if (st.is(Items.WHEAT)) wheat += st.getCount();
						else if (!st.isEmpty()) strays += st.getCount();
					}
					for (int i = 0; i < other.getContainerSize(); i++) {
						if (other.getItem(i).is(Items.WHEAT)) left += other.getItem(i).getCount();
						if (other.getItem(i).is(Items.COBBLESTONE)) cobble += other.getItem(i).getCount();
					}
					return "wheat chest " + wheat + " wheat, " + strays + " other; plain chest " + left + " wheat, " + cobble + " cobblestone";
				});
				if (sorted.matches("wheat chest (1\\d|[2-9]\\d|\\d{3,}) wheat, 0 other; plain chest 0 wheat, 5 cobblestone")) break;
			}
			LOG.info("BaseTest sorting: {}", sorted);
			if (!sorted.matches("wheat chest (1\\d|[2-9]\\d|\\d{3,}) wheat, 0 other; plain chest 0 wheat, 5 cobblestone"))
				failures.add("Transport workers should sort the wheat into the wheat chest: " + sorted);
			server.runCommand("tp @p %d %d %d -90 30".formatted(cx + 2, y + 2, cz - 1));
			context.waitTicks(20);
			context.takeScreenshot(TestScreenshotOptions.of("base_2b_sorted"));

			// job settings: a worker with every job switched off stops working
			String idle = server.computeOnServer(s -> {
				BaseCoreBlockEntity be = (BaseCoreBlockEntity) s.overworld().getBlockEntity(core);
				for (int i = 0; i < be.workers().size(); i++) {
					var w = be.workers().get(i);
					if (!Bases.jobsOf(w.creature()).contains(Job.FISHING)) continue;
					for (Job j : Bases.jobsOf(w.creature())) Bases.changeJob(be, i, j.getSerializedName(), "job_toggle");
					return w.entity().toString();
				}
				return "";
			});
			context.waitTicks(400);
			String offStatus = server.computeOnServer(s -> s.overworld().getEntity(java.util.UUID.fromString(idle)) instanceof dev.ahrivaea.creature.CreatureEntity e && e.brain() != null
					? e.brain().status() : "no worker");
			LOG.info("BaseTest jobs off: {}", offStatus);
			if (!offStatus.contains("switched off")) failures.add("a worker with its jobs switched off should stop: " + offStatus);

			// the base's screen
			server.runOnServer(s -> {
				var p = s.getPlayerList().getPlayers().getFirst();
				Bases.send(p, (BaseCoreBlockEntity) s.overworld().getBlockEntity(core));
			});
			context.waitFor(c -> c.gui.screen() instanceof dev.ahrivaea.client.base.BaseScreen, 100);
			context.waitTicks(10);
			context.takeScreenshot(TestScreenshotOptions.of("base_3_screen"));
			context.runOnClient(c -> ((dev.ahrivaea.client.base.BaseScreen) c.gui.screen()).showJobs(0));
			context.waitTicks(10);
			context.takeScreenshot(TestScreenshotOptions.of("base_3b_jobs"));
			context.runOnClient(c -> c.gui.setScreen(null));

			// replanting from the chests: an empty spot marked for an oak sapling gets one fetched from a chest
			BlockPos spot = new BlockPos(cx + 9, y, cz - 9);
			server.runCommand("setblock %d %d %d minecraft:grass_block".formatted(spot.getX(), y - 1, spot.getZ()));
			server.runCommand("setblock %d %d %d minecraft:air".formatted(spot.getX(), y, spot.getZ()));
			server.runOnServer(s -> {
				var c = (net.minecraft.world.Container) s.overworld().getBlockEntity(new BlockPos(cx + 2, y, cz + 2));
				c.setItem(26, new ItemStack(Items.OAK_SAPLING, 3));
				((BaseCoreBlockEntity) s.overworld().getBlockEntity(core)).needsPlanting(spot, Items.OAK_SAPLING);
			});
			String planted = "";
			for (int t = 0; t < 1200 && !planted.startsWith("planted"); t += 100) {
				context.waitTicks(100);
				planted = server.computeOnServer(s -> {
					var st = s.overworld().getBlockState(spot);
					return (st.is(net.minecraft.tags.BlockTags.SAPLINGS) || st.is(net.minecraft.tags.BlockTags.LOGS) ? "planted " : "not yet ") + st;
				});
			}
			LOG.info("BaseTest replant from chest: {}", planted);
			if (!planted.startsWith("planted")) failures.add("a logger should fetch a sapling from a chest to replant: " + planted);

			// work speed: level 20 is a player's speed, 2% more or less per level
			String speeds = dev.ahrivaea.base.WorkerBrain.speedAt(1) + "/" + dev.ahrivaea.base.WorkerBrain.speedAt(20) + "/" + dev.ahrivaea.base.WorkerBrain.speedAt(50);
			if (!speeds.equals("0.62/1.0/1.6")) failures.add("work speed should be 62% at level 1, 100% at 20 and 160% at 50: " + speeds);

			// the base is whole chunks: level 1 is the core's chunk and the 8 around it, each level one more ring
			int ccx = cx >> 4;
			String area = server.computeOnServer(s -> {
				BaseCoreBlockEntity be = (BaseCoreBlockEntity) s.overworld().getBlockEntity(core);
				be.setBaseLevel(1);
				String one = be.contains(new BlockPos((ccx + 1) * 16 + 15, y, cz)) + "/" + be.contains(new BlockPos((ccx + 2) * 16, y, cz));
				be.setBaseLevel(3);
				String three = be.contains(new BlockPos((ccx - 3) * 16, y, cz)) + "/" + be.contains(new BlockPos((ccx - 3) * 16 - 1, y, cz));
				return one + " " + three;
			});
			if (!area.equals("true/false true/false")) failures.add("a level 1 base should be 3x3 chunks and a level 3 one 7x7: " + area);

			// and it stays loaded and running while its owner is online, however far away they go, unless switched off
			BlockPos edge = new BlockPos((ccx + 3) * 16 + 8, y, cz);
			server.runCommand("tp @p %d 200 %d".formatted(cx + 5000, cz));
			context.waitTicks(100);
			String away = server.computeOnServer(s -> s.overworld().isPositionEntityTicking(edge) + "/" + s.overworld().isPositionEntityTicking(core));
			server.runOnServer(s -> ((BaseCoreBlockEntity) s.overworld().getBlockEntity(core)).setChunkLoading(false));
			context.waitTicks(100);
			String off = server.computeOnServer(s -> s.overworld().isPositionEntityTicking(edge) + "/" + s.overworld().isPositionEntityTicking(core));
			LOG.info("BaseTest chunk loading: owner far away {}, switched off {}", away, off);
			if (!away.equals("true/true")) failures.add("a base should keep running while its owner is far away: " + away);
			if (!off.equals("false/false")) failures.add("a base that's switched off shouldn't stay loaded: " + off);
		}
		if (!failures.isEmpty()) throw new AssertionError(String.join("; ", failures));
	}

	/** A species and influence whose first job is this one. */
	private static String[] firstWith(Job job) {
		for (Species s : Species.all()) {
			for (var e : s.forms().entrySet()) {
				var jobs = s.jobsFor(e.getKey());
				if (!jobs.isEmpty() && jobs.getFirst() == job) return new String[]{s.id(), e.getKey().getSerializedName()};
			}
		}
		return null;
	}

	private static int countLogs(ServerLevel level, BlockPos base) {
		int n = 0;
		for (BlockPos p : BlockPos.betweenClosed(base.offset(-3, 0, -3), base.offset(3, 10, 3))) if (level.getBlockState(p).is(BlockTags.LOGS)) n++;
		return n;
	}

	/** What's happened so far: true for done, a String saying what hasn't happened yet. */
	private static Map<String, Object> check(MinecraftServer s, BlockPos core, int y) {
		ServerLevel level = s.overworld();
		int cx = core.getX(), cz = core.getZ();
		Map<String, Object> r = new TreeMap<>();
		int young = 0;
		for (BlockPos p : BlockPos.betweenClosed(new BlockPos(cx + 4, y, cz + 3), new BlockPos(cx + 8, y, cz + 5))) {
			var st = level.getBlockState(p);
			if (st.getBlock() instanceof CropBlock c && !c.isMaxAge(st)) young++;
		}
		r.put("farming", young >= 3 ? (Object) true : "no wheat harvested and replanted yet");
		int logs = countLogs(level, new BlockPos(cx - 8, y, cz + 6));
		boolean sapling = level.getBlockState(new BlockPos(cx - 8, y, cz + 6)).is(BlockTags.SAPLINGS);
		// the felled tree's logs end up in the chests (and the sapling it replants may already have grown again)
		int felled = count(level, core, Items.OAK_LOG);
		r.put("logging", felled >= 3 ? (Object) true : "only " + felled + " oak logs collected; tree has " + logs + " logs" + (sapling ? ", sapling replanted" : ""));
		var furnace = (AbstractFurnaceBlockEntity) level.getBlockEntity(new BlockPos(cx - 2, y, cz));
		int ingots = furnace.getItem(2).getCount() + count(level, core, Items.IRON_INGOT);
		r.put("smelting", ingots >= 2 ? (Object) true : "only " + ingots + " iron ingots, " + furnace.getItem(0).getCount() + " raw iron left, fuel " + furnace.getItem(1));
		Container postChest = HopperBlockEntity.getContainerAt(level, new BlockPos(((cx >> 4) + 2) * 16 + 9, y, ((cz >> 4) + 2) * 16 + 8));
		r.put("mining", postChest != null && !postChest.isEmpty() ? (Object) true : "nothing mined into the chest by the post");
		int stored = 0;
		for (BlockPos c : List.of(new BlockPos(cx + 2, y, cz), new BlockPos(cx + 2, y, cz + 2))) {
			Container ct = HopperBlockEntity.getContainerAt(level, c);
			for (int i = 0; ct != null && i < ct.getContainerSize(); i++) stored += ct.getItem(i).getCount();
		}
		r.put("transport", stored > 0 ? (Object) true : "nothing carried to the chests");
		boolean fished = count(level, core, Items.COD) + count(level, core, Items.SALMON) + count(level, core, Items.TROPICAL_FISH) + count(level, core, Items.PUFFERFISH)
				+ count(level, core, Items.BOWL) + count(level, core, Items.STICK) + count(level, core, Items.LEATHER_BOOTS) + count(level, core, Items.LILY_PAD) > 0;
		r.put("fishing", fished ? (Object) true : "nothing fished");
		boolean grown = !level.getBlockState(new BlockPos(cx + 6, y, cz - 6)).is(Blocks.OAK_SAPLING)
				|| level.getBlockState(new BlockPos(cx + 6, y, cz - 6)).getValue(net.minecraft.world.level.block.SaplingBlock.STAGE) > 0;
		int tended = dev.ahrivaea.base.WorkerBrain.TENDED;
		r.put("growth", tended >= 3 || grown ? (Object) true : "only " + tended + " plants tended");
		var stand = (net.minecraft.world.level.block.entity.BrewingStandBlockEntity) level.getBlockEntity(new BlockPos(cx - 3, y, cz - 2));
		boolean brewed = stand.getItem(0).getOrDefault(net.minecraft.core.component.DataComponents.POTION_CONTENTS, net.minecraft.world.item.alchemy.PotionContents.EMPTY)
				.is(net.minecraft.world.item.alchemy.Potions.AWKWARD);
		r.put("brewing", brewed ? (Object) true : "no awkward potions brewed (fuel " + stand.getItem(4) + ")");
		r.put("enchanting", count(level, core, Items.EXPERIENCE_BOTTLE) > 0 ? (Object) true : "no bottles o' enchanting from the table");
		boolean lamp = level.getBlockState(new BlockPos(cx - 5, y, cz + 2)).getValue(net.minecraft.world.level.block.RedstoneLampBlock.LIT);
		r.put("redstone", lamp ? (Object) true : "the lamp by the dynamo isn't lit");
		r.put("crafting", count(level, core, Items.CRAFTING_TABLE) > 0 ? (Object) true : "the crafter hasn't made a crafting table");
		r.put("ice", count(level, core, Items.ICE) + count(level, core, Items.SNOWBALL) > 0 ? (Object) true : "no ice made");
		int torches = 0;
		for (BlockPos p : BlockPos.betweenClosed(core.offset(-40, -2, -40), core.offset(40, 2, 40))) if (level.getBlockState(p).is(Blocks.TORCH)) torches++;
		r.put("lighting", torches > 0 ? (Object) true : "no torches put up");
		boolean done = r.values().stream().allMatch(v -> v instanceof Boolean);
		Map<String, Object> out = new TreeMap<>(r);
		out.put("done", done);
		return out;
	}

	/** Items of a kind in the base's chests, the post chest, and lying around. */
	private static int count(ServerLevel level, BlockPos core, net.minecraft.world.item.Item item) {
		int n = 0;
		for (BlockPos c : List.of(core.offset(2, 0, 0), core.offset(2, 0, 2), core.offset(-2, 0, -4))) {
			Container ct = HopperBlockEntity.getContainerAt(level, c);
			for (int i = 0; ct != null && i < ct.getContainerSize(); i++) if (ct.getItem(i).is(item)) n += ct.getItem(i).getCount();
		}
		for (var e : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(core).inflate(30)))
			if (e.getItem().is(item)) n += e.getItem().getCount();
		return n;
	}
}
