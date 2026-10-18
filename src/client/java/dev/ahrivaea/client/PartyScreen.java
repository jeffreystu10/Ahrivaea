package dev.ahrivaea.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The party screen: your party, the creatures you've sent out and your box on the left (click one), and everything
 * about the chosen one on the right (HP, XP, all seven stats now and at level 50, jobs, riding) with what you can do
 * with it: send it out, level it up with your XP, move it to or from the box, call it back, or release it.
 * The buttons run the /party commands, so they behave exactly the same.
 */
public class PartyScreen extends Screen {
	/** Everything sits this much lower, clear of the F3 debug text. */
	static final int TOP = 14;
	enum Tab {
		PARTY("Party"), OUT("Out"), BOX("Box");

		final String title;

		Tab(String title) {
			this.title = title;
		}

		String key() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	private static Tab tab = Tab.PARTY;
	private static int selected = 0;
	private int scroll;
	/** Release asks twice. */
	private boolean confirmRelease;

	private static final String[] STAT_NAMES = {"HP", "Attack", "Defense", "Speed", "Magic", "Luck", "Charisma"};
	private static final int CARD_H = 24;

	public PartyScreen() {
		super(Component.literal("Party"));
	}

	void refresh() {
		rebuildWidgets();
	}

	private JsonArray list() {
		return PartyClient.state.getAsJsonArray(tab.key());
	}

	private JsonObject chosen() {
		JsonArray l = list();
		if (l.isEmpty()) return null;
		selected = Math.clamp(selected, 0, l.size() - 1);
		return l.get(selected).getAsJsonObject();
	}

	private int left() {
		return Math.max(6, width / 2 - 205);
	}

	private int right() {
		return left() + 160;
	}

	private int rows() {
		return Math.max(1, (height - 46 - TOP - 32) / CARD_H);
	}

