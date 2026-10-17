package dev.ahrivaea.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.ahrivaea.Ahrivaea;
import dev.ahrivaea.creature.CreatureEntity;
import dev.ahrivaea.party.Starters;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;

/**
 * Picking a starter: every Common creature (no influence) as a button on the left; the chosen one turns on a stand
 * on the right with its stats, how it moves and what it does, and "Choose" sends it to your party at level 1.
 */
public class StarterScreen extends Screen {
	private final JsonObject offer;
	private int selected;
	private CreatureEntity preview;
	private static final String[] STAT_NAMES = {"HP", "Attack", "Defense", "Speed", "Magic", "Luck", "Charisma"};
	private static final int COLS = 4, BTN_W = 66, BTN_H = 16, ROW = 17, PANEL_W = 186;
	private boolean picked;

	public StarterScreen(JsonObject offer) {
		super(Component.literal("Choose your starter"));
		this.offer = offer;
	}

	private JsonArray options() {
		return offer.getAsJsonArray("options");
	}

	private int gridLeft() {
		return Math.max(4, width / 2 - (COLS * (BTN_W + 2) + 8 + PANEL_W) / 2);
	}

	private int panelLeft() {
		return gridLeft() + COLS * (BTN_W + 2) + 8;
	}

	@Override
	protected void init() {
		JsonArray l = options();
		selected = Math.clamp(selected, 0, Math.max(0, l.size() - 1));
		int x0 = gridLeft();
		for (int i = 0; i < l.size(); i++) {
			final int idx = i;
			Button b = addRenderableWidget(Button.builder(Component.literal(l.get(i).getAsJsonObject().get("name").getAsString()), btn -> {
				selected = idx;
				rebuildWidgets();
			}).bounds(x0 + (i % COLS) * (BTN_W + 2), 24 + (i / COLS) * ROW, BTN_W, BTN_H).build());
			b.active = i != selected;
		}
		if (!l.isEmpty()) {
			JsonObject c = l.get(selected).getAsJsonObject();
			addRenderableWidget(Button.builder(Component.literal("Choose " + c.get("name").getAsString()), b -> confirm()).bounds(panelLeft(), height - 26, PANEL_W, 20).build());
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.level != null && !l.isEmpty()) {
			if (preview == null) preview = new CreatureEntity(Ahrivaea.CREATURE, mc.level);
			preview.previewAs(l.get(selected).getAsJsonObject().get("id").getAsString(), "none");
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(g, mouseX, mouseY, partialTick);
		g.centeredText(font, "Choose your starter: it joins your party at level " + offer.get("level").getAsInt(), width / 2, 8, 0xFFFFD040);
		JsonArray l = options();
		if (l.isEmpty()) return;
		JsonObject c = l.get(selected).getAsJsonObject();
		int x = panelLeft(), y = 24, w = PANEL_W;
		g.fill(x - 4, y - 4, x + w + 4, height - 30, 0x80000000);
		g.text(font, c.get("name").getAsString(), x, y, 0xFFFFFFFF);
		String move = c.get("movement").getAsString();
		String how = (move.equals("fly") ? "Flies" : move.equals("swim") ? "Swims" : "Walks") + (c.get("rideable").getAsBoolean() ? ", rideable" : "");
		g.text(font, how, x + w - font.width(how), y, 0xFF9FD0FF);
		// the creature itself, turning to follow the mouse
		int boxTop = y + 12, boxBottom = boxTop + 66;
		if (preview != null) {
			String id = c.get("id").getAsString();
			float size = Math.max(0.5f, Math.max(GeoModels.height(id, "none"), GeoModels.extent(id, "none") * 0.7f) * GeoModels.scale(id, "none"));
			int px = Math.max(8, Math.round(54 / size));
			float h = GeoModels.height(id, "none") * GeoModels.scale(id, "none");
			InventoryScreen.extractEntityInInventoryFollowsMouse(g, x + w / 2 - 50, boxTop, x + w / 2 + 50, boxBottom, px, h / 2 - 0.4f, mouseX, mouseY, preview);
		}
		y = boxBottom + 4;
		JsonArray stats = c.getAsJsonArray("stats");
		for (int i = 0; i < stats.size(); i++) {
			int v = stats.get(i).getAsInt();
			g.text(font, STAT_NAMES[i], x, y, 0xFFFFFFFF);
			PartyClient.bar(g, x + 50, y + 2, 104, 5, v / 100f, 0xFFFFC040);
			g.text(font, String.valueOf(v), x + 160, y, 0xFFFFD27F);
			y += 10;
		}
		g.text(font, "Total " + c.get("total").getAsInt() + ", moves " + c.get("squares").getAsInt() + " squares in battle", x, y + 2, 0xFFDDDDDD);
		y += 13;
		for (var line : font.split(Component.literal("Jobs: " + c.get("jobs").getAsString()), w)) {
			g.text(font, line, x, y, 0xFF9FC8FF);
			y += 10;
		}
	}

	/** Selects a creature by species id (as clicking its button does). */
	public void choose(String speciesId) {
		JsonArray l = options();
		for (int i = 0; i < l.size(); i++) if (l.get(i).getAsJsonObject().get("id").getAsString().equals(speciesId)) selected = i;
		rebuildWidgets();
	}

	/** Picks the selected creature (the Choose button). */
	public void confirm() {
		JsonArray l = options();
		if (l.isEmpty()) return;
		picked = true;
		ClientPlayNetworking.send(new Starters.PickPayload(l.get(selected).getAsJsonObject().get("id").getAsString()));
		onClose();
	}

	@Override
	public void onClose() {
		super.onClose();
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null && !picked)
			mc.player.sendSystemMessage(Component.literal("Type /starter to choose your starter creature."));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
