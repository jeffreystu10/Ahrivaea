package dev.ahrivaea.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;

/**
 * Starts the real game (no window), lines up every dragon form on a flat world and takes screenshots,
 * so the models can be checked without anyone running Minecraft. CI publishes them to the
 * ci-screenshots branch.
 */
public class CreatureScreenshots implements FabricClientGameTest {
	/**
	 * Creatures with models from the Mythic Mob Library, in rows by size: each row is its influence, its size
	 * (s = small and medium, l = large, x = huge) and the species.
	 */
	private static final String[][] GROUPS = {
			{"arcane", "s", "homunculus", "living_spellbook", "enchanted_lantern", "clockwork_beetle", "clockwork_raven", "enchanted_rug", "mage", "artificer"},
			{"arcane", "s", "enchanted_axe", "enchanted_broom", "flying_sword", "animated_armor", "living_statue", "clockwork_soldier"},
			{"arcane", "l", "djinni", "elemental", "shield_guardian"},
			{"arcane", "x", "gilded_sentinel", "clockwork_dragon"},
			{"darkness", "s", "gremlin", "hraesvelgr", "mephit", "ghost", "lich", "mage", "night_hag"},
			{"darkness", "s", "ranger", "skeleton", "vampire", "warrior", "wight", "yurei", "zombie"},
			{"darkness", "l", "drider", "death_knight", "elemental", "nidhogg"},
			{"darkness", "x", "cerberus", "dragon"},
			{"earth", "s", "boggart", "gargoyle", "mephit", "aboleth", "bake_kujira", "black_pudding", "mage"},
			{"earth", "s", "ranger", "troll", "warrior", "giant_scorpion"},
			{"earth", "l", "behemoth", "cyclops", "elemental", "golem", "sphinx", "giant_snake"},
			{"earth", "x", "dragon", "terrasque", "wyrm"},
			{"fae", "s", "brownie", "gnome", "jackalope", "leprechaun", "myconid", "pixie", "will_o_the_wisp"},
			{"fae", "s", "doppleganger", "dryad", "fairy_queen", "giant_snail", "mage", "mushussu", "satyr"},
			{"fae", "l", "pegasus", "unicorn", "couatl", "elemental", "treant"},
			{"fae", "x", "dragon"},
			{"fire", "s", "axe_beak", "cockatrice", "imp", "mephit", "peryton", "efreeti", "kitsune"},
			{"fire", "s", "hell_hound", "omukade", "mage", "ranger", "warrior", "death_knight"},
			{"fire", "l", "elemental", "namean_lion", "oni", "phoenix", "salamander"},
			{"fire", "x", "dragon", "drathkuhl_balor"},
			{"frost", "s", "mephit", "nue", "giant_boar", "saber_toothed_cat", "mage", "ranger", "warrior"},
			{"frost", "s", "cambion", "draugr", "krampus", "revenant", "spriggan", "zombie"},
			{"frost", "l", "elemental", "giant", "moose", "wyvern", "yeti"},
			{"frost", "x", "dragon", "jotunn"},
			{"nature", "s", "ibong_adarna", "mephit", "myconid", "assassin_vine", "dryad", "komainu", "mage"},
			{"nature", "s", "ranger", "warrior", "zombie", "leshy", "centaur", "wild_hunt_rider"},
			{"nature", "l", "elemental", "giant_ape", "shambling_mound", "treant", "troll", "wendigo"},
			{"nature", "x", "dragon"},
			{"none", "s", "dog", "duck", "fox", "giant_rat", "goblin", "jellyfish", "dolphin"},
			{"none", "s", "gnoll", "goat", "horse", "insect_swarm", "lion", "lizardfolk", "llama", "medusa"},
			{"none", "l", "elephant", "gelatinous_cube", "griffon"},
			{"none", "x", "dragon", "t_rex"},
			{"none", "s", "cat", "chicken", "crab", "mimic", "penguin", "bear", "camel", "cow"},
			{"none", "s", "deer", "bandit", "birdfolk", "bugbear", "catfolk", "cultist", "minotaur", "ogre", "orc"},
			{"none", "l", "chimaera", "moose", "owlbear"}};

