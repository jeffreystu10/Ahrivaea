package dev.ahrivaea.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.ahrivaea.villager.Villagers;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

import java.util.ArrayList;
import java.util.List;

/**
 * A villager's screen. Untrained: the professions to train it in, with the first deed of each. Trained: its ranks
 * (earned, the one it's training for with progress, and the ones to come), and its shop: every item the profession
 * sells, by rank, the ones not yet unlocked dimmed. Click an item to buy it, shift-click to buy ten.
 */
public class VillagerScreen extends Screen {
	static final int TOP = 14, CELL = 20, COLS = 12;
	private JsonObject state;
	private final List<ItemStack> stacks = new ArrayList<>();
	private int scroll;
	private boolean confirmChange;
	private boolean choosing;

	public VillagerScreen(JsonObject state) {
		super(Component.literal("Villager"));
		setState(state);
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(Villagers.StatePayload.TYPE, (payload, ctx) -> {
			JsonObject o = JsonParser.parseString(payload.json()).getAsJsonObject();
			Minecraft mc = ctx.client();
			if (mc.gui.screen() instanceof VillagerScreen s) s.update(o);
			else mc.gui.setScreen(new VillagerScreen(o));
		});
	}

	public JsonObject state() {
		return state;
	}

	private void setState(JsonObject o) {
		state = o;
		stacks.clear();
		var mc = Minecraft.getInstance();
		if (o.has("offers") && mc.level != null) {
			var ops = RegistryOps.create(JsonOps.INSTANCE, mc.level.registryAccess());
			for (JsonElement e : o.getAsJsonArray("offers"))
				stacks.add(ItemStack.CODEC.parse(ops, e.getAsJsonObject().get("stack")).result().orElse(ItemStack.EMPTY));
		}
	}

	void update(JsonObject o) {
		setState(o);
		rebuildWidgets();
	}

	private boolean trained() {
		return !state.get("profession").getAsString().isEmpty();
	}

	public void send(String kind, java.util.function.Consumer<JsonObject> extra) {
		JsonObject a = new JsonObject();
		a.addProperty("kind", kind);
		a.add("handle", state.getAsJsonObject("handle"));
		extra.accept(a);
		ClientPlayNetworking.send(new Villagers.ActionPayload(a.toString()));
	}

	private int left() {
		return width / 2 - 250;
	}

	/** The shop's top-left corner. */
	private int shopX() {
		return left() + 210;
	}

	private int shopY() {
		return 50 + TOP;
	}

	private int rowsShown() {
		return Math.max(1, (height - shopY() - 34) / CELL);
	}

