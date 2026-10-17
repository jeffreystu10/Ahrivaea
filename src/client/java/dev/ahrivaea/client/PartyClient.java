package dev.ahrivaea.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import dev.ahrivaea.Ahrivaea;
import dev.ahrivaea.party.PartySync;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.lwjgl.sdl.SDLScancode;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Your party at a glance: a small summary on the left of the screen (name, level and HP of each creature, and the
 * ones you've sent out), and the party screen on a key (K by default; P is Minecraft's Social Interactions key).
 * The server sends what's in them (PartySync), so both only appear on servers with Ahrivaea Creatures.
 */
public final class PartyClient {
	private PartyClient() {}

	public static KeyMapping OPEN;
	/** The latest party view from the server (null until it arrives, and on servers without the mod). */
	static JsonObject state;
	/** Whether the on-screen summary is shown (the party screen has a button for it; kept in config/ahrivaea_party.json). */
	static boolean showSummary = true;

	public static void register() {
		loadConfig();
		KeyMapping.Category category = KeyMapping.Category.register(Ahrivaea.id("party"));
		OPEN = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.ahrivaea.party", InputConstants.Type.KEYBOARD, SDLScancode.SDL_SCANCODE_K, category));
		ClientPlayNetworking.registerGlobalReceiver(PartySync.StatePayload.TYPE, (payload, ctx) -> {
			state = JsonParser.parseString(payload.json()).getAsJsonObject();
			if (ctx.client().gui.screen() instanceof PartyScreen s) s.refresh();
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> state = null);
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			while (OPEN.consumeClick()) {
				if (mc.gui.screen() != null || mc.player == null) continue;
				if (state == null) mc.player.sendOverlayMessage(net.minecraft.network.chat.Component.literal("This server doesn't use Ahrivaea Creatures."));
				else mc.gui.setScreen(new PartyScreen());
			}
		});
		HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, Ahrivaea.id("party_summary"), (g, delta) -> {
			Minecraft mc = Minecraft.getInstance();
			if (state == null || !showSummary || mc.player == null || mc.gui.hud.isHidden()) return;
			drawSummary(g, mc.font);
		});
	}

	/** How many of your creatures are out in the world with you (for tests). */
	public static int outCount() {
		return state == null ? -1 : state.getAsJsonArray("out").size();
	}

	static final int ROW = 21, WIDTH = 112;

	/** The summary: a row per party creature (then any out in the world), down the left side of the screen. */
	static void drawSummary(GuiGraphicsExtractor g, Font font) {
		JsonArray party = state.getAsJsonArray("party"), out = state.getAsJsonArray("out");
		int rows = party.size() + out.size();
		if (rows == 0) return;
		int h = 12 + rows * ROW + (out.isEmpty() || party.isEmpty() ? 0 : 4);
		int x = 4, y = Math.max(76, g.guiHeight() / 2 - h / 2 - 20);
		g.fill(x - 2, y - 2, x + WIDTH + 2, y + h, 0x90000000);
		g.text(font, "Party  [" + OPEN.getTranslatedKeyMessage().getString() + "]", x + 1, y, 0xFFAAAAAA);
		y += 12;
		for (JsonElement e : party) {
			row(g, font, e.getAsJsonObject(), x, y, null);
			y += ROW;
		}
		if (!out.isEmpty() && !party.isEmpty()) y += 4;
		for (JsonElement e : out) {
			JsonObject c = e.getAsJsonObject();
			row(g, font, c, x, y, c.get("riding").getAsBoolean() ? "riding" : "out");
			y += ROW;
		}
	}

	private static void row(GuiGraphicsExtractor g, Font font, JsonObject c, int x, int y, String tag) {
		String lv = "Lv." + c.get("level").getAsInt();
		String name = c.get("name").getAsString();
		int room = WIDTH - 4 - font.width(lv);
		if (font.width(name) > room) name = font.plainSubstrByWidth(name, room - 6) + "..";
		g.text(font, name, x + 1, y, 0xFF000000 | c.get("color").getAsInt());
		g.text(font, lv, x + WIDTH - font.width(lv), y, 0xFFFFFFFF);
		int hp = c.get("hp").getAsInt(), max = c.has("maxHp") ? c.get("maxHp").getAsInt() : hp;
		bar(g, x + 1, y + 11, 56, 4, max <= 0 ? 0 : hp / (float) max, hpColor(hp, max));
		String hpText = tag != null ? tag : hp + "/" + max;
		g.text(font, hpText, x + 61, y + 9, tag != null ? 0xFF9FD0FF : 0xFFDDDDDD);
	}

	static int hpColor(int hp, int max) {
		float f = max <= 0 ? 0 : hp / (float) max;
		return f > 0.5f ? 0xFF55DD55 : f > 0.2f ? 0xFFE0C040 : 0xFFE05050;
	}

	static void bar(GuiGraphicsExtractor g, int x, int y, int w, int h, float fraction, int color) {
		g.fill(x, y, x + w, y + h, 0xFF303030);
		g.fill(x, y, x + Math.round(w * Math.clamp(fraction, 0f, 1f)), y + h, color);
	}

	private static Path configFile() {
		return FabricLoader.getInstance().getConfigDir().resolve("ahrivaea_party.json");
	}

	private static void loadConfig() {
		try {
			if (!Files.exists(configFile())) return;
			JsonObject o = JsonParser.parseString(Files.readString(configFile())).getAsJsonObject();
			if (o.has("summary")) showSummary = o.get("summary").getAsBoolean();
		} catch (Exception e) {
			Ahrivaea.LOGGER.warn("Couldn't read {}: {}", configFile(), e.toString());
		}
	}

	static void setShowSummary(boolean on) {
		showSummary = on;
		try {
			JsonObject o = new JsonObject();
			o.addProperty("summary", on);
			Files.createDirectories(configFile().getParent());
			Files.writeString(configFile(), o.toString());
		} catch (Exception e) {
			Ahrivaea.LOGGER.warn("Couldn't save {}: {}", configFile(), e.toString());
		}
	}
}
