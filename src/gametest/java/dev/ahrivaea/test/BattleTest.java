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
			server.runCommand("gamerule doDaylightCycle false");
			server.runCommand("gamerule doMobSpawning false");
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

			// hit the wild dragon: the battle starts
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
			context.takeScreenshot(TestScreenshotOptions.of("battle_1_start"));

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
			for (int i = 0; i < enemiesBefore && i < 4; i++) {
				final int k = i;
				server.runOnServer(s -> {
					Battle b = Battles.of(s.getPlayerList().getPlayers().getFirst());
					if (b == null) return;
					for (Unit u : b.units) if (u.side == Unit.Side.PLAYER) u.acted = false;
					Unit target = null;
					for (Unit u : b.units) if (u.side == Unit.Side.ENEMY && u.alive()) target = u;
					if (target == null) return;
					Unit p = b.playerUnit();
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
			// recruiting costs 10 XP per level; top the pool up so the test can afford one
			server.runOnServer(s -> Party.addXp(s.getPlayerList().getPlayers().getFirst(), 500));
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
			context.waitFor(c -> !(c.gui.screen() instanceof BattleScreen) && !c.level.dimension().equals(Arena.DIMENSION), 400);
			sp.getConnection().waitForChunksRender();
			context.waitTicks(10);
			double away = context.computeOnClient(c -> Math.sqrt(c.player.blockPosition().distSqr(pos)));
			if (away > 3) failures.add("after the battle we should be back where we started, but are " + away + " blocks away");
			context.takeScreenshot(TestScreenshotOptions.of("battle_6_after"));
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