	@Override
	protected void init() {
		if (!trained() || choosing) {
			JsonArray profs = state.getAsJsonArray("professions");
			scroll = Math.clamp(scroll, 0, Math.max(0, profs.size() - professionRows()));
			for (int i = scroll; i < Math.min(profs.size(), scroll + professionRows()); i++) {
				String id = profs.get(i).getAsJsonObject().get("id").getAsString();
				Button b = addRenderableWidget(Button.builder(Component.literal(id.equals(state.get("profession").getAsString()) ? "Current" : "Train"), x -> {
					choosing = false;
					send("train", a -> a.addProperty("profession", id));
				}).bounds(left() + 430, 46 + TOP + (i - scroll) * 30, 66, 20).build());
				b.active = !id.equals(state.get("profession").getAsString());
			}
			if (choosing) addRenderableWidget(Button.builder(Component.literal("Back"), b -> {
				choosing = false;
				rebuildWidgets();
			}).bounds(width / 2 - 40, height - 26, 80, 20).build());
			else addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose()).bounds(width / 2 - 40, height - 26, 80, 20).build());
			return;
		}
		JsonArray ranks = state.getAsJsonArray("ranks");
		int rank = state.get("rank").getAsInt();
		if (rank < ranks.size()) {
			JsonObject r = ranks.get(rank).getAsJsonObject();
			Button promote = addRenderableWidget(Button.builder(Component.literal("Become " + r.get("name").getAsString()), b -> send("promote", a -> {}))
					.bounds(left(), 60 + TOP + ranks.size() * 34, 190, 20).build());
			promote.active = r.has("progress") && r.get("progress").getAsInt() >= r.get("amount").getAsInt();
		}
		addRenderableWidget(Button.builder(Component.literal(confirmChange ? "Start over in another? (click again)" : "Change profession"), b -> {
			if (!confirmChange) {
				confirmChange = true;
				rebuildWidgets();
			} else {
				confirmChange = false;
				choosing = true;
				scroll = 0;
				rebuildWidgets();
			}
		}).bounds(left(), 84 + TOP + ranks.size() * 34, 190, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose()).bounds(width / 2 - 40, height - 26, 80, 20).build());
	}

	private int professionRows() {
		return Math.max(1, (height - 46 - TOP - 34) / 30);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double dx, double dy) {
		scroll -= (int) Math.signum(dy);
		if (trained() && !choosing) scroll = Math.clamp(scroll, 0, Math.max(0, shopRows() - rowsShown()));
		rebuildWidgets();
		return true;
	}

	/** The shop's layout: for each offer, its row and column (each rank starts a new row, after a heading row). */
	private int[][] layout() {
		JsonArray offers = state.getAsJsonArray("offers");
		int[][] at = new int[offers.size()][2];
		int row = -1, col = COLS, lastRank = -1;
		for (int i = 0; i < offers.size(); i++) {
			int r = offers.get(i).getAsJsonObject().get("rank").getAsInt();
			if (r != lastRank) {
				row += 2; // a heading row, then the items
				col = 0;
				lastRank = r;
			} else if (col >= COLS) {
				row++;
				col = 0;
			}
			at[i][0] = row;
			at[i][1] = col++;
		}
		return at;
	}

	private int shopRows() {
		int[][] at = layout();
		return at.length == 0 ? 0 : at[at.length - 1][0] + 1;
	}

	private int offerAt(double mx, double my) {
		int[][] at = layout();
		for (int i = 0; i < at.length; i++) {
			int x = shopX() + at[i][1] * CELL, y = shopY() + (at[i][0] - scroll) * CELL;
			if (at[i][0] < scroll || at[i][0] >= scroll + rowsShown()) continue;
			if (mx >= x && mx < x + CELL && my >= y && my < y + CELL) return i;
		}
		return -1;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) return true;
		if (trained() && !choosing && event.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) {
			int i = offerAt(event.x(), event.y());
			if (i >= 0) {
				int times = event.hasShiftDown() ? 10 : 1;
				send("buy", a -> {
					a.addProperty("offer", i);
					a.addProperty("times", times);
				});
				return true;
			}
		}
		return false;
	}

	/** Buys an offer as if it were clicked (for the tests). */
	public void buy(int offer, int times) {
		send("buy", a -> {
			a.addProperty("offer", offer);
			a.addProperty("times", times);
		});
	}

	public void train(String profession) {
		send("train", a -> a.addProperty("profession", profession));
	}

	public void promote() {
		send("promote", a -> {});
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(g, mouseX, mouseY, partialTick);
		int x = left();
		String title = "Villager Lv." + state.get("level").getAsInt() + (trained() ? ": " + rankName() + " " + state.get("professionName").getAsString() : ", untrained");
		g.centeredText(font, title, width / 2, 10 + TOP, 0xFFFFD040);
		String xp = "Your XP: " + state.get("xp").getAsInt();
		g.text(font, xp, x + 500 - font.width(xp), 10 + TOP, 0xFF9FE09F);
		if (!trained() || choosing) {
			g.centeredText(font, choosing ? "Pick a new profession. It starts over as a Novice." : "Pick a profession to train it in. Each rank takes a deed; each rank sells more.",
					width / 2, 24 + TOP, 0xFFAAAAAA);
			JsonArray profs = state.getAsJsonArray("professions");
			for (int i = scroll; i < Math.min(profs.size(), scroll + professionRows()); i++) {
				JsonObject p = profs.get(i).getAsJsonObject();
				int y = 44 + TOP + (i - scroll) * 30;
				g.fill(x, y - 2, x + 500, y + 26, 0x80000000);
				g.item(new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(p.get("icon").getAsString()))), x + 4, y + 3);
				g.text(font, p.get("name").getAsString() + "  (" + p.get("offers").getAsInt() + " items)", x + 26, y, 0xFFFFFFFF);
				g.text(font, p.get("description").getAsString(), x + 26, y + 10, 0xFFAAAAAA);
				String first = "Novice: " + p.get("first").getAsString();
				g.text(font, first, x + 426 - font.width(first), y, 0xFF9FC8FF);
			}
			return;
		}
		// ranks
		JsonArray ranks = state.getAsJsonArray("ranks");
		int rank = state.get("rank").getAsInt();
		g.text(font, "Ranks", x, 44 + TOP, 0xFFFFFFFF);
		for (int i = 0; i < ranks.size(); i++) {
			JsonObject r = ranks.get(i).getAsJsonObject();
			int y = 58 + TOP + i * 34;
			boolean earned = r.get("earned").getAsBoolean(), now = i == rank;
			g.fill(x, y - 2, x + 190, y + 30, now ? 0xA0302040 : 0x80000000);
			g.text(font, (earned ? "✔ " : now ? "▶ " : "🔒 ") + r.get("name").getAsString(), x + 4, y, earned ? 0xFF7FE07F : now ? 0xFFFFD040 : 0xFF888888);
			String deed = r.get("deed").getAsString();
			if (font.width(deed) > 180) deed = font.plainSubstrByWidth(deed, 175) + "...";
			g.text(font, deed, x + 4, y + 11, earned ? 0xFF8FAF8F : now ? 0xFFFFFFFF : 0xFF777777);
			if (now && r.has("progress")) {
				int p = r.get("progress").getAsInt(), a = r.get("amount").getAsInt();
				g.fill(x + 4, y + 22, x + 150, y + 26, 0xFF333333);
				g.fill(x + 4, y + 22, x + 4 + (int) (146L * p / Math.max(1, a)), y + 26, p >= a ? 0xFF55DD55 : 0xFFDDAA33);
				g.text(font, p + "/" + a, x + 154, y + 20, 0xFFCCCCCC);
			}
		}
		if (rank >= ranks.size()) g.text(font, "A Master: everything is unlocked.", x, 60 + TOP + ranks.size() * 34, 0xFF7FE07F);
		// shop
		JsonArray offers = state.getAsJsonArray("offers");
		int[][] at = layout();
		g.text(font, "Shop (click to buy, shift-click for 10; scroll for more)", shopX(), 44 + TOP - 10, 0xFFFFFFFF);
		int hovered = offerAt(mouseX, mouseY);
		for (int i = 0; i < at.length; i++) {
			int row = at[i][0] - scroll;
			int r = offers.get(i).getAsJsonObject().get("rank").getAsInt();
			boolean firstOfRank = i == 0 || offers.get(i - 1).getAsJsonObject().get("rank").getAsInt() != r;
			if (firstOfRank && row - 1 >= 0 && row - 1 < rowsShown()) {
				g.text(font, ranks.get(r).getAsJsonObject().get("name").getAsString() + (r < rank ? "" : " (locked)"), shopX(), shopY() + (row - 1) * CELL + 8,
						r < rank ? 0xFFFFD040 : 0xFF888888);
			}
			if (row < 0 || row >= rowsShown()) continue;
			int cx = shopX() + at[i][1] * CELL, cy = shopY() + row * CELL;
			boolean open = r < rank;
			g.fill(cx, cy, cx + CELL - 2, cy + CELL - 2, i == hovered ? 0xC0606080 : open ? 0x80303040 : 0x80101010);
			ItemStack s = stacks.get(i);
			g.item(s, cx + 1, cy + 1);
			if (s.getCount() > 1) {
				String n = String.valueOf(s.getCount());
				g.text(font, n, cx + CELL - 2 - font.width(n), cy + CELL - 9, 0xFFFFFFFF, true);
			}
			if (!open) g.fill(cx, cy, cx + CELL - 2, cy + CELL - 2, 0xA0000000);
		}
		if (hovered >= 0) {
			ItemStack s = stacks.get(hovered);
			JsonObject off = offers.get(hovered).getAsJsonObject();
			List<Component> tip = new ArrayList<>();
			tip.add(Component.literal(s.getCount() + " x ").append(s.getHoverName()));
			var books = s.get(DataComponents.STORED_ENCHANTMENTS);
			if (books != null) for (var e : books.entrySet()) tip.add(Enchantment.getFullname(e.getKey(), e.getIntValue()).copy().withStyle(ChatFormatting.AQUA));
			var potion = s.get(DataComponents.POTION_CONTENTS);
			if (potion != null && potion.potion().isPresent())
				tip.add(Component.literal(potion.potion().get().unwrapKey().map(k -> k.identifier().getPath().replace('_', ' ')).orElse("")).withStyle(ChatFormatting.BLUE));
			int cost = off.get("cost").getAsInt();
			tip.add(Component.literal(cost + " XP").withStyle(state.get("xp").getAsInt() >= cost ? ChatFormatting.GREEN : ChatFormatting.RED));
			int r = off.get("rank").getAsInt();
			tip.add(r < rank ? Component.literal("Click to buy, shift-click for 10").withStyle(ChatFormatting.DARK_GRAY)
					: Component.literal("Sold from " + ranks.get(r).getAsJsonObject().get("name").getAsString() + " rank").withStyle(ChatFormatting.GRAY));
			g.setComponentTooltipForNextFrame(font, tip, mouseX, mouseY);
		}
	}

	private String rankName() {
		int rank = state.get("rank").getAsInt();
		JsonArray ranks = state.getAsJsonArray("ranks");
		return rank == 0 ? "training" : ranks.get(Math.min(rank, ranks.size()) - 1).getAsJsonObject().get("name").getAsString();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
