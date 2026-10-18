package dev.ahrivaea.client.battle;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.ahrivaea.battle.BattleGrid;
import dev.ahrivaea.battle.Battles;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

/** The client's copy of the battle it's in, received from the server. Drives the camera and the battle screen. */
public final class BattleClient {
	private BattleClient() {}

	private static JsonObject state;
	private static BattleGrid grid;
	private static double camX, camY, camZ;
	private static boolean hudWasHidden;

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(Battles.StatePayload.TYPE, (payload, ctx) -> receive(ctx.client(), payload.json()));
		// moving to the arena (another dimension) closes every screen; open the battle screen again once there
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (state != null && mc.level != null && mc.player != null && mc.gui.screen() == null) mc.gui.setScreen(new BattleScreen());
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> {
			state = null;
			grid = null;
		});
	}

	public static boolean active() {
		return state != null;
	}

	public static JsonObject state() {
		return state;
	}

	public static BattleGrid grid() {
		return grid;
	}

	public static double camX() {
		return camX;
	}

	public static double camY() {
		return camY;
	}

	private static int maxHeight;
	private static double homeY;

	/** Closest and farthest the camera goes (blocks above the highest ground). */
	static double minHeight() {
		return maxHeight + 5;
	}

	static double maxHeightAbove() {
		return maxHeight + 6 + (grid == null ? 23 : grid.size * 1.2);
	}

	/** Scroll to zoom: closer or farther, towards (wx, wz), the point under the mouse. */
	public static void zoomAt(double steps, double wx, double wz) {
		double before = camY;
		camY = Math.clamp(camY * Math.pow(0.88, steps) + (1 - Math.pow(0.88, steps)) * maxHeight, minHeight(), maxHeightAbove());
		// keep the point under the mouse where it is
		double k = (before - camY) / Math.max(1, before - maxHeight);
		pan((wx - camX) * k, (wz - camZ) * k);
	}

	public static void zoom(double steps) {
		zoomAt(steps, camX, camZ);
	}

	/** Moves the camera (blocks), kept over the battlefield. */
	public static void pan(double dx, double dz) {
		if (grid == null) return;
		camX = Math.clamp(camX + dx, grid.originX - 2, grid.originX + grid.size + 2);
		camZ = Math.clamp(camZ + dz, grid.originZ - 2, grid.originZ + grid.size + 2);
	}

	/** Looks at a square of the battlefield. */
	public static void centerOn(int x, int z) {
		if (grid == null) return;
		camX = grid.originX + x + 0.5;
		camZ = grid.originZ + z + 0.5;
	}

	/** Back to the starting view: the whole battlefield. */
	public static void resetView() {
		if (grid == null) return;
		camX = grid.originX + grid.size / 2.0;
		camZ = grid.originZ + grid.size / 2.0;
		camY = homeY;
	}

	public static double camZ() {
		return camZ;
	}

	static void receive(Minecraft mc, String json) {
		JsonObject o = JsonParser.parseString(json).getAsJsonObject();
		if (!o.has("grid")) {
			end(mc);
			return;
		}
		boolean starting = state == null;
		state = o;
		if (starting) {
			JsonObject g = o.getAsJsonObject("grid");
			int size = g.get("size").getAsInt();
			int[] h = new int[size * size];
			BattleGrid.Terrain[] t = new BattleGrid.Terrain[size * size];
			String ts = g.get("t").getAsString();
			int max = Integer.MIN_VALUE;
			for (int i = 0; i < h.length; i++) {
				h[i] = g.getAsJsonArray("h").get(i).getAsInt();
				t[i] = BattleGrid.Terrain.of(ts.charAt(i));
				max = Math.max(max, h[i]);
			}
			grid = new BattleGrid(size, g.get("ox").getAsInt(), g.get("oz").getAsInt(), h, t);
			camX = grid.originX + size / 2.0;
			camZ = grid.originZ + size / 2.0;
			// high enough to see the whole field, whatever its size (13 above the top for 21×21)
			camY = max + Math.round(size * 0.7f);
			homeY = camY;
			maxHeight = max;
			hudWasHidden = mc.gui.hud.isHidden();
			if (!hudWasHidden) mc.gui.hud.toggle();
		}
		if (!(mc.gui.screen() instanceof BattleScreen)) mc.gui.setScreen(new BattleScreen());
		else ((BattleScreen) mc.gui.screen()).refresh();
	}

	static void end(Minecraft mc) {
		if (state == null) return;
		state = null;
		grid = null;
		if (mc.gui.hud.isHidden() != hudWasHidden) mc.gui.hud.toggle();
		if (mc.gui.screen() instanceof BattleScreen) mc.gui.setScreen(null);
	}

	public static void send(JsonObject action) {
		ClientPlayNetworking.send(new Battles.ActionPayload(action.toString()));
	}
}