	private static final String[] FORMS = {"fire", "frost", "storm", "earth", "water", "nature", "void", "fae", "darkness", "radiant", "none"};

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext sp = context.worldBuilder()
				.adjustSettings(c -> c.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE)).create()) {
			sp.getConnection().waitForChunksRender();
			BlockPos p = context.computeOnClient(client -> client.player.blockPosition());
			int x = p.getX(), y = p.getY(), z = p.getZ();
			var server = sp.getServer();
			server.runCommand("time set noon");
			server.runCommand("weather clear");
			server.runCommand("gamerule advance_time false");
			server.runCommand("gamerule spawn_mobs false");
			for (int i = 0; i < FORMS.length; i++) {
				int dx = (i % 4) * 7 - 10, dz = 10 + (i / 4) * 8;
				server.runCommand("execute positioned %d %d %d run ahrivaea spawn dragon %s 30".formatted(x + dx, y, z + dz, FORMS[i]));
			}
			server.runCommand("execute as @e[type=ahrivaea:creature] run data merge entity @s {NoAI:1b}");
			context.runOnClient(client -> {
				if (!client.gui.hud.isHidden()) client.gui.hud.toggle();
				client.player.getAbilities().flying = true;
			});
			context.waitTicks(40);

			// all eleven from above and in front (the player faces south, towards them)
			view(context, server, x - 0.5, y + 11, z - 6, 0, 38);
			context.takeScreenshot(TestScreenshotOptions.of("dragons_overview").withSize(1600, 900));

			// Ashen Dragon close up, three-quarter front
			view(context, server, x - 10 + 4.5, y + 3, z + 10 + 5.5, 140, 18);
			context.takeScreenshot(TestScreenshotOptions.of("ashen_closeup").withSize(1600, 900));

			// side view, to check it stands upright and faces the right way
			view(context, server, x - 10 + 8, y + 1.5, z + 10, 90, 5);
			context.takeScreenshot(TestScreenshotOptions.of("ashen_side").withSize(1600, 900));

			// back row from behind
			view(context, server, x + 0.5, y + 7, z + 38, 180, 25);
			context.takeScreenshot(TestScreenshotOptions.of("dragons_back").withSize(1600, 900));

			// the creatures with library models: each group in a row along x, 60 blocks apart, facing the camera
			server.runCommand("kill @e[type=ahrivaea:creature]");
			java.util.Map<String, Integer> shots = new java.util.HashMap<>();
			for (int g = 0; g < GROUPS.length; g++) {
				String influence = GROUPS[g][0], size = GROUPS[g][1];
				int gx = x + 60 + g * 60, n = GROUPS[g].length - 2;
				int spacing = size.equals("x") ? 20 : size.equals("l") ? 7 : 3;
				float[] cam = size.equals("x") ? new float[]{14, 14, 40} : size.equals("l") ? new float[]{10, 6, 20} : new float[]{8, 3, 12};
				// from the south looking north; they face south, towards us (go there first so the chunks are loaded)
				view(context, server, gx + 0.5, y + cam[1], z + 10 + cam[2], 180, cam[0]);
				sp.getConnection().waitForChunksRender();
				for (int i = 0; i < n; i++) {
					int cx = gx + Math.round((i - (n - 1) / 2f) * spacing);
					server.runCommand("execute positioned %d %d %d run ahrivaea spawn %s %s 20".formatted(cx, y, z + 10, GROUPS[g][i + 2], influence));
				}
				server.runCommand("execute as @e[type=ahrivaea:creature] run data merge entity @s {NoAI:1b,Rotation:[0f,0f]}");
				context.waitTicks(40);
				int k = shots.merge(influence, 1, Integer::sum) - 1;
				context.takeScreenshot(TestScreenshotOptions.of(influence + "_" + k).withSize(1600, 900));
				if (influence.equals("nature") && k == 0) {
					// the Assassin Vine (in the middle of this row) close up: its tentacles are thin
					view(context, server, gx + 0.5, y + 1.2, z + 10 + 4.5, 180, 5);
					context.waitTicks(10);
					context.takeScreenshot(TestScreenshotOptions.of("nature_vine_closeup").withSize(1600, 900));
				}
			}
			// a close look at the medium ones, three-quarter front
			view(context, server, x + 120 + 0.5 + 9, y + 2.5, z + 10 + 6, 125, 12);
			context.waitTicks(20);
			context.takeScreenshot(TestScreenshotOptions.of("arcane_side").withSize(1600, 900));
		}
	}

	private static void view(ClientGameTestContext context, net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server,
			double x, double y, double z, float yaw, float pitch) {
		server.runCommand("tp @a %.2f %.2f %.2f %.1f %.1f".formatted(x, y, z, yaw, pitch));
		context.waitTicks(10);
	}
}
