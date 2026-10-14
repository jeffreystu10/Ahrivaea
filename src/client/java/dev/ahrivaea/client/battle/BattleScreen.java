package dev.ahrivaea.client.battle;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.ahrivaea.battle.BattleGrid;
import dev.ahrivaea.battle.Pathing;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The battle seen from above. Click one of your units, click where it should go (or itself to stay), then
 * pick what it does: a move (then its target), an item or rally (you only), or wait. Right-click or Escape
 * goes back a step. Blue squares: where it can move. Red: what it can hit from there.
 */
public class BattleScreen extends Screen {
	private enum Step { UNIT, DESTINATION, ACTION, TARGET }

	private Step step = Step.UNIT;
	private int selected = -1;
	private int destX, destZ;
	/** In the TARGET step: a move index, or -2 for item, -3 for rally. */
	private int pending;
	private int hoverX = -1, hoverZ = -1;

	public BattleScreen() {
		super(Component.literal("Battle"));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	protected void init() {
		rebuild();
	}

	void refresh() {
		JsonObject u = unit(selected);
		if (u == null || u.get("hp").getAsInt() <= 0 || u.get("acted").getAsBoolean() || !"PLAYER".equals(phase())) reset();
		rebuild();
	}

	private void reset() {
		step = Step.UNIT;
		selected = -1;
	}

	// ---- state helpers ----

	private static JsonObject state() {
		return BattleClient.state();
	}

	private static String phase() {
		return state() == null ? "" : state().get("phase").getAsString();
	}

	private static List<JsonObject> units() {
		List<JsonObject> out = new ArrayList<>();
		if (state() != null) for (JsonElement e : state().getAsJsonArray("units")) out.add(e.getAsJsonObject());
		return out;
	}

	private static JsonObject unit(int id) {
		for (JsonObject u : units()) if (u.get("id").getAsInt() == id) return u;
		return null;
	}

	private static JsonObject unitAt(int x, int z) {
		for (JsonObject u : units()) if (u.get("hp").getAsInt() > 0 && u.get("x").getAsInt() == x && u.get("z").getAsInt() == z) return u;
		return null;
	}

	private static boolean mine(JsonObject u) {
		return "PLAYER".equals(u.get("side").getAsString());
	}

	private Map<Integer, Integer> reach(JsonObject u) {
		BattleGrid g = BattleClient.grid();
		String side = u.get("side").getAsString();
		return Pathing.reachable(g, u.get("x").getAsInt(), u.get("z").getAsInt(), u.get("squares").getAsInt(), u.get("flies").getAsBoolean(),
				u.get("lava").getAsBoolean(), occupancy(u, side));
	}

	private Pathing.Occupancy occupancy(JsonObject self, String side) {
		return (x, z) -> {
			JsonObject o = unitAt(x, z);
			if (o == null || o.get("id").getAsInt() == self.get("id").getAsInt()) return 0;
			return side.equals(o.get("side").getAsString()) ? 1 : 2;
		};
	}

	private boolean canStop(JsonObject u, int x, int z) {
		return Pathing.canStop(BattleClient.grid(), x, z, u.get("flies").getAsBoolean(), u.get("lava").getAsBoolean(),
				occupancy(u, u.get("side").getAsString()), u.get("x").getAsInt(), u.get("z").getAsInt());
	}

	/** Whether {@code target} is a valid pick from (destX, destZ) for the pending action. */
	private boolean validTarget(JsonObject u, JsonObject target) {
		if (target == null || target.get("hp").getAsInt() <= 0) return false;
		int d = Pathing.distance(destX, destZ, target.get("x").getAsInt(), target.get("z").getAsInt());
		boolean self = target.get("id").getAsInt() == u.get("id").getAsInt();
		if (self) d = 0;
		if (pending >= 0) {
			JsonObject m = u.getAsJsonArray("moves").get(pending).getAsJsonObject();
			return !mine(target) && d >= m.get("min").getAsInt() && d <= m.get("max").getAsInt();
		}
		if (pending == -2) return mine(target) && d <= 1;
		return mine(target) && !self && d == 1 && !target.get("player").getAsBoolean();
	}

	// ---- projection: world <-> screen (the camera looks straight down, north up) ----

	private double scale(double y) {
		double fov = Math.toRadians(minecraft.gameRenderer.mainCamera().getFov());
		double f = (height / 2.0) / Math.tan(fov / 2);
		return f / Math.max(0.5, BattleClient.camY() - y);
	}

	public double[] project(double wx, double wy, double wz) {
		double s = scale(wy);
		return new double[]{width / 2.0 + (wx - BattleClient.camX()) * s, height / 2.0 + (wz - BattleClient.camZ()) * s};
	}

	/** Screen rectangle of a cell's top face: {x0, y0, x1, y1}. */
	public double[] cellRect(int x, int z) {
		BattleGrid g = BattleClient.grid();
		double y = g.y(x, z);
		double[] a = project(g.originX + x, y, g.originZ + z), b = project(g.originX + x + 1, y, g.originZ + z + 1);
		return new double[]{a[0], a[1], b[0], b[1]};
	}

	/** The cell under a screen point (the nearest to the camera if several overlap), or null. */
	public int[] cellAt(double mx, double my) {
		BattleGrid g = BattleClient.grid();
		if (g == null) return null;
		int[] best = null;
		int bestY = Integer.MIN_VALUE;
		for (int x = 0; x < g.size; x++) {
			for (int z = 0; z < g.size; z++) {
				double[] r = cellRect(x, z);
				if (mx >= r[0] && mx < r[2] && my >= r[1] && my < r[3] && g.y(x, z) > bestY) {
					bestY = g.y(x, z);
					best = new int[]{x, z};
				}
			}
		}
		return best;
	}

	// ---- input ----

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) return true;
		if (event.button() == 1) {
			back();
			return true;
		}
		int[] c = cellAt(event.x(), event.y());
		if (c != null) clickCell(c[0], c[1]);
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == 256) { // escape
			back();
			return true;
		}
		return super.keyPressed(event);
	}

	private void back() {
		switch (step) {
			case TARGET -> step = Step.ACTION;
			case ACTION -> step = Step.DESTINATION;
			default -> reset();
		}
		rebuild();
	}

	/** What a click on a battlefield square does, depending on the step. Public for the automated tests. */
	public void clickCell(int x, int z) {
		if (!"PLAYER".equals(phase())) return;
		JsonObject u = unit(selected);
		JsonObject there = unitAt(x, z);
		switch (step) {
			case UNIT, DESTINATION -> {
				if (there != null && mine(there) && !there.get("acted").getAsBoolean() && (u == null || there != u && there.get("id").getAsInt() != u.get("id").getAsInt())) {
					selected = there.get("id").getAsInt();
					step = Step.DESTINATION;
				} else if (u != null && reach(u).containsKey(x + z * BattleClient.grid().size) && canStop(u, x, z)) {
					destX = x;
					destZ = z;
					step = Step.ACTION;
				} else if (u != null && there != null && !mine(there)) {
					// clicking an enemy straight away: attack it from where the unit stands, if a move reaches
					destX = u.get("x").getAsInt();
					destZ = u.get("z").getAsInt();
					for (int i = 0; i < u.getAsJsonArray("moves").size(); i++) {
						pending = i;
						if (validTarget(u, there)) {
							sendAction("attack", there.get("id").getAsInt());
							return;
						}
					}
				} else {
					reset();
				}
			}
			case ACTION -> {
				if (there != null && !mine(there)) {
					for (int i = 0; i < u.getAsJsonArray("moves").size(); i++) {
						pending = i;
						if (validTarget(u, there)) {
							sendAction("attack", there.get("id").getAsInt());
							return;
						}
					}
				}
			}
			case TARGET -> {
				if (u != null && validTarget(u, there)) sendAction(pending >= 0 ? "attack" : pending == -2 ? "item" : "rally", there.get("id").getAsInt());
			}
		}
		rebuild();
	}

	private void sendAction(String kind, int target) {
		JsonObject a = new JsonObject();
		a.addProperty("kind", kind);
		a.addProperty("unit", selected);
		a.addProperty("x", destX);
		a.addProperty("z", destZ);
		a.addProperty("move", Math.max(pending, 0));
		a.addProperty("target", target);
		BattleClient.send(a);
		reset();
		rebuild();
	}

	public void chooseMove(int i) {
		pending = i;
		step = Step.TARGET;
		rebuild();
	}

	public void waitHere() {
		sendAction("wait", -1);
	}

	public void endTurn() {
		JsonObject a = new JsonObject();
		a.addProperty("kind", "end_turn");
		BattleClient.send(a);
		reset();
	}

	public void flee() {
		JsonObject a = new JsonObject();
		a.addProperty("kind", "flee");
		BattleClient.send(a);
		reset();
	}

	public void recruit(int index) {
		JsonObject a = new JsonObject();
		a.addProperty("kind", "recruit");
		a.addProperty("index", index);
		BattleClient.send(a);
	}

	public void leave() {
		JsonObject a = new JsonObject();
		a.addProperty("kind", "leave");
		BattleClient.send(a);
	}

	// ---- buttons ----

	private void rebuild() {
		clearWidgets();
		if (state() == null) return;
		if (state().has("result")) {
			JsonObject r = state().getAsJsonObject("result");
			JsonArray rec = r.getAsJsonArray("recruits");
			int y = height / 2 - 10;
			for (int i = 0; i < rec.size(); i++) {
				JsonObject d = rec.get(i).getAsJsonObject();
				final int idx = i;
				addRenderableWidget(Button.builder(Component.literal("Recruit " + d.get("name").getAsString() + " Lv." + d.get("level").getAsInt()
						+ " (" + d.get("cost").getAsInt() + " XP)"), b -> recruit(idx)).bounds(width / 2 - 110, y + i * 22, 220, 20).build());
			}
			addRenderableWidget(Button.builder(Component.literal("Leave battle"), b -> leave()).bounds(width / 2 - 60, y + rec.size() * 22 + 8, 120, 20).build());
			return;
		}
		boolean myTurn = "PLAYER".equals(phase());
		Button end = addRenderableWidget(Button.builder(Component.literal("End turn"), b -> endTurn()).bounds(width - 86, height - 26, 80, 20).build());
		Button run = addRenderableWidget(Button.builder(Component.literal("Flee"), b -> flee()).bounds(width - 86, height - 50, 80, 20).build());
		end.active = myTurn;
		run.active = myTurn;
		JsonObject u = unit(selected);
		if (myTurn && u != null && step == Step.ACTION) {
			int x = 6, y = height - 26;
			JsonArray moves = u.getAsJsonArray("moves");
			for (int i = 0; i < moves.size(); i++) {
				JsonObject m = moves.get(i).getAsJsonObject();
				int min = m.get("min").getAsInt(), max = m.get("max").getAsInt();
				String range = min == max ? "" + min : min + "-" + max;
				final int idx = i;
				addRenderableWidget(Button.builder(Component.literal(m.get("name").getAsString() + " (" + range + ")"), b -> chooseMove(idx)).bounds(x, y, 110, 20).build());
				x += 114;
			}
			if (u.get("player").getAsBoolean()) {
				addRenderableWidget(Button.builder(Component.literal("Item"), b -> {
					pending = -2;
					step = Step.TARGET;
					rebuild();
				}).bounds(x, y, 50, 20).build());
				x += 54;
				addRenderableWidget(Button.builder(Component.literal("Rally"), b -> {
					pending = -3;
					step = Step.TARGET;
					rebuild();
				}).bounds(x, y, 50, 20).build());
				x += 54;
			}
			addRenderableWidget(Button.builder(Component.literal("Wait"), b -> waitHere()).bounds(x, y, 50, 20).build());
		}
	}

	// ---- drawing ----

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		// no darkening: the battlefield is the background
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		if (state() == null || BattleClient.grid() == null) return;
		BattleGrid grid = BattleClient.grid();
		int[] hover = cellAt(mouseX, mouseY);
		hoverX = hover == null ? -1 : hover[0];
		hoverZ = hover == null ? -1 : hover[1];
		JsonObject u = unit(selected);

		// movement range and targets
		if (u != null && "PLAYER".equals(phase())) {
			if (step == Step.DESTINATION) {
				for (int cell : reach(u).keySet()) {
					int x = cell % grid.size, z = cell / grid.size;
					if (canStop(u, x, z)) fillCell(g, x, z, 0x553C8CFF);
				}
			} else {
				fillCell(g, destX, destZ, 0x7744AAFF);
				if (step == Step.TARGET || step == Step.ACTION) {
					for (JsonObject t : units()) {
						if (step == Step.ACTION) {
							for (int i = 0; i < u.getAsJsonArray("moves").size(); i++) {
								pending = i;
								if (validTarget(u, t)) fillCell(g, t.get("x").getAsInt(), t.get("z").getAsInt(), 0x66FF4040);
							}
							pending = 0;
						} else if (validTarget(u, t)) {
							fillCell(g, t.get("x").getAsInt(), t.get("z").getAsInt(), pending >= 0 ? 0x88FF4040 : 0x8840FF60);
						}
					}
				}
			}
		}
		if (hoverX >= 0) outlineCell(g, hoverX, hoverZ, 0xFFFFFFFF);

		// units: side frame and health bar
		for (JsonObject t : units()) {
			if (t.get("hp").getAsInt() <= 0) continue;
			int x = t.get("x").getAsInt(), z = t.get("z").getAsInt();
			int frame = mine(t) ? (t.get("acted").getAsBoolean() ? 0xFF6070A0 : 0xFF4AA8FF) : 0xFFFF5050;
			if (t.get("id").getAsInt() == selected) frame = 0xFFFFE040;
			outlineCell(g, x, z, frame);
			double[] r = cellRect(x, z);
			int bw = (int) (r[2] - r[0]);
			int filled = (int) Math.round(bw * t.get("hp").getAsDouble() / t.get("max").getAsDouble());
			g.fill((int) r[0], (int) r[1] - 4, (int) r[0] + bw, (int) r[1] - 1, 0xC0000000);
			g.fill((int) r[0], (int) r[1] - 4, (int) r[0] + filled, (int) r[1] - 1, mine(t) ? 0xFF40D060 : 0xFFE04040);
		}

		// banner and log
		String banner = state().has("result") ? "" : "Turn " + state().get("turn").getAsInt() + " - " + ("PLAYER".equals(phase()) ? "Your phase" : "Enemy phase");
		if (!banner.isEmpty()) {
			int w = font.width(banner) + 12;
			g.fill(width / 2 - w / 2, 4, width / 2 + w / 2, 18, 0xA0000000);
			g.centeredText(font, banner, width / 2, 7, "PLAYER".equals(phase()) ? 0xFF7FC8FF : 0xFFFF8080);
		}
		JsonArray log = state().getAsJsonArray("log");
		int ly = 22;
		for (int i = Math.max(0, log.size() - 6); i < log.size(); i++) {
			String line = log.get(i).getAsString();
			g.fill(4, ly - 1, 8 + font.width(line), ly + 9, 0x80000000);
			g.text(font, line, 6, ly, 0xFFFFFFFF);
			ly += 11;
		}

		// details of the selected or hovered unit
		JsonObject info = u != null ? u : unitAt(hoverX, hoverZ);
		if (info != null) {
			List<String> lines = new ArrayList<>();
			lines.add(info.get("name").getAsString() + "  Lv." + info.get("level").getAsInt() + (info.get("player").getAsBoolean() ? "" : "  " + info.get("influence").getAsString()));
			lines.add("HP " + info.get("hp").getAsInt() + "/" + info.get("max").getAsInt() + "   Moves " + info.get("squares").getAsInt() + " squares" + (info.get("flies").getAsBoolean() ? " (flies)" : ""));
			for (JsonElement me : info.getAsJsonArray("moves")) {
				JsonObject m = me.getAsJsonObject();
				lines.add("  " + m.get("name").getAsString() + ": power " + m.get("power").getAsInt() + ", range " + m.get("min").getAsInt() + "-" + m.get("max").getAsInt()
						+ ", " + m.get("kind").getAsString().toLowerCase(java.util.Locale.ROOT));
			}
			int w = 0;
			for (String s : lines) w = Math.max(w, font.width(s));
			int y0 = height - 34 - lines.size() * 10;
			g.fill(4, y0 - 3, 10 + w, y0 + lines.size() * 10, 0xA0000000);
			for (int i = 0; i < lines.size(); i++) g.text(font, lines.get(i), 7, y0 + i * 10, i == 0 ? (mine(info) ? 0xFF7FC8FF : 0xFFFF8080) : 0xFFFFFFFF);
		}
		String hint = switch (step) {
			case UNIT -> "PLAYER".equals(phase()) ? "Click one of your units" : "";
			case DESTINATION -> "Click where it should move (or itself to stay)";
			case ACTION -> "Pick a move, then a target - or click an enemy in red";
			case TARGET -> pending >= 0 ? "Click a target in red" : "Click a unit in green";
		};
		if (!hint.isEmpty() && !state().has("result")) g.centeredText(font, hint, width / 2, 20, 0xFFFFFFA0);

		// results
		if (state().has("result")) {
			JsonObject r = state().getAsJsonObject("result");
			String o = r.get("outcome").getAsString();
			String title = o.equals("WON") ? "Victory!" : o.equals("LOST") ? "Defeated" : "Escaped";
			g.fill(width / 2 - 130, height / 2 - 60, width / 2 + 130, height / 2 + 30 + r.getAsJsonArray("recruits").size() * 22, 0xC0000000);
			g.centeredText(font, title, width / 2, height / 2 - 52, o.equals("WON") ? 0xFFFFD040 : 0xFFFF8080);
			if (o.equals("WON")) {
				g.centeredText(font, "+" + r.get("xp").getAsInt() + " XP to give your creatures (/party xp)", width / 2, height / 2 - 38, 0xFFFFFFFF);
				g.centeredText(font, "+" + r.get("charXp").getAsInt() + " XP for you (/character)", width / 2, height / 2 - 27, 0xFFFFFFFF);
			}
		}
		super.extractRenderState(g, mouseX, mouseY, partialTick);
	}

	private void fillCell(GuiGraphicsExtractor g, int x, int z, int color) {
		double[] r = cellRect(x, z);
		g.fill((int) r[0] + 1, (int) r[1] + 1, (int) r[2], (int) r[3], color);
	}

	private void outlineCell(GuiGraphicsExtractor g, int x, int z, int color) {
		double[] r = cellRect(x, z);
		g.outline((int) r[0], (int) r[1], (int) (r[2] - r[0]), (int) (r[3] - r[1]), color);
	}
}
