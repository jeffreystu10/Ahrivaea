package dev.ahrivaea.guide;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The guide: a list of mods on the left; on the right, the chosen mod's About page (what it does, how to start,
 * commands, items) or its Recipes, which can be searched. "All mods" shows every recipe together. Clicking any
 * item shows every recipe that makes or uses it.
 */
public class GuideScreen extends Screen {
	private static final int GOLD = 0xFFFFC94A, WHITE = 0xFFFFFFFF, GREY = 0xFFA8A8B8, DIM = 0xFF707080, NEW = 0xFF7CFC6A;
	private static final int CARD_W = 144, CARD_H = 86, GAP = 6, ROW = 16, LIST_W = 120;

	private enum Page {ABOUT, RECIPES}

	private final GuideData data;
	/** null = all mods */
	private GuideData.Mod mod;
	private Page page = Page.ABOUT;
	/** When set, only recipes that make or use this item. */
	private Item focus;
	private String search = "";
	private double scroll;
	private int contentHeight;
	private EditBox searchBox;
	private ItemStack hovered = ItemStack.EMPTY;
	private int hoveredOptions;

	public GuideScreen(GuideData data, String open) {
		super(Component.literal("Ahrivaea Guide"));
		this.data = data;
		if (open != null) {
			String q = open.toLowerCase(Locale.ROOT).trim();
			for (GuideData.Mod m : data.mods) {
				if (m.key().equalsIgnoreCase(q) || m.id().equalsIgnoreCase(q) || m.name().toLowerCase(Locale.ROOT).contains(q)) {
					select(m);
					break;
				}
			}
		}
	}

	// ---- layout ----

	private int left() {
		return 8;
	}

	/** Kept a little below the top edge, clear of the F3 debug text. */
	private int top() {
		return 22;
	}

	private int right() {
		return width - 8;
	}

	private int bottom() {
		return height - 8;
	}

	private int contentX() {
		return left() + 6 + LIST_W + 8;
	}

	private int contentW() {
		return right() - 6 - contentX();
	}

	/** Where the scrolling part of the page starts. */
	private int bodyTop() {
		return top() + 24 + (page == Page.RECIPES ? 40 : 18);
	}

	private int bodyBottom() {
		return bottom() - 6;
	}

