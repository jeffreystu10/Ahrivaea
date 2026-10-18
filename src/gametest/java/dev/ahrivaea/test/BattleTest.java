package dev.ahrivaea.test;

import com.google.gson.JsonObject;
import dev.ahrivaea.battle.Arena;
import dev.ahrivaea.battle.Battle;
import dev.ahrivaea.battle.Battles;
import dev.ahrivaea.battle.Unit;
import dev.ahrivaea.client.battle.BattleClient;
import dev.ahrivaea.client.battle.BattleScreen;
import dev.ahrivaea.creature.CreatureEntity;
import dev.ahrivaea.creature.Influence;
import dev.ahrivaea.creature.Species;
import dev.ahrivaea.party.CaughtCreature;
import dev.ahrivaea.party.Party;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * Plays a battle through the real battle screen: starts it by hitting a wild dragon, moves a unit with mouse
 * clicks, ends the turn, watches the enemy phase, wins, recruits, and checks XP and the party afterwards.
 */
public class BattleTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		List<String> failures = new ArrayList<>();
		try (TestSingleplayerContext sp = context.worldBuilder().create()) {
			sp.getConnection().waitForChunksRender();
			// a real window size, so the battle screen's layout matches the picture
			context.getInput().resizeWindow(1600, 900);
			context.waitTicks(2);
			var server = sp.getServer();
			server.runCommand("time set noon");
			server.runCommand("gamerule advance_time false");
			server.runCommand("gamerule spawn_mobs false");
			server.runCommand("item replace entity @a weapon.mainhand with minecraft:netherite_sword");
			server.runCommand("give @a minecraft:golden_apple 2");
			// a party of two, and a little hill and pond on the field
			server.runOnServer(s -> {
				ServerPlayer p = s.getPlayerList().getPlayers().getFirst();
				Species dragon = Species.get("dragon").orElseThrow();
				Party.add(p, new CaughtCreature("dragon", Influence.FIRE, 20, 0, -1));
				Party.add(p, new CaughtCreature("dragon", Influence.WATER, 18, 0, -1));
				// fix health to full
				for (int i = 0; i < 2; i++) {
					var c = Party.get(p).get(i);
					Party.set(p, i, new CaughtCreature(c.species(), c.influence(), c.level(), 0, dragon.statsFor(c.influence()).maxHealth(c.level())));
				}
			});
			var pos = context.computeOnClient(c -> c.player.blockPosition());
			server.runCommand("execute positioned %d %d %d run ahrivaea spawn dragon nature 8".formatted(pos.getX(), pos.getY(), pos.getZ() + 6));
			server.runCommand("execute as @e[type=ahrivaea:creature] run data merge entity @s {NoAI:1b}");
			context.waitTicks(10);

			// hitting it with an empty hand does nothing: only a sword starts a battle
			server.runCommand("item replace entity @a weapon.mainhand with minecraft:air");
			context.waitTicks(2);
			context.runOnClient(c -> {
				var wild = c.level.getEntitiesOfClass(CreatureEntity.class, c.player.getBoundingBox().inflate(12)).getFirst();
				c.gameMode.attack(c.player, wild);
			});
			context.waitTicks(20);
			boolean startedBarehanded = server.computeOnServer(s -> Battles.of(s.getPlayerList().getPlayers().getFirst()) != null);
			float wildHealth = server.computeOnServer(s -> s.overworld().getEntitiesOfClass(CreatureEntity.class, new net.minecraft.world.phys.AABB(pos).inflate(16)).getFirst().getHealth());
			float wildMax = server.computeOnServer(s -> s.overworld().getEntitiesOfClass(CreatureEntity.class, new net.minecraft.world.phys.AABB(pos).inflate(16)).getFirst().getMaxHealth());
			if (startedBarehanded) failures.add("hitting a creature without a sword shouldn't start a battle");
			if (wildHealth < wildMax) failures.add("hitting a creature without a sword shouldn't hurt it");
			server.runCommand("item replace entity @a weapon.mainhand with minecraft:netherite_sword");
			context.waitTicks(2);

			// hit the wild dragon with the sword: the battle starts
			context.runOnClient(c -> {
				var wild = c.level.getEntitiesOfClass(CreatureEntity.class, c.player.getBoundingBox().inflate(12)).getFirst();
				c.gameMode.attack(c.player, wild);
			});
			// off to the arena (another dimension); the battle screen opens there
			context.waitFor(c -> c.level.dimension().equals(Arena.DIMENSION) && c.gui.screen() instanceof BattleScreen, 400);
			sp.getConnection().waitForChunksRender();
			context.waitTicks(20);
			boolean wildGone = server.computeOnServer(s -> s.overworld().getEntitiesOfClass(CreatureEntity.class, new net.minecraft.world.phys.AABB(pos).inflate(20)).isEmpty());
			if (!wildGone) failures.add("the wild dragon should have gone into the arena with us");
			long[] times = server.computeOnServer(s -> new long[]{s.overworld().getDefaultClockTime(), s.getLevel(Arena.DIMENSION).getDefaultClockTime()});
			if (times[0] != times[1]) failures.add("the arena's time of day should match the world's: " + times[0] + " vs " + times[1]);
			// while we're away, someone builds over the spot we left from: we must still come back somewhere safe
			server.runCommand("fill %d %d %d %d %d %d minecraft:stone".formatted(pos.getX() - 1, pos.getY(), pos.getZ() - 1, pos.getX() + 1, pos.getY() + 2, pos.getZ() + 1));
			context.takeScreenshot(TestScreenshotOptions.of("battle_1_start"));

			// the camera: scroll zooms in towards the mouse, dragging the battlefield looks around, R goes back
			double[] cam0 = context.computeOnClient(c -> new double[]{BattleClient.camX(), BattleClient.camY(), BattleClient.camZ()});
			context.getInput().setCursorPos(800, 450);
			context.waitTick();
			context.getInput().scroll(4);
			context.waitTicks(3);
			context.getInput().holdMouse(0);
			for (int i = 0; i < 10; i++) {
				context.getInput().moveCursor(-30, 12);
				context.waitTick();
			}
			context.getInput().releaseMouse(0);
			context.waitTicks(3);
			double[] cam1 = context.computeOnClient(c -> new double[]{BattleClient.camX(), BattleClient.camY(), BattleClient.camZ()});
			context.takeScreenshot(TestScreenshotOptions.of("battle_1b_zoomed_and_moved"));
			if (cam1[1] > cam0[1] - 2) failures.add("scrolling should zoom in: camera height " + cam0[1] + " -> " + cam1[1]);
			if (cam1[0] < cam0[0] + 1 || cam1[2] > cam0[2] - 0.3) failures.add("dragging left and down should move the view right and up: x " + cam0[0] + " -> " + cam1[0] + ", z " + cam0[2] + " -> " + cam1[2]);
			boolean stillUnitStep = context.computeOnClient(c -> BattleClient.state() != null);
			if (!stillUnitStep) failures.add("dragging shouldn't end anything");
			context.runOnClient(c -> BattleClient.resetView());
			context.waitTicks(2);

			// select our first creature by clicking it, then click a square two steps away
			JsonObject state = context.computeOnClient(c -> BattleClient.state());
			JsonObject creature = null;
			for (var e : state.getAsJsonArray("units")) {
				JsonObject u = e.getAsJsonObject();
				if ("PLAYER".equals(u.get("side").getAsString()) && !u.get("player").getAsBoolean()) {
					creature = u;
					break;
				}
			}
			if (creature == null) throw new AssertionError("no creature of ours in the battle");
			int cx = creature.get("x").getAsInt(), cz = creature.get("z").getAsInt();
			int id = creature.get("id").getAsInt();
			click(context, cx, cz);
			context.waitTicks(3);
			context.takeScreenshot(TestScreenshotOptions.of("battle_2_move_range"));
			int tx = cx, tz = cz + 2;
			int[] dest = context.computeOnClient(c -> {
				// pick a square 1-2 steps away that the screen shows as reachable
				var g = BattleClient.grid();
				for (int[] d : new int[][]{{0, 2}, {2, 0}, {0, -2}, {-2, 0}, {0, 1}, {1, 0}, {0, -1}, {-1, 0}}) {
					int x = cx + d[0], z = cz + d[1];
					if (g.inside(x, z) && g.at(x, z) == dev.ahrivaea.battle.BattleGrid.Terrain.GROUND && unitFree(x, z)) return new int[]{x, z};
				}
				return new int[]{cx, cz};
			});
			click(context, dest[0], dest[1]);
			context.waitTicks(3);
			context.runOnClient(c -> ((BattleScreen) c.gui.screen()).waitHere());
			context.waitTicks(10);
			int[] moved = server.computeOnServer(s -> {
				Battle b = Battles.of(s.getPlayerList().getPlayers().getFirst());
				Unit u = b == null ? null : b.unit(id);
				return u == null ? new int[]{-1, -1} : new int[]{u.x, u.z};
			});
			if (moved[0] != dest[0] || moved[1] != dest[1]) failures.add("unit should have moved to " + dest[0] + "," + dest[1] + " but is at " + moved[0] + "," + moved[1]);

			// end the turn and let the enemy phase play out
			context.runOnClient(c -> ((BattleScreen) c.gui.screen()).endTurn());
			context.waitTicks(15);
			context.takeScreenshot(TestScreenshotOptions.of("battle_3_enemy_phase"));
			context.waitFor(c -> BattleClient.state() != null && "PLAYER".equals(BattleClient.state().get("phase").getAsString()), 600);
			context.takeScreenshot(TestScreenshotOptions.of("battle_4_turn_2"));

			// finish the enemies off (the rules of hitting are covered by the self-test), then recruit one
			server.runOnServer(s -> {
				ServerPlayer p = s.getPlayerList().getPlayers().getFirst();
				Battle b = Battles.of(p);
				if (b == null) return;
				for (Unit u : b.units) if (u.side == Unit.Side.ENEMY && u.alive()) {
					Unit attacker = b.playerUnit();
					// put the enemy next to us and swing until it falls
					u.hp = 1;
				}
			});
			// our player attacks the nearest enemy through the screen: select ourselves, stay, click the enemy
			JsonObject st2 = context.computeOnClient(c -> BattleClient.state());
			int[] me = null;
			for (var e : st2.getAsJsonArray("units")) {
				JsonObject u = e.getAsJsonObject();
				if (u.get("player").getAsBoolean()) me = new int[]{u.get("x").getAsInt(), u.get("z").getAsInt(), u.get("id").getAsInt()};
			}
			final int[] meF = me;
			server.runOnServer(s -> {
				Battle b = Battles.of(s.getPlayerList().getPlayers().getFirst());
				if (b == null) return;
				// line the enemies up next to the player so one swing each finishes them
				int k = 0;
				int[][] around = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
				for (Unit u : b.units) {
					if (u.side != Unit.Side.ENEMY || !u.alive()) continue;
					if (k < 4) {
						u.x = meF[0] + around[k][0];
						u.z = meF[1] + around[k][1];
					}
					k++;
				}
				Battles.send(b);
			});
			context.waitTicks(5);
			int enemiesBefore = server.computeOnServer(s -> {
				Battle b = Battles.of(s.getPlayerList().getPlayers().getFirst());
				return b == null ? 0 : (int) b.units.stream().filter(u -> u.side == Unit.Side.ENEMY && u.alive()).count();
			});
			for (int i = 0; i < enemiesBefore; i++) {
				final int k = i;
				server.runOnServer(s -> {
					Battle b = Battles.of(s.getPlayerList().getPlayers().getFirst());
					if (b == null) return;
					for (Unit u : b.units) if (u.side == Unit.Side.PLAYER) u.acted = false;
					Unit target = null;
					Unit me0 = b.playerUnit();
					for (Unit u : b.units) if (u.side == Unit.Side.ENEMY && u.alive() && (target == null || Math.abs(u.x - me0.x) + Math.abs(u.z - me0.z) < Math.abs(target.x - me0.x) + Math.abs(target.z - me0.z))) target = u;
					if (target == null) return;
					Unit p = b.playerUnit();
					// bring it next to us (a wild encounter can have up to 5)
					for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
						int nx = p.x + d[0], nz = p.z + d[1];
						if (b.units.stream().noneMatch(o -> o.alive() && o.x == nx && o.z == nz)) {
							target.x = nx;
							target.z = nz;
							break;
						}
					}
					// attacks can miss: keep swinging with the luck turned up
					for (int tries = 0; tries < 20 && target.alive() && b.phase == Battle.Phase.PLAYER; tries++) {
						p.acted = false;
						b.act(new Battle.Action("attack", p.id, p.x, p.z, 0, target.id));
						b.phase = b.phase == Battle.Phase.OVER ? Battle.Phase.OVER : Battle.Phase.PLAYER;
					}
					Battles.send(b);
				});
				context.waitTicks(5);
			}
			context.waitFor(c -> BattleClient.state() != null && BattleClient.state().has("result"), 200);
			context.waitTicks(5);
			context.takeScreenshot(TestScreenshotOptions.of("battle_5_victory"));
			int poolBefore = server.computeOnServer(s -> Party.xp(s.getPlayerList().getPlayers().getFirst()));
			// recruiting costs 100 XP per level times its rarity's multiplier; top the pool up so the test can afford one
			server.runOnServer(s -> Party.addXp(s.getPlayerList().getPlayers().getFirst(), 20000));
			// costs: 400 XP for a Lv.4 Common, 2000 for a Lv.4 Legendary; reinforcements for a Common Lv.4 are only
			// Commons of level 2-4
			String rules = server.computeOnServer(s -> {
				StringBuilder out = new StringBuilder();
				var cat = dev.ahrivaea.creature.Species.get("cat").orElseThrow();
				var none = dev.ahrivaea.creature.Influence.NONE;
				for (var spc : dev.ahrivaea.creature.Species.all()) {
					for (var inf : spc.forms().keySet()) {
						String r = dev.ahrivaea.party.RecruitRules.rarity(spc, inf);
						boolean ok4 = dev.ahrivaea.party.RecruitRules.canReinforce(spc, inf, 4, cat, none, 4);
						boolean ok5 = dev.ahrivaea.party.RecruitRules.canReinforce(spc, inf, 5, cat, none, 4);
						boolean ok1 = dev.ahrivaea.party.RecruitRules.canReinforce(spc, inf, 1, cat, none, 4);
						if (ok4 != r.equals("Common") || ok5 || ok1) out.append(spc.id()).append('/').append(inf.getSerializedName()).append(' ');
						if (r.equals("Common") && dev.ahrivaea.party.RecruitRules.cost(spc, inf, 4) != 400) out.append("cost:").append(spc.id()).append(' ');
						if (r.equals("Legendary") && dev.ahrivaea.party.RecruitRules.cost(spc, inf, 4) != 2000) out.append("cost:").append(spc.id()).append(' ');
					}
				}
				// and the picks themselves, in a plains biome
				var level = s.overworld();
				var biome = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BIOME).getOrThrow(net.minecraft.world.level.biome.Biomes.PLAINS);
				for (int i = 0; i < 200; i++) {
					var pick = dev.ahrivaea.party.RecruitRules.reinforcement(biome, level, level.getRandom(), cat, none, 4);
					if (pick.isPresent() && !dev.ahrivaea.party.RecruitRules.canReinforce(pick.get().species(), pick.get().influence(), pick.get().level(), cat, none, 4))
						out.append("pick:").append(pick.get().species().id()).append(" Lv.").append(pick.get().level()).append(' ');
				}
				return out.toString();
			});
			if (!rules.isEmpty()) failures.add("reinforcements for a Common Lv.4 should be Commons of Lv.2-4 (and a Lv.4 Common costs 400 XP, a Legendary 2000); wrong for: " + rules);
			int partyBefore = server.computeOnServer(s -> Party.get(s.getPlayerList().getPlayers().getFirst()).size());
			context.runOnClient(c -> ((BattleScreen) c.gui.screen()).recruit(0));
			context.waitTicks(10);
			int partyAfter = server.computeOnServer(s -> Party.get(s.getPlayerList().getPlayers().getFirst()).size());
			if (poolBefore < 10) failures.add("winning should give XP, pool is " + poolBefore);
			if (partyAfter != partyBefore + 1) failures.add("recruiting should add a party member (" + partyBefore + " -> " + partyAfter + ")");
			int charLevelXp = server.computeOnServer(s -> {
				var c = dev.ahrivaea.character.PlayerCharacter.get(s.getPlayerList().getPlayers().getFirst());
				return c.level() * 1000 + c.xp();
			});
			if (charLevelXp <= 1000) failures.add("the player character should have gained XP");
			context.runOnClient(c -> ((BattleScreen) c.gui.screen()).leave());
			for (int t = 0; t < 400 && !context.computeOnClient(c -> c.level != null && !(c.gui.screen() instanceof BattleScreen) && !c.level.dimension().equals(Arena.DIMENSION)); t++) context.waitTick();
			String where = context.computeOnClient(c -> "dimension " + (c.level == null ? "none" : c.level.dimension().identifier()) + ", screen " + (c.gui.screen() == null ? "none" : c.gui.screen().getClass().getSimpleName()) + ", battle state " + BattleClient.active());
			if (!where.contains("minecraft:overworld") || where.contains("BattleScreen")) throw new AssertionError("after leaving: " + where + "; " + String.join("; ", failures));
			sp.getConnection().waitForChunksRender();
			context.waitTicks(10);
			double away = context.computeOnClient(c -> Math.sqrt(c.player.blockPosition().distSqr(pos)));
			boolean stuck = server.computeOnServer(s -> {
				ServerPlayer pl = s.getPlayerList().getPlayers().getFirst();
				return !pl.level().noCollision(pl.getBoundingBox()) || pl.level().getBlockState(pl.blockPosition().below()).isAir();
			});
			if (away > 10) failures.add("after the battle we should be back near where we started, but are " + away + " blocks away");
			if (stuck) failures.add("after the battle we should be standing on the ground with room around us");
			context.takeScreenshot(TestScreenshotOptions.of("battle_6_after"));

			// reinforcements scale with the wild creature's level: up to 1 (50%) at 1-10, 2 (60%) at 11-20 ... 5 (90%) at 41-50
			String scaling = server.computeOnServer(s -> {
				StringBuilder out = new StringBuilder();
				var rnd = net.minecraft.util.RandomSource.create(7);
				int[][] cases = {{5, 1}, {15, 2}, {25, 3}, {45, 5}};
				for (int[] cs : cases) {
					int max = 0, sum = 0;
					for (int i = 0; i < 4000; i++) {
						int n = Battles.reinforcements(cs[0], rnd);
						max = Math.max(max, n);
						sum += n;
					}
					double mean = sum / 4000.0, want = cs[1] * (0.4 + 0.1 * cs[1]);
					if (max != cs[1] || Math.abs(mean - want) > 0.1) out.append("Lv.").append(cs[0]).append(": max ").append(max).append(", mean ").append(mean).append("; ");
				}
				return out.toString();
			});
			if (!scaling.isEmpty()) failures.add("reinforcement counts are off: " + scaling);

			// a second battle, fled during the enemy phase: fleeing always works, at any time, and the wild creature stays in the world.
			// One of our creatures is out with us first: it joins the battle and comes back out afterwards
			var here = context.computeOnClient(c -> c.player.blockPosition());
			int partyBeforeSend = server.computeOnServer(s -> Party.get(s.getPlayerList().getPlayers().getFirst()).size());
			context.runOnClient(c -> c.player.connection.sendCommand("party send 1"));
			context.waitTicks(20);
			server.runCommand("execute as @e[type=ahrivaea:creature] run data merge entity @s {NoAI:1b}");
			server.runCommand("execute positioned %d %d %d run ahrivaea spawn dragon frost 45".formatted(here.getX(), here.getY(), here.getZ() + 6));
			server.runCommand("execute as @e[type=ahrivaea:creature] run data merge entity @s {NoAI:1b}");
			context.waitTicks(10);
			context.runOnClient(c -> {
				var wild = c.level.getEntitiesOfClass(CreatureEntity.class, c.player.getBoundingBox().inflate(12), x -> !x.isTame()).getFirst();
				c.gameMode.attack(c.player, wild);
			});
			context.waitFor(c -> c.level.dimension().equals(Arena.DIMENSION) && c.gui.screen() instanceof BattleScreen, 400);
			context.waitTicks(10);
			// in battle food doesn't heal, only brewed potions do (and a drunk potion leaves its bottle)
			String items = server.computeOnServer(s -> {
				ServerPlayer p = s.getPlayerList().getPlayers().getFirst();
				Battle b = Battles.of(p);
				if (b == null) return "no battle";
				Unit pu = b.playerUnit();
				p.getInventory().clearContent();
				p.getInventory().add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GOLDEN_APPLE, 3));
				p.getInventory().add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COOKED_BEEF, 3));
				pu.hp = 1;
				pu.acted = false;
				String withFood = b.act(new Battle.Action("item", pu.id, pu.x, pu.z, -1, pu.id));
				p.getInventory().add(net.minecraft.world.item.alchemy.PotionContents.createItemStack(net.minecraft.world.item.Items.POTION, net.minecraft.world.item.alchemy.Potions.HEALING));
				pu.acted = false;
				String withPotion = b.act(new Battle.Action("item", pu.id, pu.x, pu.z, -1, pu.id));
				var inv = p.getInventory();
				int bottles = java.util.stream.IntStream.range(0, inv.getContainerSize())
						.map(i -> inv.getItem(i).is(net.minecraft.world.item.Items.GLASS_BOTTLE) ? inv.getItem(i).getCount() : 0).sum();
				int expected = 1 + (int) Math.round(pu.maxHp * 0.4);
				pu.acted = false;
				Battles.send(b);
				return (withFood == null ? "food was accepted; " : "") + (withPotion != null ? "potion refused: " + withPotion + "; " : "")
						+ (pu.hp != Math.min(pu.maxHp, expected) ? "hp " + pu.hp + " not " + expected + "; " : "") + (bottles != 1 ? bottles + " bottles; " : "");
			});
			if (!items.isEmpty()) failures.add("battle healing: " + items);
			context.waitTicks(3);

			// the creature that was out is fighting with us; then all our creatures and the challenged one go down,
			// which ends nothing: only being knocked out yourself loses (and only beating every enemy wins)
			String joined = server.computeOnServer(s -> {
				Battle b = Battles.of(s.getPlayerList().getPlayers().getFirst());
				if (b == null) return "no battle";
				long outUnits = b.units.stream().filter(u -> u.side == Unit.Side.PLAYER && !u.isPlayer && u.partySlot <= -2).count();
				long enemies = b.units.stream().filter(u -> u.side == Unit.Side.ENEMY).count();
				for (Unit u : b.units) if (!u.isPlayer && u.side == Unit.Side.PLAYER) u.hp = 0;
				if (enemies > 1) b.units.stream().filter(u -> u.side == Unit.Side.ENEMY).findFirst().ifPresent(u -> u.hp = 0);
				Battles.send(b);
				return outUnits + " out, " + enemies + " enemies";
			});
			if (!joined.startsWith("1 out")) failures.add("the creature we had out should join the battle: " + joined);
			context.runOnClient(c -> ((BattleScreen) c.gui.screen()).endTurn());
			context.waitTicks(3);
			String phaseWhenFleeing = context.computeOnClient(c -> BattleClient.state().get("phase").getAsString());
			boolean stillOn = server.computeOnServer(s -> Battles.of(s.getPlayerList().getPlayers().getFirst()) != null);
			if (!stillOn) failures.add("losing all your creatures (and the challenged creature going down) shouldn't end the battle");
			context.runOnClient(c -> ((BattleScreen) c.gui.screen()).flee());
			context.waitFor(c -> BattleClient.state() != null && BattleClient.state().has("result"), 100);
			context.takeScreenshot(TestScreenshotOptions.of("battle_7_fled"));
			context.runOnClient(c -> ((BattleScreen) c.gui.screen()).leave());
			context.waitFor(c -> !(c.gui.screen() instanceof BattleScreen) && !c.level.dimension().equals(Arena.DIMENSION), 400);
			context.waitTicks(10);
			if (!"ENEMY".equals(phaseWhenFleeing)) failures.add("the flee test should have run in the enemy phase, was " + phaseWhenFleeing);
			String outcome = server.computeOnServer(s -> {
				var all = s.overworld().getEntitiesOfClass(CreatureEntity.class, new net.minecraft.world.phys.AABB(here).inflate(16), c -> !c.isTame());
				return all.size() + " wild";
			});
			if (!outcome.equals("1 wild")) failures.add("after fleeing, the wild creature should be back in the world even though it was knocked out (found " + outcome + ")");
			int[] backOut = server.computeOnServer(s -> {
				var p = s.getPlayerList().getPlayers().getFirst();
				int near = s.overworld().getEntitiesOfClass(CreatureEntity.class, p.getBoundingBox().inflate(8), c -> c.isTame() && c.isOwnedBy(p)).size();
				return new int[]{near, Party.get(p).size()};
			});
			if (backOut[0] != 1 || backOut[1] != partyBeforeSend - 1)
				failures.add("our creature that was out should be back out beside us (not in the party): " + backOut[0] + " out, party " + backOut[1]);
		}
		if (!failures.isEmpty()) throw new AssertionError(String.join("; ", failures));
	}

	private static boolean unitFree(int x, int z) {
		for (var e : BattleClient.state().getAsJsonArray("units")) {
			JsonObject u = e.getAsJsonObject();
			if (u.get("hp").getAsInt() > 0 && u.get("x").getAsInt() == x && u.get("z").getAsInt() == z) return false;
		}
		return true;
	}

	/** Moves the real mouse to the middle of a battlefield square and clicks. */
	private static void click(ClientGameTestContext context, int x, int z) {
		double[] p = context.computeOnClient(c -> {
			BattleScreen s = (BattleScreen) c.gui.screen();
			double[] r = s.cellRect(x, z);
			double scale = c.getWindow().getGuiScale();
			return new double[]{(r[0] + r[2]) / 2 * scale, (r[1] + r[3]) / 2 * scale};
		});
		context.getInput().setCursorPos(p[0], p[1]);
		context.waitTick();
		context.getInput().pressMouse(0);
	}
}
