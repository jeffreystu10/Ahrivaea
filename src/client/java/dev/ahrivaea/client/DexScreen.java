package dev.ahrivaea.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.ahrivaea.Ahrivaea;
import dev.ahrivaea.creature.CreatureEntity;
import dev.ahrivaea.party.Dex;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The creature dex: every creature form, numbered. Unknown ones are a "?" with a clue to where they live; ones you've
 * seen show their name, look, rarity, levels and habitat; ones you've caught show everything (stats, jobs, drops).
 * Filter by All / Seen / Caught, or search by name.
 */
public class DexScreen extends Screen {
	static final int TOP = 14;
	private static final int ROW = 14, LIST_W = 176, PANEL_W = 236;
	private static final String[] STAT_NAMES = {"HP", "Attack", "Defense", "Speed", "Magic", "Luck", "Charisma"};

	private enum Filter { ALL, SEEN, CAUGHT }

	private static Filter filter = Filter.ALL;
	private static int selected = 0;
	private JsonObject data;
	private int scroll;
	private String search = "";
	private EditBox searchBox;
	/** Typing in the search box rebuilds the list; keep typing where you were. */
	private boolean searching;
	private CreatureEntity preview;

	public DexScreen(JsonObject data) {
		super(Component.literal("Creature Dex"));
		this.data = data;
	}

	/** Opens the dex; the server sends what this player knows. */
	public static void open() {
		Minecraft mc = Minecraft.getInstance();
		mc.gui.setScreen(new DexScreen(null));
		ClientPlayNetworking.send(new Dex.RequestPayload());
	}

	void setData(JsonObject data) {
		this.data = data;
		rebuildWidgets();
	}

	private int left() {
		return Math.max(4, width / 2 - (LIST_W + 8 + PANEL_W) / 2);
	}

	private int panelX() {
		return left() + LIST_W + 8;
	}

	private int listTop() {
		return TOP + 40;
	}

	private int rows() {
		return Math.max(1, (height - listTop() - 30) / ROW);
	}

	/** The entries shown under the current filter and search. */
	private List<JsonObject> shown() {
		List<JsonObject> out = new ArrayList<>();
		if (data == null) return out;
		String q = search.toLowerCase(Locale.ROOT).trim();
		for (JsonElement e : data.getAsJsonArray("entries")) {
			JsonObject j = e.getAsJsonObject();
			int st = j.get("status").getAsInt();
			if (filter == Filter.SEEN && st < Dex.SEEN) continue;
			if (filter == Filter.CAUGHT && st < Dex.CAUGHT) continue;
			if (!q.isEmpty() && (!j.has("name") || !j.get("name").getAsString().toLowerCase(Locale.ROOT).contains(q))
					&& !String.valueOf(j.get("n").getAsInt()).equals(q)) continue;
			out.add(j);
		}
		return out;
	}