	private void command(String cmd) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) mc.player.connection.sendCommand(cmd);
		confirmRelease = false;
	}

	@Override
	protected void init() {
		if (PartyClient.state == null) {
			onClose();
			return;
		}
		int x = left();
		// tabs
		for (int i = 0; i < Tab.values().length; i++) {
			Tab t = Tab.values()[i];
			int n = PartyClient.state.getAsJsonArray(t.key()).size();
			Button b = addRenderableWidget(Button.builder(Component.literal(t.title + " (" + n + ")"), btn -> {
				tab = t;
				selected = 0;
				scroll = 0;
				confirmRelease = false;
				rebuildWidgets();
			}).bounds(x + i * 51, 20 + TOP, 49, 18).build());
			b.active = tab != t;
		}
		// the creatures in this tab, as cards to click
		JsonArray l = list();
		scroll = Math.clamp(scroll, 0, Math.max(0, l.size() - rows()));
		for (int i = scroll; i < Math.min(l.size(), scroll + rows()); i++) {
			final int idx = i;
			Button card = addRenderableWidget(Button.builder(Component.empty(), btn -> {
				selected = idx;
				confirmRelease = false;
				rebuildWidgets();
			}).bounds(x, 44 + TOP + (i - scroll) * CARD_H, 152, CARD_H - 2).build());
			card.active = idx != selected;
		}
		// actions for the chosen one
		JsonObject c = chosen();
		int rx = right(), by = height - 50;
		if (c != null) {
			int slot = selected + 1;
			switch (tab) {
				case PARTY -> {
					addRenderableWidget(Button.builder(Component.literal("Send out"), b -> command("party send " + slot)).bounds(rx, by, 54, 20).build());
					int need = c.get("xpNext").getAsInt() - c.get("xp").getAsInt();
					int pool = PartyClient.state.get("xp").getAsInt();
					Button up = addRenderableWidget(Button.builder(Component.literal("Level up"), b -> command("party xp " + slot + " " + need))
							.tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Spend " + need + " of your XP to raise it one level")))
							.bounds(rx + 57, by, 76, 20).build());
					up.active = pool >= need && c.get("level").getAsInt() < 50;
					addRenderableWidget(Button.builder(Component.literal("To box"), b -> command("party box store " + slot)).bounds(rx + 136, by, 46, 20).build());
					addRenderableWidget(Button.builder(Component.literal(confirmRelease ? "Release?" : "Release"), b -> {
						if (confirmRelease) command("party release " + slot);
						else {
							confirmRelease = true;
							rebuildWidgets();
						}
					}).bounds(rx + 185, by, 59, 20).build());
				}
				case OUT -> addRenderableWidget(Button.builder(Component.literal("Call back"), b -> command("party recall " + c.get("entity").getAsInt()))
						.bounds(rx, by, 90, 20).build());
				case BOX -> {
					Button take = addRenderableWidget(Button.builder(Component.literal("To party"), b -> command("party box take " + slot)).bounds(rx, by, 90, 20).build());
					take.active = PartyClient.state.getAsJsonArray("party").size() < 6;
				}
			}
		}
		// feed it the food in your hand (outside battle food heals; in battle only potions do)
		if (c != null && tab != Tab.BOX) {
			Minecraft mc = Minecraft.getInstance();
			boolean food = mc.player != null && mc.player.getMainHandItem().has(net.minecraft.core.component.DataComponents.FOOD);
			int slot = selected + 1;
			String cmd = tab == Tab.PARTY ? "party feed " + slot : "party feed out " + (c.has("entity") ? c.get("entity").getAsInt() : -1);
			Button feed = addRenderableWidget(Button.builder(Component.literal("Feed"), b -> command(cmd)).bounds(right() + 208, 22 + TOP + 13, 36, 12)
					.tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(food
							? "Feed it the " + mc.player.getMainHandItem().getHoverName().getString() + " in your hand"
							: "Hold food in your main hand to feed it (food heals outside battle)"))).build());
			int hp = c.get("hp").getAsInt(), max = c.has("maxHp") ? c.get("maxHp").getAsInt() : hp;
			feed.active = food && hp < max;
		}
		addRenderableWidget(Button.builder(Component.literal("Summary: " + (PartyClient.showSummary ? "on" : "off")), b -> {
			PartyClient.setShowSummary(!PartyClient.showSummary);
			rebuildWidgets();
		}).bounds(x, height - 26, 100, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Dex"), b -> DexScreen.open()).bounds(x + 104, height - 26, 48, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose()).bounds(width / 2 - 40, height - 26, 80, 20).build());
	}

	@Override
	public boolean mouseScrolled(double x, double y, double dx, double dy) {
		scroll -= (int) Math.signum(dy);
		rebuildWidgets();
		return true;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(g, mouseX, mouseY, partialTick);
		if (PartyClient.state == null) return;
		int x = left(), rx = right();
		g.text(font, "Party", x, 6 + TOP, 0xFFFFD040);
		String pool = "XP to give out: " + PartyClient.state.get("xp").getAsInt();
		g.text(font, pool, rx + 244 - font.width(pool), 6 + TOP, 0xFF9FE09F);
		JsonArray l = list();
		for (int i = scroll; i < Math.min(l.size(), scroll + rows()); i++) {
			JsonObject c = l.get(i).getAsJsonObject();
			int y = 44 + TOP + (i - scroll) * CARD_H;
			String lv = "Lv." + c.get("level").getAsInt();
			String name = c.get("name").getAsString();
			int room = 146 - font.width(lv) - 8;
			if (font.width(name) > room) name = font.plainSubstrByWidth(name, room - 6) + "..";
			g.text(font, name, x + 4, y + 3, 0xFF000000 | c.get("color").getAsInt());
			g.text(font, lv, x + 148 - font.width(lv), y + 3, 0xFFFFFFFF);
			int hp = c.get("hp").getAsInt(), max = c.has("maxHp") ? c.get("maxHp").getAsInt() : hp;
			PartyClient.bar(g, x + 4, y + 14, 80, 4, max <= 0 ? 0 : hp / (float) max, PartyClient.hpColor(hp, max));
			g.text(font, hp + "/" + max, x + 88, y + 12, 0xFFDDDDDD);
		}
		if (l.isEmpty()) g.text(font, switch (tab) {
			case PARTY -> "Empty. Catch creatures with a Vessel.";
			case OUT -> "None out. Send one from your party.";
			case BOX -> "Empty. Extras go here.";
		}, x, 48 + TOP, 0xFFAAAAAA);
		else if (l.size() > rows()) g.text(font, "(scroll for more)", x, 44 + TOP + rows() * CARD_H, 0xFF888888);
		details(g, chosen(), rx, 22 + TOP);
	}

	/** Everything about one creature, in the right-hand column. */
	private void details(GuiGraphicsExtractor g, JsonObject c, int x, int y) {
		if (c == null) return;
		int w = 244;
		g.fill(x - 4, y - 4, x + w + 4, height - 54, 0x80000000);
		g.text(font, c.get("name").getAsString(), x, y, 0xFF000000 | c.get("color").getAsInt());
		String lv = "Lv." + c.get("level").getAsInt() + "  " + c.get("influence").getAsString();
		g.text(font, lv, x + w - font.width(lv), y, 0xFFFFFFFF);
		y += 14;
		int hp = c.get("hp").getAsInt(), max = c.has("maxHp") ? c.get("maxHp").getAsInt() : hp;
		g.text(font, "HP", x, y, 0xFFFFFFFF);
		PartyClient.bar(g, x + 24, y + 2, 120, 5, max <= 0 ? 0 : hp / (float) max, PartyClient.hpColor(hp, max));
		g.text(font, hp + "/" + max, x + 150, y, 0xFFDDDDDD);
		y += 12;
		int perLevel = c.has("xpNext") ? c.get("xpNext").getAsInt() : 100;
		g.text(font, "XP", x, y, 0xFFFFFFFF);
		PartyClient.bar(g, x + 24, y + 2, 120, 5, c.get("xp").getAsInt() / (float) perLevel, 0xFF60A0FF);
		g.text(font, c.get("xp").getAsInt() + "/" + perLevel, x + 150, y, 0xFFDDDDDD);
		y += 16;
		if (c.has("stats")) {
			g.text(font, "Stats (now / at Lv.50)", x, y, 0xFFAAAAAA);
			y += 11;
			JsonArray stats = c.getAsJsonArray("stats");
			for (int i = 0; i < stats.size(); i++) {
				JsonArray s = stats.get(i).getAsJsonArray();
				int col = i % 2, row = i / 2;
				int sx = x + col * 124, sy = y + row * 11;
				g.text(font, STAT_NAMES[i], sx, sy, 0xFFFFFFFF);
				g.text(font, s.get(0).getAsInt() + " / " + s.get(1).getAsInt(), sx + 56, sy, 0xFFFFD27F);
			}
			y += 4 * 11 + 4;
			g.text(font, "Moves " + c.get("squares").getAsInt() + " squares a turn in battle", x, y, 0xFFDDDDDD);
			y += 11;
			y = wrapped(g, "Jobs: " + c.get("jobs").getAsString(), x, y, w, 0xFF9FC8FF);
			y += 4;
			String move = c.get("movement").getAsString();
			if (c.get("rideable").getAsBoolean()) {
				g.text(font, "Riding: tier " + c.get("rideTier").getAsInt() + ", " + c.get("topSpeed").getAsDouble() + " blocks/sec"
						+ (move.equals("fly") ? ", flies" : move.equals("swim") ? ", swims" : ""), x, y, 0xFFFFD27F);
				y += 11;
				g.text(font, (c.get("saddled").getAsBoolean() ? "Saddled. " : "No saddle. ") + "Next tier: " + c.get("nextTier").getAsString(), x, y, 0xFFAAAAAA);
			} else g.text(font, "Too small to ride", x, y, 0xFFAAAAAA);
			y += 13;
		}
		if (tab == Tab.OUT) {
			String where = c.get("riding").getAsBoolean() ? "You're riding it." : c.get("sitting").getAsBoolean() ? "Out with you, waiting where you left it." : "Out with you, following you.";
			g.text(font, where, x, y, 0xFF9FE09F);
		} else if (tab == Tab.PARTY) g.text(font, "Party slot " + (selected + 1) + (selected < 4 ? " (goes into battles)" : ""), x, y, 0xFF9FE09F);
	}

	private int wrapped(GuiGraphicsExtractor g, String text, int x, int y, int w, int color) {
		for (var line : font.split(Component.literal(text), w)) {
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
