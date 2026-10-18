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
 * The battle seen from above. Click one of your units (on the field or its card on the left), click where it
 * should go (or itself to stay), then pick what it does from the menu beside it: a move (then its target), an item
 * or rally (you only), or wait. Right-click or Escape goes back a step. Blue squares: where it can move. Red: what
 * it can hit from there. Drag to look around, scroll to zoom in and out (towards the mouse), R for the whole field.
 */
public class BattleScreen extends Screen {
	/** Everything along the top edge sits this much lower, clear of the F3 debug text. */
	static final int TOP = 14;
	private enum Step { UNIT, DESTINATION, ACTION, TARGET }

	private Step step = Step.UNIT;
	private int selected = -1;
	private int destX, destZ;
	/** In the TARGET step: a move index, or -2 for item, -3 for rally. */
	private int pending;
	private int hoverX = -1, hoverZ = -1;
	/** A left click that turns into a drag pans the camera instead of clicking a square. */
	private boolean pressed, dragging;
	private double pressX, pressY;
	/** Unit cards: id -> screen rectangle {x0, y0, x1, y1}, set while drawing. */
	private final Map<Integer, int[]> cards = new java.util.HashMap<>();
	private int hoveredCard = -1;
	/** Damage and healing numbers floating up from the squares where they happened. */
	private record Popup(double wx, double wy, double wz, String text, int color, long start) {}
	private final List<Popup> popups = new ArrayList<>();
	private final Map<Integer, Integer> lastHp = new java.util.HashMap<>();
	private static final int CARD_W = 118, CARD_H = 24;

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
		noteHp();
		rebuild();
	}

	/** Numbers for every change in health since the last update. */
	private void noteHp() {
		BattleGrid g = BattleClient.grid();
		for (JsonObject u : units()) {
			int id = u.get("id").getAsInt(), hp = u.get("hp").getAsInt();
			Integer before = lastHp.put(id, hp);
			if (before == null || before == hp || g == null) continue;
			int x = u.get("x").getAsInt(), z = u.get("z").getAsInt();
			int diff = hp - before;
			popups.add(new Popup(g.originX + x + 0.5, g.y(x, z), g.originZ + z + 0.5, (diff > 0 ? "+" : "") + diff + (hp <= 0 ? " KO" : ""),
					diff > 0 ? 0xFF60FF80 : 0xFFFF5040, net.minecraft.util.Util.getMillis()));
		}
	}

	void refresh() {
		noteHp();
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
		if (event.button() == 0) {
			pressed = true;
			dragging = false;
			pressX = event.x();
			pressY = event.y();
		}
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (!pressed || event.button() != 0) return super.mouseDragged(event, dx, dy);
		if (!dragging && Math.hypot(event.x() - pressX, event.y() - pressY) > 4) dragging = true;
		if (dragging && BattleClient.grid() != null) {
			double s = scale(BattleClient.grid().y(BattleClient.grid().size / 2, BattleClient.grid().size / 2));
			BattleClient.pan(-dx / s, -dy / s);
		}
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (pressed && event.button() == 0) {
			pressed = false;
			if (!dragging) click(event.x(), event.y());
			dragging = false;
			return true;
		}
		return super.mouseReleased(event);
	}

	/** A click (not a drag): a unit card, else a battlefield square. */
	private void click(double mx, double my) {
		for (var e : cards.entrySet()) {
			int[] r = e.getValue();
			if (mx >= r[0] && mx < r[2] && my >= r[1] && my < r[3]) {
				clickCard(e.getKey());
				return;
			}
		}
		int[] c = cellAt(mx, my);
		if (c != null) clickCell(c[0], c[1]);
	}

	/** A unit's card: look at it, and pick it if it's one of yours that can still act (or target it). */
	private void clickCard(int id) {
		JsonObject t = unit(id);
		if (t == null) return;
		int x = t.get("x").getAsInt(), z = t.get("z").getAsInt();
		BattleClient.centerOn(x, z);
		if (t.get("hp").getAsInt() > 0 && (step == Step.TARGET || step == Step.ACTION || (mine(t) && !t.get("acted").getAsBoolean()))) clickCell(x, z);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double sx, double sy) {
		BattleGrid g = BattleClient.grid();
		if (g == null) return true;
		// zoom towards the point under the mouse
		double s = scale(g.y(g.size / 2, g.size / 2));
		BattleClient.zoomAt(sy, BattleClient.camX() + (mx - width / 2.0) / s, BattleClient.camZ() + (my - height / 2.0) / s);
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int k = event.key();
		if (k == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE) {
			back();
			return true;
		}
		double d = Math.max(1, (BattleClient.camY() - BattleClient.minHeight()) / 6 + 1);
		if (k == com.mojang.blaze3d.platform.InputConstants.KEY_W || k == com.mojang.blaze3d.platform.InputConstants.KEY_UP) BattleClient.pan(0, -d);
		else if (k == com.mojang.blaze3d.platform.InputConstants.KEY_S || k == com.mojang.blaze3d.platform.InputConstants.KEY_DOWN) BattleClient.pan(0, d);
		else if (k == com.mojang.blaze3d.platform.InputConstants.KEY_A || k == com.mojang.blaze3d.platform.InputConstants.KEY_LEFT) BattleClient.pan(-d, 0);
		else if (k == com.mojang.blaze3d.platform.InputConstants.KEY_D || k == com.mojang.blaze3d.platform.InputConstants.KEY_RIGHT) BattleClient.pan(d, 0);
		else if (k == com.mojang.blaze3d.platform.InputConstants.KEY_R) BattleClient.resetView();
		else if (k == com.mojang.blaze3d.platform.InputConstants.KEY_SPACE) {
			JsonObject u = unit(selected);
			if (u != null) BattleClient.centerOn(u.get("x").getAsInt(), u.get("z").getAsInt());
		} else return super.keyPressed(event);
		return true;
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
			int y = resultTop() + 84;
			for (int i = 0; i < rec.size(); i++) {
				JsonObject d = rec.get(i).getAsJsonObject();
				final int idx = i;
				String rarity = d.has("rarity") ? d.get("rarity").getAsString() + " " : "";
				boolean blocked = d.has("why");
				Button rb = addRenderableWidget(Button.builder(Component.literal("Recruit " + rarity + d.get("name").getAsString() + " Lv." + d.get("level").getAsInt()
						+ " (" + d.get("cost").getAsInt() + " XP)"), b -> recruit(idx)).bounds(width / 2 - 140, y + i * 22, 280, 20).build());
				if (blocked) {
					rb.active = false;
					rb.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Can't recruit: " + d.get("why").getAsString())));
					rb.setMessage(Component.literal(d.get("name").getAsString() + " Lv." + d.get("level").getAsInt() + ": " + d.get("why").getAsString()));
				}
			}
			addRenderableWidget(Button.builder(Component.literal("Leave battle"), b -> leave()).bounds(width / 2 - 60, y + rec.size() * 22 + 6, 120, 20).build());
			return;
		}
		boolean myTurn = "PLAYER".equals(phase());
		Button end = addRenderableWidget(Button.builder(Component.literal("End turn"), b -> endTurn()).bounds(width - 86, height - 26, 80, 20).build());
		Button run = addRenderableWidget(Button.builder(Component.literal("Flee"), b -> flee()).bounds(width - 86, height - 50, 80, 20).build());
		end.active = myTurn;
		run.active = true; // fleeing works in either phase
		JsonObject u = unit(selected);
		if (myTurn && u != null && step == Step.ACTION) {
			// the action menu, beside the square it's moving to (like Fire Emblem)
			List<String> labels = new ArrayList<>();
			List<Runnable> actions = new ArrayList<>();
			JsonArray moves = u.getAsJsonArray("moves");
			for (int i = 0; i < moves.size(); i++) {
				JsonObject m = moves.get(i).getAsJsonObject();
				int min = m.get("min").getAsInt(), max = m.get("max").getAsInt();
				final int idx = i;
				labels.add(m.get("name").getAsString() + "  " + (min == max ? "" + min : min + "-" + max));
				actions.add(() -> chooseMove(idx));
			}
			if (u.get("player").getAsBoolean()) {
				labels.add("Item");
				actions.add(() -> {
					pending = -2;
					step = Step.TARGET;
					rebuild();
				});
				labels.add("Rally");
				actions.add(() -> {
					pending = -3;
					step = Step.TARGET;
					rebuild();
				});
			}
			labels.add("Wait");
			actions.add(this::waitHere);
			int[] at = menuPos(labels.size());
			for (int i = 0; i < labels.size(); i++) {
				Runnable act = actions.get(i);
				addRenderableWidget(Button.builder(Component.literal(labels.get(i)), b -> act.run()).bounds(at[0], at[1] + i * 18, MENU_W, 17).build());
			}
		}
	}

	private static final int MENU_W = 96;

	/** Where the action menu goes: to the right of the destination square, kept on screen. */
	private int[] menuPos(int items) {
		double[] r = cellRect(destX, destZ);
		int x = (int) r[2] + 6, y = (int) r[1] - 4;
		if (x + MENU_W > width - 90) x = (int) r[0] - 6 - MENU_W;
		return new int[]{Math.clamp(x, CARD_W + 10, Math.max(CARD_W + 10, width - 90 - MENU_W)), Math.clamp(y, TOP + 46, Math.max(TOP + 46, height - 30 - items * 18))};
	}

	private int resultTop() {
		int recruits = state().getAsJsonObject("result").getAsJsonArray("recruits").size();
		return Math.max(TOP + 10, height / 2 - (84 + recruits * 22 + 30) / 2);
	}

	// ---- drawing ----

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		// no darkening: the battlefield is the background
	}

	@Override
	public void tick() {
		super.tick();
		long now = net.minecraft.util.Util.getMillis();
		popups.removeIf(p -> now - p.start() > 1400);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		if (state() == null || BattleClient.grid() == null) return;
		BattleGrid grid = BattleClient.grid();
		boolean over = cards.values().stream().anyMatch(r -> mouseX >= r[0] && mouseX < r[2] && mouseY >= r[1] && mouseY < r[3]);
		int[] hover = over ? null : cellAt(mouseX, mouseY);
		hoverX = hover == null ? -1 : hover[0];
		hoverZ = hover == null ? -1 : hover[1];
		JsonObject u = unit(selected);
		boolean result = state().has("result");

		// movement range and targets
		if (u != null && "PLAYER".equals(phase()) && !result) {
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
		if (hoverX >= 0 && !result) outlineCell(g, hoverX, hoverZ, 0xFFFFFFFF, 1);

		// units: a coloured base, a frame and a health bar over each
		long now = net.minecraft.util.Util.getMillis();
		for (JsonObject t : units()) {
			if (t.get("hp").getAsInt() <= 0) continue;
			int x = t.get("x").getAsInt(), z = t.get("z").getAsInt();
			int id = t.get("id").getAsInt();
			boolean acted = mine(t) && t.get("acted").getAsBoolean();
			int frame = mine(t) ? (acted ? 0xFF6070A0 : 0xFF4AA8FF) : 0xFFFF5050;
			if (id == selected) frame = (now / 250) % 2 == 0 ? 0xFFFFE040 : 0xFFFFFFA0;
			else if (id == hoveredCard) frame = 0xFFFFFFFF;
			fillCell(g, x, z, mine(t) ? (acted ? 0x30404060 : 0x303C8CFF) : 0x30FF4040);
			outlineCell(g, x, z, frame, 2);
			double[] r = cellRect(x, z);
			int bw = Math.max(16, (int) (r[2] - r[0]));
			int bx = (int) ((r[0] + r[2]) / 2) - bw / 2, by = (int) r[1] - 7;
			double frac = t.get("hp").getAsDouble() / t.get("max").getAsDouble();
			g.fill(bx - 1, by - 1, bx + bw + 1, by + 4, 0xE0000000);
			g.fill(bx, by, bx + (int) Math.round(bw * frac), by + 3, hpColor(frac, mine(t)));
		}

		// floating damage and healing numbers
		for (Popup p : popups) {
			double age = (now - p.start()) / 1400.0;
			double[] at = project(p.wx(), p.wy() + 1, p.wz());
			int alpha = (int) (255 * Math.clamp(1.4 - age * 1.4, 0, 1));
			if (alpha < 8) continue;
			g.pose().pushMatrix();
			g.pose().translate((float) at[0], (float) (at[1] - 10 - age * 24));
			g.pose().scale(1.5f, 1.5f);
			g.centeredText(font, p.text(), 0, 0, (alpha << 24) | (p.color() & 0xFFFFFF));
			g.pose().popMatrix();
		}

		// turn banner
		if (!result) {
			boolean mineTurn = "PLAYER".equals(phase());
			String banner = mineTurn ? "YOUR TURN" : "ENEMY TURN";
			String sub = "Turn " + state().get("turn").getAsInt();
			int bw = Math.max(170, font.width(banner) * 2 + 40), bx = width / 2 - bw / 2, by = TOP + 2;
			int c1 = mineTurn ? 0xE0204A90 : 0xE0902020, c2 = mineTurn ? 0x80102850 : 0x80501010;
			g.fillGradient(bx, by, bx + bw, by + 26, c1, c2);
			g.fill(bx, by + 26, bx + bw, by + 27, mineTurn ? 0xFF7FC8FF : 0xFFFF8080);
			g.pose().pushMatrix();
			g.pose().translate(width / 2f, by + 4);
			g.pose().scale(2f, 2f);
			g.centeredText(font, banner, 0, 0, 0xFFFFFFFF);
			g.pose().popMatrix();
			g.text(font, sub, bx + bw - font.width(sub) - 4, by + 17, 0xFFDDDDDD);
			String hint = switch (step) {
				case UNIT -> mineTurn ? "Pick one of your creatures (or you)" : "The wild creatures are moving...";
				case DESTINATION -> "Click where it should move (or itself to stay)";
				case ACTION -> "Pick an action - or click an enemy in red";
				case TARGET -> pending >= 0 ? "Click a target in red" : "Click a unit in green";
			};
			int hw = font.width(hint) + 12;
			g.fill(width / 2 - hw / 2, by + 29, width / 2 + hw / 2, by + 41, 0xA0000000);
			g.centeredText(font, hint, width / 2, by + 31, 0xFFFFFFA0);
		}

		// the two sides as cards: yours on the left, the wild ones on the right
		cards.clear();
		hoveredCard = -1;
		if (!result) {
			drawRoster(g, units().stream().filter(BattleScreen::mine).toList(), 4, "YOUR TEAM", 0xFF7FC8FF, mouseX, mouseY);
			drawRoster(g, units().stream().filter(t -> !mine(t)).toList(), width - CARD_W - 4, "WILD", 0xFFFF8080, mouseX, mouseY);
		}

		// what happened lately, bottom middle
		if (!result) {
			JsonArray log = state().getAsJsonArray("log");
			int lines = Math.min(3, log.size()), ly = height - 6 - lines * 10;
			for (int i = log.size() - lines; i < log.size(); i++) {
				String line = log.get(i).getAsString();
				if (font.width(line) > width - 2 * CARD_W - 120) line = font.plainSubstrByWidth(line, width - 2 * CARD_W - 130) + "...";
				int fade = i == log.size() - 1 ? 0xFFFFFFFF : 0xFFB0B0B0;
				int lw = font.width(line);
				g.fill(width / 2 - lw / 2 - 4, ly - 1, width / 2 + lw / 2 + 4, ly + 9, 0x90000000);
				g.centeredText(font, line, width / 2, ly, fade);
				ly += 10;
			}
		}

		// details of the selected or hovered unit, bottom left
		JsonObject info = u != null ? u : hoveredCard >= 0 ? unit(hoveredCard) : unitAt(hoverX, hoverZ);
		if (info != null && !result) drawDetails(g, info, 4, height - 4);

		// results
		if (result) drawResult(g);
		super.extractRenderState(g, mouseX, mouseY, partialTick);
	}

	private static int hpColor(double frac, boolean mine) {
		if (frac <= 0.25) return 0xFFE04040;
		if (frac <= 0.5) return 0xFFE0C040;
		return mine ? 0xFF40D060 : 0xFFE07040;
	}

	private void drawRoster(GuiGraphicsExtractor g, List<JsonObject> side, int x, String title, int titleColor, int mouseX, int mouseY) {
		int y = TOP + 48;
		g.fill(x, y - 11, x + CARD_W, y - 1, 0xA0000000);
		g.text(font, title, x + 4, y - 10, titleColor);
		for (JsonObject t : side) {
			int id = t.get("id").getAsInt(), hp = Math.max(0, t.get("hp").getAsInt()), max = t.get("max").getAsInt();
			boolean down = hp <= 0, acted = mine(t) && t.get("acted").getAsBoolean();
			boolean hovered = mouseX >= x && mouseX < x + CARD_W && mouseY >= y && mouseY < y + CARD_H;
			if (hovered) hoveredCard = id;
			cards.put(id, new int[]{x, y, x + CARD_W, y + CARD_H});
			int color = 0xFF000000 | t.get("color").getAsInt();
			g.fill(x, y, x + CARD_W, y + CARD_H, down ? 0xB0201010 : hovered ? 0xD0303848 : 0xC0101418);
			g.fill(x, y, x + 3, y + CARD_H, t.get("player").getAsBoolean() ? 0xFFFFFFFF : color);
			if (id == selected) g.outline(x, y, CARD_W, CARD_H, 0xFFFFE040);
			String name = t.get("name").getAsString();
			String lv = t.get("player").getAsBoolean() ? "" : "Lv." + t.get("level").getAsInt();
			int room = CARD_W - 10 - font.width(lv);
			if (font.width(name) > room) name = font.plainSubstrByWidth(name, room - 6) + "..";
			g.text(font, name, x + 6, y + 3, down ? 0xFF808080 : acted ? 0xFF9098B0 : 0xFFFFFFFF);
			g.text(font, lv, x + CARD_W - 3 - font.width(lv), y + 3, 0xFFCCCCCC);
			double frac = max <= 0 ? 0 : hp / (double) max;
			g.fill(x + 6, y + 14, x + CARD_W - 40, y + 19, 0xFF303030);
			g.fill(x + 6, y + 14, x + 6 + (int) Math.round((CARD_W - 46) * frac), y + 19, down ? 0xFF505050 : hpColor(frac, mine(t)));
			String hpText = down ? "KO" : hp + "/" + max;
			g.text(font, hpText, x + CARD_W - 3 - font.width(hpText), y + 13, down ? 0xFFFF6060 : 0xFFDDDDDD);
			if (acted && !down) g.text(font, "done", x + CARD_W - 3 - font.width("done") - font.width(lv) - 4, y + 3, 0xFF707890);
			y += CARD_H + 2;
		}
	}

	/** A unit's card in full: name and influence, health, movement and its moves. */
	private void drawDetails(GuiGraphicsExtractor g, JsonObject t, int x, int bottom) {
		List<String> moves = new ArrayList<>();
		for (JsonElement me : t.getAsJsonArray("moves")) {
			JsonObject m = me.getAsJsonObject();
			int min = m.get("min").getAsInt(), max = m.get("max").getAsInt();
			moves.add(m.get("name").getAsString() + "  pow " + m.get("power").getAsInt() + "  range " + (min == max ? "" + min : min + "-" + max)
					+ "  " + m.get("kind").getAsString().toLowerCase(java.util.Locale.ROOT));
		}
		String move = "Moves " + t.get("squares").getAsInt() + " squares" + (t.get("flies").getAsBoolean() ? ", flies" : "") + (t.get("lava").getAsBoolean() ? ", crosses lava" : "");
		int w = Math.max(150, font.width(move) + 10);
		for (String m : moves) w = Math.max(w, font.width(m) + 14);
		w = Math.min(w, width / 2 - 20);
		int h = 36 + moves.size() * 10 + 4, y = bottom - h;
		int color = 0xFF000000 | t.get("color").getAsInt();
		g.fill(x, y, x + w, y + h, 0xD0101418);
		g.fill(x, y, x + w, y + 13, t.get("player").getAsBoolean() ? 0xFF404858 : (color & 0x00FFFFFF) | 0xC0000000);
		String title = t.get("name").getAsString() + (t.get("player").getAsBoolean() ? "" : "  Lv." + t.get("level").getAsInt() + "  " + t.get("influence").getAsString());
		g.text(font, title, x + 4, y + 3, 0xFFFFFFFF);
		int hp = Math.max(0, t.get("hp").getAsInt()), max = t.get("max").getAsInt();
		double frac = max <= 0 ? 0 : hp / (double) max;
		g.fill(x + 4, y + 17, x + w - 60, y + 22, 0xFF303030);
		g.fill(x + 4, y + 17, x + 4 + (int) Math.round((w - 64) * frac), y + 22, hpColor(frac, mine(t)));
		g.text(font, "HP " + hp + "/" + max, x + w - 56, y + 16, 0xFFDDDDDD);
		g.text(font, move, x + 4, y + 25, 0xFF9FD0FF);
		for (int i = 0; i < moves.size(); i++) g.text(font, "• " + moves.get(i), x + 4, y + 36 + i * 10, 0xFFFFD27F);
	}

	private void drawResult(GuiGraphicsExtractor g) {
		JsonObject r = state().getAsJsonObject("result");
		String o = r.get("outcome").getAsString();
		boolean won = o.equals("WON");
		String title = won ? "VICTORY!" : o.equals("LOST") ? "DEFEATED" : "ESCAPED";
		int recruits = r.getAsJsonArray("recruits").size();
		int w = 300, x = width / 2 - w / 2, y = resultTop(), h = 84 + recruits * 22 + 30;
		g.fill(x, y, x + w, y + h, 0xD0101418);
		g.fillGradient(x, y, x + w, y + 30, won ? 0xF0A07010 : o.equals("LOST") ? 0xF0802020 : 0xF0405060, 0x60000000);
		g.pose().pushMatrix();
		g.pose().translate(width / 2f, y + 6);
		g.pose().scale(2f, 2f);
		g.centeredText(font, title, 0, 0, won ? 0xFFFFE070 : 0xFFFFFFFF);
		g.pose().popMatrix();
		if (won) {
			g.centeredText(font, "+" + r.get("xp").getAsInt() + " XP for your creatures   +" + r.get("charXp").getAsInt() + " XP for you", width / 2, y + 38, 0xFFFFFFFF);
			StringBuilder drops = new StringBuilder();
			JsonArray loot = r.has("loot") ? r.getAsJsonArray("loot") : new JsonArray();
			for (int i = 0; i < loot.size(); i++) drops.append(i > 0 ? ", " : "").append(loot.get(i).getAsString());
			String line = loot.isEmpty() ? "No drops" : "Drops: " + drops;
			if (font.width(line) > w - 16) line = font.plainSubstrByWidth(line, w - 26) + "...";
			g.centeredText(font, line, width / 2, y + 52, 0xFF9FE09F);
			if (recruits > 0) g.centeredText(font, "Recruit one of them (costs creature XP):", width / 2, y + 68, 0xFFAAAAAA);
		} else {
			g.centeredText(font, o.equals("LOST") ? "You were knocked out. Your creatures keep their damage." : "You got away safely.", width / 2, y + 44, 0xFFDDDDDD);
		}
	}

	private void fillCell(GuiGraphicsExtractor g, int x, int z, int color) {
		double[] r = cellRect(x, z);
		g.fill((int) r[0] + 1, (int) r[1] + 1, (int) r[2], (int) r[3], color);
	}

	private void outlineCell(GuiGraphicsExtractor g, int x, int z, int color, int thickness) {
		double[] r = cellRect(x, z);
		for (int i = 0; i < thickness; i++)
			g.outline((int) r[0] + i, (int) r[1] + i, (int) (r[2] - r[0]) - 2 * i, (int) (r[3] - r[1]) - 2 * i, color);
	}
}