	@Override
	protected void init() {
		int x = left();
		for (int i = 0; i < Filter.values().length; i++) {
			Filter f = Filter.values()[i];
			Button b = addRenderableWidget(Button.builder(Component.literal(f.name().charAt(0) + f.name().substring(1).toLowerCase(Locale.ROOT)), btn -> {
				filter = f;
				scroll = 0;
				selected = 0;
				rebuildWidgets();
			}).bounds(x + i * 46, TOP + 18, 44, 16).build());
			b.active = filter != f;
		}
		searchBox = new EditBox(font, panelX(), TOP + 18, PANEL_W, 16, Component.literal("Search"));
		searchBox.setHint(Component.literal("Search by name or number..."));
		searchBox.setValue(search);
		searchBox.setResponder(v -> {
			if (v.equals(search)) return;
			search = v;
			searching = true;
			scroll = 0;
			selected = 0;
			rebuildWidgets();
		});
		addRenderableWidget(searchBox);
		if (searching) setFocused(searchBox);
		List<JsonObject> list = shown();
		selected = Math.clamp(selected, 0, Math.max(0, list.size() - 1));
		scroll = Math.clamp(scroll, 0, Math.max(0, list.size() - rows()));
		for (int i = scroll; i < Math.min(list.size(), scroll + rows()); i++) {
			final int idx = i;
			Button row = addRenderableWidget(Button.builder(Component.empty(), b -> {
				selected = idx;
				rebuildWidgets();
			}).bounds(x, listTop() + (i - scroll) * ROW, LIST_W, ROW - 1).build());
			row.active = i != selected;
		}
		addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose()).bounds(width / 2 - 40, height - 24, 80, 20).build());
		// the creature on its stand (seen or caught ones)
		Minecraft mc = Minecraft.getInstance();
		if (mc.level != null) {
			if (preview == null) {
				preview = new CreatureEntity(Ahrivaea.CREATURE, mc.level);
				preview.setId(-4243); // never added to the world, but rendering asks for an id
			}
			if (!list.isEmpty()) preview.previewAs(list.get(selected).get("species").getAsString(), list.get(selected).get("influence").getAsString());
		}
	}

	/** Types into the search box (for the automated tests). */
	public void searchFor(String text) {
		searchBox.setValue(text);
	}

	/** Selects the first shown entry with this status: 0 unknown, 1 seen, 2 caught (for the automated tests). */
	public void selectFirst(int status) {
		List<JsonObject> list = shown();
		for (int i = 0; i < list.size(); i++) {
			if (list.get(i).get("status").getAsInt() == status) {
				selected = i;
				scroll = Math.max(0, i - 2);
				break;
			}
		}
		rebuildWidgets();
	}

	public boolean loaded() {
		return data != null;
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double sx, double sy) {
		scroll -= (int) Math.signum(sy) * 3;
		rebuildWidgets();
		return true;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(g, mouseX, mouseY, partialTick);
		int x = left();
		g.text(font, "Creature Dex", x, TOP + 4, 0xFFFFD040);
		if (data == null) {
			g.centeredText(font, "Loading...", width / 2, height / 2, 0xFFAAAAAA);
			return;
		}
		int total = data.getAsJsonArray("entries").size();
		String counts = "Seen " + data.get("seen").getAsInt() + "/" + total + "   Caught " + data.get("caught").getAsInt() + "/" + total;
		g.text(font, counts, panelX() + PANEL_W - font.width(counts), TOP + 4, 0xFF9FE09F);
		List<JsonObject> list = shown();
		for (int i = scroll; i < Math.min(list.size(), scroll + rows()); i++) {
			JsonObject j = list.get(i);
			int y = listTop() + (i - scroll) * ROW;
			int st = j.get("status").getAsInt();
			String num = String.format("#%03d", j.get("n").getAsInt());
			g.text(font, num, x + 4, y + 3, 0xFF808080);
			int color = 0xFF000000 | j.get("color").getAsInt();
			if (st >= Dex.SEEN) {
				g.fill(x + 30, y + 3, x + 36, y + 9, color);
				g.text(font, font.plainSubstrByWidth(j.get("name").getAsString(), LIST_W - 54), x + 40, y + 3, 0xFFFFFFFF);
				if (st >= Dex.CAUGHT) g.text(font, "●", x + LIST_W - 10, y + 3, 0xFFFF5050);
			} else {
				g.text(font, "???", x + 40, y + 3, 0xFF606060);
			}
		}
		if (list.isEmpty()) g.text(font, filter == Filter.ALL ? "Nothing matches." : "None yet.", x + 4, listTop() + 4, 0xFFAAAAAA);
		else if (list.size() > rows()) g.text(font, "Scroll for more (" + list.size() + ")", x + 4, listTop() + rows() * ROW + 2, 0xFF808080);
		if (!list.isEmpty()) details(g, list.get(selected), panelX(), listTop(), mouseX, mouseY);
	}

	private void details(GuiGraphicsExtractor g, JsonObject j, int x, int y, int mouseX, int mouseY) {
		int st = j.get("status").getAsInt();
		int bottom = height - 30;
		int color = 0xFF000000 | j.get("color").getAsInt();
		g.fill(x - 4, y - 4, x + PANEL_W + 4, bottom, 0xC0101418);
		String num = String.format("#%03d", j.get("n").getAsInt());
		g.fill(x - 4, y - 4, x + PANEL_W + 4, y + 10, st >= Dex.SEEN ? (color & 0xFFFFFF) | 0xA0000000 : 0xA0303030);
		g.text(font, num + "  " + (st >= Dex.SEEN ? j.get("name").getAsString() : "???"), x, y - 1, 0xFFFFFFFF);
		String state = st >= Dex.CAUGHT ? "Caught" : st >= Dex.SEEN ? "Seen" : "Unknown";
		g.text(font, state, x + PANEL_W - font.width(state), y - 1, st >= Dex.CAUGHT ? 0xFFFF8080 : st >= Dex.SEEN ? 0xFF9FD0FF : 0xFF909090);
		y += 14;
		// the creature itself, or a mystery box
		int boxTop = y, boxBottom = y + (st >= Dex.CAUGHT ? 50 : 62);
		if (st >= Dex.SEEN && preview != null) {
			String id = j.get("species").getAsString(), inf = j.get("influence").getAsString();
			float size = Math.max(0.5f, Math.max(GeoModels.height(id, inf), GeoModels.extent(id, inf) * 0.7f) * GeoModels.scale(id, inf));
			float h = GeoModels.height(id, inf) * GeoModels.scale(id, inf);
			InventoryScreen.extractEntityInInventoryFollowsMouse(g, x + PANEL_W / 2 - 50, boxTop, x + PANEL_W / 2 + 50, boxBottom,
					Math.max(2, Math.round((boxBottom - boxTop - 8) / size)), h / 2 - 0.4f, mouseX, mouseY, preview);
		} else {
			g.fill(x + PANEL_W / 2 - 24, boxTop + 4, x + PANEL_W / 2 + 24, boxBottom - 4, 0xFF202020);
			g.pose().pushMatrix();
			g.pose().translate(x + PANEL_W / 2f, boxTop + 18);
			g.pose().scale(3f, 3f);
			g.centeredText(font, "?", 0, 0, 0xFF707070);
			g.pose().popMatrix();
		}
		y = boxBottom + 4;
		if (st < Dex.SEEN) {
			y = wrap(g, j.get("hint").getAsString() + ".", x, y, 0xFF9FD0FF);
			wrap(g, "Find one to learn its name; catch or recruit one to learn everything about it.", x, y + 4, 0xFF909090);
			return;
		}
		g.text(font, j.get("influenceName").getAsString() + " - " + j.get("rarity").getAsString(), x, y, color);
		String lv = "Lv." + j.get("levels").getAsString();
		g.text(font, lv, x + PANEL_W - font.width(lv), y, 0xFFDDDDDD);
		y += 11;
		StringBuilder where = new StringBuilder();
		for (JsonElement b : j.getAsJsonArray("biomes")) where.append(where.isEmpty() ? "" : ", ").append(b.getAsString());
		String move = j.get("movement").getAsString();
		y = wrap(g, (move.equals("fly") ? "Flies" : move.equals("swim") ? "Swims" : "Walks") + ". Lives in " + (where.isEmpty() ? "no wild place" : where) + ".", x, y, 0xFF9FD0FF);
		if (st < Dex.CAUGHT) {
			wrap(g, "Catch or recruit one to see its stats, jobs and drops.", x, y + 4, 0xFF909090);
			return;
		}
		y += 3;
		JsonArray stats = j.getAsJsonArray("stats");
		for (int i = 0; i < stats.size() && y < bottom - 30; i++) {
			int v = stats.get(i).getAsInt();
			int col = i % 2, sx = x + col * (PANEL_W / 2);
			g.text(font, STAT_NAMES[i], sx, y, 0xFFFFFFFF);
			PartyClient.bar(g, sx + 48, y + 2, 40, 5, v / 100f, 0xFFFFC040);
			g.text(font, String.valueOf(v), sx + 92, y, 0xFFFFD27F);
			if (col == 1 || i == stats.size() - 1) y += 10;
		}
		g.text(font, "Total " + j.get("total").getAsInt() + ", moves " + j.get("squares").getAsInt() + " squares" + (j.get("rideable").getAsBoolean() ? ", rideable" : ""), x, y + 1, 0xFFDDDDDD);
		y = wrap(g, "Jobs: " + j.get("jobs").getAsString(), x, y + 12, 0xFF9FC8FF);
		StringBuilder drops = new StringBuilder();
		for (JsonElement d : j.getAsJsonArray("drops")) drops.append(drops.isEmpty() ? "" : ", ").append(d.getAsString());
		if (y < bottom - 10) wrap(g, "Drops: " + (drops.isEmpty() ? "nothing" : drops), x, y, 0xFF9FE09F);
	}

	private int wrap(GuiGraphicsExtractor g, String text, int x, int y, int color) {
		for (var line : font.split(Component.literal(text), PANEL_W)) {
			if (y > height - 40) break;
			g.text(font, line, x, y, color);
			y += 10;
		}
		return y;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