	@Override
	protected void init() {
		int x = contentX(), w = contentW(), y = top() + 22;
		Button about = addRenderableWidget(Button.builder(Component.literal("About"), b -> setPage(Page.ABOUT)).bounds(x + w - 166, y - 2, 70, 16).build());
		int n = recipesOfMod().size();
		Button recipes = addRenderableWidget(Button.builder(Component.literal("Recipes (" + n + ")"), b -> setPage(Page.RECIPES)).bounds(x + w - 92, y - 2, 92, 16).build());
		about.active = page != Page.ABOUT;
		recipes.active = page != Page.RECIPES;
		if (page == Page.RECIPES) {
			searchBox = new EditBox(font, x, y + 18, Math.min(220, w), 16, Component.literal("Search"));
			searchBox.setHint(Component.literal("Search recipes...").withStyle(ChatFormatting.DARK_GRAY));
			searchBox.setValue(search);
			searchBox.setResponder(s -> {
				search = s;
				scroll = 0;
			});
			addRenderableWidget(searchBox);
			if (focus != null) addRenderableWidget(Button.builder(Component.literal("Show all"), b -> {
				focus = null;
				scroll = 0;
				rebuildWidgets();
			}).bounds(x + Math.min(220, w) + 6, y + 18, 60, 16).build());
		} else searchBox = null;
		addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose()).bounds(left() + 6, bottom() - 22, LIST_W, 16).build());
	}

	private void select(GuideData.Mod m) {
		mod = m;
		focus = null;
		search = "";
		scroll = 0;
		if (m != null && m.recipes().isEmpty()) page = Page.ABOUT;
		if (m != null && page == Page.ABOUT) GuideClient.markSeen(m.key());
	}

	private void setPage(Page p) {
		page = p;
		scroll = 0;
		if (mod != null && p == Page.ABOUT) GuideClient.markSeen(mod.key());
		rebuildWidgets();
	}

	private List<GuideData.Recipe> recipesOfMod() {
		return mod == null ? data.allRecipes() : mod.recipes();
	}

	private List<GuideData.Recipe> shownRecipes() {
		List<GuideData.Recipe> out = new ArrayList<>();
		String[] words = search.toLowerCase(Locale.ROOT).trim().split("\\s+");
		for (GuideData.Recipe r : focus != null ? data.allRecipes() : recipesOfMod()) {
			if (focus != null && !r.makes(focus) && !r.uses(focus)) continue;
			boolean ok = true;
			for (String w : words) if (!w.isEmpty() && !r.searchText().contains(w)) ok = false;
			if (ok) out.add(r);
		}
		// with an item picked: what makes it first, then what uses it
		if (focus != null) out.sort((a, b) -> Boolean.compare(!a.makes(focus), !b.makes(focus)));
		return out;
	}

	// ---- for tests (and other mods' buttons) ----

	public void showRecipes() {
		setPage(Page.RECIPES);
	}

	public void showRecipesFor(Item item) {
		focus = item;
		mod = null;
		search = "";
		page = Page.RECIPES;
		scroll = 0;
		rebuildWidgets();
	}

	public void search(String text) {
		search = text;
		if (searchBox != null) searchBox.setValue(text);
	}

	/** How many recipes the page shows now (Recipes page) */
	public int shownCount() {
		return shownRecipes().size();
	}

		// ---- input ----

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) return true;
		double mx = event.x(), my = event.y();
		// the mod list
		int lx = left() + 6, ly = top() + 24;
		if (mx >= lx && mx < lx + LIST_W) {
			int i = (int) Math.floor((my - ly) / ROW);
			if (my >= ly && i >= 0 && i <= data.mods.size()) {
				select(i == 0 ? null : data.mods.get(i - 1));
				rebuildWidgets();
				return true;
			}
		}
		// an item: show what makes and uses it
		if (!hovered.isEmpty() && event.button() == 0) {
			focus = hovered.getItem();
			mod = null;
			page = Page.RECIPES;
			search = "";
			scroll = 0;
			rebuildWidgets();
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double sx, double sy) {
		int max = Math.max(0, contentHeight - (bodyBottom() - bodyTop()));
		scroll = Math.clamp(scroll - sy * 24, 0, max);
		return true;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	// ---- drawing ----

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(g, mouseX, mouseY, partialTick);
		g.fill(left(), top(), right(), bottom(), 0xF0101018);
		g.outline(left(), top(), right() - left(), bottom() - top(), 0xFF3C3C50);
		g.text(font, "Ahrivaea Guide", left() + 8, top() + 8, GOLD);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		// the panel is the background; the buttons come next, then the pages
		super.extractRenderState(g, mouseX, mouseY, partialTick);
		hovered = ItemStack.EMPTY;
		drawModList(g, mouseX, mouseY);

		int x = contentX(), y = top() + 24;
		String heading = focus != null ? "Recipes with " + new ItemStack(focus).getHoverName().getString() : mod == null ? "All mods" : mod.name();
		g.text(font, heading, x, y + 2, WHITE);

		int top = bodyTop(), bot = bodyBottom();
		g.enableScissor(x - 2, top, x + contentW() + 2, bot);
		boolean inBody = mouseY >= top && mouseY < bot && mouseX >= x && mouseX < x + contentW();
		int end = page == Page.ABOUT ? drawAbout(g, x, top - (int) scroll, contentW(), inBody, mouseX, mouseY)
				: drawRecipes(g, x, top - (int) scroll, contentW(), inBody, mouseX, mouseY, top, bot);
		g.disableScissor();
		contentHeight = end - (top - (int) scroll);
		if (contentHeight > bot - top) {
			// scroll bar
			int h = bot - top, barH = Math.max(16, h * h / contentHeight);
			int barY = top + (int) ((h - barH) * (scroll / Math.max(1, contentHeight - h)));
			g.fill(right() - 5, top, right() - 3, bot, 0x40FFFFFF);
			g.fill(right() - 5, barY, right() - 3, barY + barH, 0xC0FFFFFF);
		}

		if (!hovered.isEmpty()) {
			List<Component> tip = new ArrayList<>();
			tip.add(hovered.getHoverName());
			if (hoveredOptions > 1) tip.add(Component.literal("Any of " + hoveredOptions + " kinds (they take turns here)").withStyle(ChatFormatting.GRAY));
			String by = data.addedBy.get(hovered.getItem());
			String ns = BuiltInRegistries.ITEM.getKey(hovered.getItem()).getNamespace();
			tip.add(Component.literal(by != null ? "Added by " + by : ns.equals("minecraft") ? "Minecraft" : ns).withStyle(ChatFormatting.BLUE));
			tip.add(Component.literal("Click: recipes that make or use it").withStyle(ChatFormatting.DARK_GRAY));
			g.setComponentTooltipForNextFrame(font, tip, mouseX, mouseY);
		}
	}

	private void drawModList(GuiGraphicsExtractor g, int mx, int my) {
		int x = left() + 6, y = top() + 24;
		for (int i = 0; i <= data.mods.size(); i++) {
			GuideData.Mod m = i == 0 ? null : data.mods.get(i - 1);
			boolean selected = focus == null && m == mod;
			boolean over = mx >= x && mx < x + LIST_W && my >= y && my < y + ROW;
			if (selected) g.fill(x, y, x + LIST_W, y + ROW - 1, 0xFF34344A);
			else if (over) g.fill(x, y, x + LIST_W, y + ROW - 1, 0xFF22222E);
			String name = m == null ? "All mods" : m.name().replaceFirst("^Ahrivaea ", "");
			boolean fresh = m != null && !GuideClient.seen(m.key());
			int room = LIST_W - 8 - (fresh ? font.width("NEW") + 4 : 0);
			g.text(font, font.plainSubstrByWidth(name, room), x + 4, y + 4, selected ? GOLD : WHITE);
			if (fresh) g.text(font, "NEW", x + LIST_W - 4 - font.width("NEW"), y + 4, NEW);
			y += ROW;
		}
	}

	/** Draws word-wrapped text; returns the y below it. */
	private int para(GuiGraphicsExtractor g, String text, int x, int y, int w, int color) {
		for (FormattedCharSequence line : font.split(Component.literal(text), w)) {
			g.text(font, line, x, y, color);
			y += font.lineHeight + 1;
		}
		return y;
	}

	private int bullets(GuiGraphicsExtractor g, List<String> items, int x, int y, int w, int color, boolean numbered) {
		for (int i = 0; i < items.size(); i++) {
			String mark = numbered ? (i + 1) + "." : "•";
			g.text(font, mark, x, y, GREY);
			y = para(g, items.get(i), x + 12, y, w - 12, color) + 3;
		}
		return y;
	}

	private int drawAbout(GuiGraphicsExtractor g, int x, int y, int w, boolean inBody, int mx, int my) {
		w = Math.min(w - 8, 520);
		if (mod == null) {
			y = para(g, "Every mod in the Ahrivaea pack. Pick one on the left to read what it does and how to get started, "
					+ "or open Recipes to see how to make everything. Click any item to see what makes it and what it's used in.", x, y, w, GREY) + 8;
			for (GuideData.Mod m : data.mods) {
				g.text(font, m.name(), x, y, GOLD);
				if (!GuideClient.seen(m.key())) g.text(font, "NEW", x + font.width(m.name()) + 6, y, NEW);
				y = para(g, m.summary(), x, y + 11, w, WHITE) + 8;
			}
			return y;
		}
		y = para(g, mod.summary(), x, y, w, WHITE) + 8;
		if (!mod.guide().isEmpty()) {
			g.text(font, "Getting started", x, y, GOLD);
			y = bullets(g, mod.guide(), x, y + 12, w, WHITE, true) + 6;
		}
		if (!mod.items().isEmpty()) {
			g.text(font, "Its items", x, y, GOLD);
			y += 12;
			int ix = x;
			for (ItemStack st : mod.items()) {
				if (ix + 18 > x + w) {
					ix = x;
					y += 20;
				}
				slot(g, st, 1, ix, y, inBody, mx, my);
				ix += 20;
			}
			y += 24;
			y = para(g, "Click one to see how it's made. Items with no recipe come from creatures, loot or the mod itself.", x, y, w, DIM) + 6;
		}
		if (!mod.features().isEmpty()) {
			g.text(font, "Everything it does", x, y, GOLD);
			y = bullets(g, mod.features(), x, y + 12, w, GREY, false) + 6;
		}
		if (!mod.commands().isBlank() && !mod.commands().startsWith("(none)")) {
			g.text(font, "Commands", x, y, GOLD);
			y = para(g, mod.commands(), x, y + 12, w, GREY) + 6;
		}
		if (!mod.install().isBlank()) {
			g.text(font, "Do players need it installed?", x, y, GOLD);
			y = para(g, mod.install(), x, y + 12, w, GREY) + 6;
		}
		return y;
	}

	private int drawRecipes(GuiGraphicsExtractor g, int x, int y, int w, boolean inBody, int mx, int my, int top, int bot) {
		List<GuideData.Recipe> list = shownRecipes();
		if (list.isEmpty()) {
			String why = focus != null ? "Nothing in these mods makes or uses this item."
					: !search.isBlank() ? "No recipes match \"" + search + "\"."
					: "This mod has no recipes of its own. Its About page says how it works.";
			return para(g, why, x, y + 4, w, GREY);
		}
		int cols = Math.max(1, (w + GAP) / (CARD_W + GAP));
		for (int i = 0; i < list.size(); i++) {
			int cx = x + (i % cols) * (CARD_W + GAP), cy = y + (i / cols) * (CARD_H + GAP);
			if (cy + CARD_H < top || cy > bot) continue;
			card(g, list.get(i), cx, cy, inBody, mx, my);
		}
		int rows = (list.size() + cols - 1) / cols;
		return y + rows * (CARD_H + GAP);
	}

	private void card(GuiGraphicsExtractor g, GuideData.Recipe r, int x, int y, boolean inBody, int mx, int my) {
		g.fill(x, y, x + CARD_W, y + CARD_H, 0xFF22222C);
		g.outline(x, y, CARD_W, CARD_H, 0xFF44445A);
		String name = r.result().getHoverName().getString() + (r.result().getCount() > 1 ? " x" + r.result().getCount() : "");
		g.text(font, font.plainSubstrByWidth(name, CARD_W - 8), x + 4, y + 4, WHITE);
		int gx = x + 6, gy = y + 16, mid = gy + 18;
		switch (r.station()) {
			case CRAFTING -> {
				for (int i = 0; i < 9; i++) slotOptions(g, r.slots().get(i), gx + (i % 3) * 18, gy + (i / 3) * 18, inBody, mx, my);
			}
			case STONECUTTER -> slotOptions(g, r.slots().get(0), gx + 18, mid, inBody, mx, my);
			case SMITHING -> {
				for (int i = 0; i < 3; i++) slotOptions(g, r.slots().get(i), gx + i * 18, mid, inBody, mx, my);
			}
			default -> {
			}
		}
		g.text(font, "→", gx + 54 + 6, mid + 5, GREY);
		slot(g, r.result(), 1, gx + 54 + 20, mid, inBody, mx, my);
		g.text(font, r.station().title, x + 4, y + CARD_H - 11, DIM);
		if (mod == null || focus != null) {
			// which mod it's from, after the station name
			String m = font.plainSubstrByWidth(r.mod().name().replaceFirst("^Ahrivaea ", ""), CARD_W - 14 - font.width(r.station().title));
			g.text(font, m, x + CARD_W - 4 - font.width(m), y + CARD_H - 11, 0xFF6A8AC0);
		}
	}

	private void slotOptions(GuiGraphicsExtractor g, List<ItemStack> options, int x, int y, boolean inBody, int mx, int my) {
		ItemStack shown = options.isEmpty() ? ItemStack.EMPTY : options.get((int) ((System.currentTimeMillis() / 1000) % options.size()));
		slot(g, shown, Math.max(1, options.size()), x, y, inBody, mx, my);
	}

	private void slot(GuiGraphicsExtractor g, ItemStack stack, int options, int x, int y, boolean inBody, int mx, int my) {
		g.fill(x, y, x + 18, y + 18, 0xFF15151C);
		g.fill(x + 1, y + 1, x + 17, y + 17, 0xFF3A3A48);
		if (stack.isEmpty()) return;
		g.item(stack, x + 1, y + 1);
		g.itemDecorations(font, stack, x + 1, y + 1);
		if (inBody && mx >= x && mx < x + 18 && my >= y && my < y + 18) {
			g.fill(x + 1, y + 1, x + 17, y + 17, 0x60FFFFFF);
			hovered = stack;
			hoveredOptions = options;
		}
	}
}
