package dev.ahrivaea.test;

import dev.ahrivaea.client.VillagerScreen;
import dev.ahrivaea.creature.CreatureEntity;
import dev.ahrivaea.creature.Influence;
import dev.ahrivaea.party.CaughtCreature;
import dev.ahrivaea.party.Party;
import dev.ahrivaea.villager.Profession;
import dev.ahrivaea.villager.Villagers;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.stats.Stats;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Villagers: a vanilla villager becomes a villager creature; a villager in the party is trained as a Mason, earns
 * Novice by mining stone, and sells cobblestone for XP; it wears the mason's clothes when out. Also checks every
 * profession loaded and that the professions between them sell nearly every item.
 */
public class VillagerTest implements FabricClientGameTest {
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

			// the professions, and what they sell between them
			String coverage = server.computeOnServer(s -> {
				Set<net.minecraft.world.item.Item> sold = new HashSet<>();
				int offers = 0;
				for (Profession p : Profession.all().values())
					for (Profession.Rank r : p.ranks()) for (Profession.Offer o : r.sells()) {
						sold.add(o.item());
						offers++;
					}
				List<String> missing = new ArrayList<>();
				for (var item : BuiltInRegistries.ITEM) if (!sold.contains(item)) missing.add(BuiltInRegistries.ITEM.getKey(item).getPath());
				LOG.info("VillagerTest: not sold ({}): {}", missing.size(), String.join(" ", missing));
				return Profession.all().size() + " professions, " + offers + " offers, " + sold.size() + " items, beacon " + sold.contains(Items.BEACON)
						+ ", elytra " + sold.contains(Items.ELYTRA) + ", cobblestone " + sold.contains(Items.COBBLESTONE) + ", base core " + sold.contains(dev.ahrivaea.base.Bases.BASE_CORE.asItem());
			});
			LOG.info("VillagerTest: {}", coverage);
			if (!coverage.startsWith("17 professions") || !coverage.endsWith("beacon true, elytra true, cobblestone true, base core false"))
				failures.add("the professions should sell everything but creative-only things and base cores: " + coverage);

			// a vanilla villager becomes a villager creature
			var pos = context.computeOnClient(c -> c.player.blockPosition());
			server.runCommand("summon minecraft:villager %d %d %d".formatted(pos.getX() + 4, pos.getY(), pos.getZ() + 6));
			context.waitTicks(20);
			String wild = server.computeOnServer(s -> {
				var list = s.overworld().getEntitiesOfClass(CreatureEntity.class, new net.minecraft.world.phys.AABB(pos).inflate(12), e -> Villagers.isVillager(e.getSpeciesId()));
				return list.isEmpty() ? "none" : list.getFirst().displayName();
			});
			LOG.info("VillagerTest: the summoned villager became {}", wild);
			if (wild.equals("none")) failures.add("a vanilla villager should become a villager creature");

			// a villager in the party: train it as a Mason
			server.runOnServer(s -> {
				var p = s.getPlayerList().getPlayers().getFirst();
				Party.add(p, new CaughtCreature("villager", Influence.NONE, 5, 0, 30));
				Party.addXp(p, 500);
			});
			context.runOnClient(c -> c.player.connection.sendCommand("villager"));
			context.waitFor(c -> c.gui.screen() instanceof VillagerScreen, 100);
			context.waitTicks(10);
			context.takeScreenshot(TestScreenshotOptions.of("villager_1_professions").withSize(1600, 900));
			context.runOnClient(c -> ((VillagerScreen) c.gui.screen()).train("mason"));
			context.waitTicks(10);
			// the Novice deed: mine 64 stone (counted from now)
			server.runOnServer(s -> s.getPlayerList().getPlayers().getFirst().awardStat(Stats.BLOCK_MINED.get(Blocks.STONE), 64));
			context.runOnClient(c -> c.player.connection.sendCommand("villager"));
			context.waitTicks(10);
			context.runOnClient(c -> ((VillagerScreen) c.gui.screen()).promote());
			context.waitTicks(10);
			String rank = server.computeOnServer(s -> {
				var job = Party.get(s.getPlayerList().getPlayers().getFirst()).getFirst().job();
				return job.profession() + " " + job.rank();
			});
			if (!rank.equals("mason 1")) failures.add("mining 64 stone should make it a Novice Mason: " + rank);
			// buy the first thing it sells, then a cobblestone
			int[] before = server.computeOnServer(s -> new int[]{Party.xp(s.getPlayerList().getPlayers().getFirst())});
			int cobble = server.computeOnServer(s -> {
				var offers = Profession.get("mason").ranks().getFirst().sells();
				for (int i = 0; i < offers.size(); i++) if (offers.get(i).item() == Items.COBBLESTONE) return i;
				return -1;
			});
			server.runCommand("clear @a");
			context.runOnClient(c -> ((VillagerScreen) c.gui.screen()).buy(cobble, 1));
			context.waitTicks(10);
			String bought = server.computeOnServer(s -> {
				var p = s.getPlayerList().getPlayers().getFirst();
				int n = 0;
				for (int i = 0; i < p.getInventory().getContainerSize(); i++) if (p.getInventory().getItem(i).is(Items.COBBLESTONE)) n += p.getInventory().getItem(i).getCount();
				return n + " cobblestone, " + (before[0] - Party.xp(p)) + " XP spent";
			});
			LOG.info("VillagerTest: bought {}", bought);
			if (!bought.startsWith("64 cobblestone") || bought.contains(" 0 XP spent")) failures.add("buying cobblestone should give 64 for some XP: " + bought);
			context.waitTicks(10);
			context.takeScreenshot(TestScreenshotOptions.of("villager_2_mason").withSize(1600, 900));
			context.runOnClient(c -> c.gui.setScreen(null));

			// out in the world, in a mason's clothes
			context.runOnClient(c -> c.player.connection.sendCommand("party send 1"));
			context.waitTicks(30);
			String out = server.computeOnServer(s -> {
				var p = s.getPlayerList().getPlayers().getFirst();
				var list = s.overworld().getEntitiesOfClass(CreatureEntity.class, p.getBoundingBox().inflate(10), e -> e.isOwnedBy(p) && Villagers.isVillager(e.getSpeciesId()));
				if (list.isEmpty()) return "not out";
				var e = list.getFirst();
				e.setOrderedToSit(true);
				s.getPlayerList().getPlayers().getFirst().teleportTo(e.getX(), e.getY(), e.getZ() - 3.5);
				p.setYRot(0);
				p.setXRot(10);
				return e.getAttachedOrElse(Villagers.JOB, dev.ahrivaea.villager.VillagerJob.NONE).profession();
			});
			if (!out.equals("mason")) failures.add("the villager out in the world should still be a mason: " + out);
			server.runCommand("tp @a ~ ~ ~ 0 10");
			context.runOnClient(c -> {
				if (!c.gui.hud.isHidden()) c.gui.hud.toggle();
			});
			context.waitTicks(30);
			context.takeScreenshot(TestScreenshotOptions.of("villager_3_out").withSize(1600, 900));
			server.runOnServer(s -> {
				var p = s.getPlayerList().getPlayers().getFirst();
				for (var e : s.overworld().getEntitiesOfClass(CreatureEntity.class, p.getBoundingBox().inflate(30), e -> true)) e.discard();
			});
			context.waitTicks(5);
		}
		if (!failures.isEmpty()) throw new AssertionError(String.join("; ", failures));
	}
}
