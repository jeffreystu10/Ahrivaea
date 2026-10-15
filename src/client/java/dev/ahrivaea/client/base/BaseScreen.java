package dev.ahrivaea.client.base;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.ahrivaea.base.Bases;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * A base's screen: its workers (what each is doing, with a button to send it away) on the left, and your party and
 * box on the right, each with a button to put that creature to work here.
 */
public class BaseScreen extends Screen {
	private JsonObject state;
	private int tick;
	private int boxScroll;

	public BaseScreen(JsonObject state) {
		super(Component.literal("Base"));
		this.state = state;
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(Bases.StatePayload.TYPE, (payload, ctx) -> {
			JsonObject o = JsonParser.parseString(payload.json()).getAsJsonObject();
			Minecraft mc = ctx.client();
			if (mc.gui.screen() instanceof BaseScreen s) s.update(o);
			else mc.gui.setScreen(new BaseScreen(o));
		});
	}

	public JsonObject state() {
		return state;
	}

	void update(JsonObject o) {
		state = o;
		rebuildWidgets();
	}

	public void send(String kind, int index) {
		JsonObject a = new JsonObject();
		a.addProperty("kind", kind);
		a.addProperty("index", index);
		a.add("pos", state.getAsJsonArray("pos"));
		ClientPlayNetworking.send(new Bases.ActionPayload(a.toString()));
	}

	@Override
	protected void init() {
		int left = width / 2 - 250, right = width / 2 + 10;
		JsonArray workers = state.getAsJsonArray("workers");
		for (int i = 0; i < workers.size(); i++) {
			final int idx = i;
			addRenderableWidget(Button.builder(Component.literal("Send away"), b -> send("unassign", idx)).bounds(left + 170, 58 + i * 26, 70, 18).build());
		}
		boolean full = workers.size() >= state.get("cap").getAsInt();
		int y = 58;
		JsonArray party = state.getAsJsonArray("party");
		for (int i = 0; i < party.size(); i++) {
			final int idx = i;
			Button b = addRenderableWidget(Button.builder(Component.literal("Work here"), x -> send("assign_party", idx)).bounds(right + 170, y + i * 26, 70, 18).build());
			b.active = !full;
		}
		JsonArray box = state.getAsJsonArray("box");
		int boxTop = y + Math.max(1, party.size()) * 26 + 22;
		int rows = Math.max(1, (height - boxTop - 30) / 26);
		boxScroll = Math.clamp(boxScroll, 0, Math.max(0, box.size() - rows));
		for (int i = boxScroll; i < Math.min(box.size(), boxScroll + rows); i++) {
			final int idx = i;
			Button b = addRenderableWidget(Button.builder(Component.literal("Work here"), x -> send("assign_box", idx)).bounds(right + 170, boxTop + (i - boxScroll) * 26, 70, 18).build());
			b.active = !full;
		}
		addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose()).bounds(width / 2 - 40, height - 26, 80, 20).build());
	}

	@Override
	public boolean mouseScrolled(double x, double y, double dx, double dy) {
		boxScroll -= (int) Math.signum(dy);
		rebuildWidgets();
		return true;
	}

	@Override
	public void tick() {
		// keep the workers' status up to date
		if (++tick % 40 == 0) send("refresh", -1);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(g, mouseX, mouseY, partialTick);
		int left = width / 2 - 250, right = width / 2 + 10;
		JsonArray workers = state.getAsJsonArray("workers");
		g.centeredText(font, "Base (level " + state.get("level").getAsInt() + ", radius " + state.get("radius").getAsInt() + ")", width / 2, 10, 0xFFFFD040);
		g.centeredText(font, state.get("upgrade").getAsString(), width / 2, 22, 0xFFAAAAAA);
		g.text(font, "Workers " + workers.size() + "/" + state.get("cap").getAsInt(), left, 42, 0xFFFFFFFF);
		for (int i = 0; i < workers.size(); i++) entry(g, workers.get(i).getAsJsonObject(), left, 58 + i * 26, true);
		if (workers.isEmpty()) g.text(font, "Nobody works here yet: pick creatures on the right.", left, 58, 0xFFAAAAAA);
		JsonArray party = state.getAsJsonArray("party");
		g.text(font, "Party", right, 42, 0xFFFFFFFF);
		for (int i = 0; i < party.size(); i++) entry(g, party.get(i).getAsJsonObject(), right, 58 + i * 26, false);
		if (party.isEmpty()) g.text(font, "(empty)", right, 58, 0xFFAAAAAA);
		JsonArray box = state.getAsJsonArray("box");
		int boxTop = 58 + Math.max(1, party.size()) * 26 + 22;
		g.text(font, "Box (" + box.size() + (box.size() > 0 ? ", scroll for more" : "") + ")", right, boxTop - 16, 0xFFFFFFFF);
		int rows = Math.max(1, (height - boxTop - 30) / 26);
		for (int i = boxScroll; i < Math.min(box.size(), boxScroll + rows); i++) entry(g, box.get(i).getAsJsonObject(), right, boxTop + (i - boxScroll) * 26, false);
	}

	private void entry(GuiGraphicsExtractor g, JsonObject c, int x, int y, boolean worker) {
		g.fill(x - 2, y - 2, x + 244, y + 22, 0x80000000);
		g.text(font, c.get("name").getAsString() + " Lv." + c.get("level").getAsInt(), x, y, worker || c.get("works").getAsBoolean() ? 0xFFFFFFFF : 0xFFAAAAAA);
		String sub = worker ? c.get("status").getAsString() : c.get("jobs").getAsString();
		if (font.width(sub) > 165) sub = font.plainSubstrByWidth(sub, 160) + "...";
		g.text(font, sub, x, y + 11, worker ? 0xFF9FE09F : 0xFF9FC8FF);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
