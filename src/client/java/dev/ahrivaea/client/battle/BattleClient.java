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

	/** Scroll to zoom (closer or farther), WASD/arrows to pan; kept over the battlefield. */
	public static void zoom(double steps) {
		camY = Math.clamp(camY - steps * 2, maxHeight + 7, maxHeight + 7 + (grid == null ? 23 : grid.size));
	}

	public static void pan(double dx, double dz) {
		if (grid == null) return;
		camX = Math.clamp(camX + dx, grid.originX, grid.originX + grid.size);
		camZ = Math.clamp(camZ + dz, grid.originZ, grid.originZ + grid.size);
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
			camY = max + Math.round(size * 0.62f);
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
