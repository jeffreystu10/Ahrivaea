package dev.ahrivaea.map.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import dev.ahrivaea.map.Markers;
import dev.ahrivaea.map.Present;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.sdl.SDLScancode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * The map on the player's side: records what they see into {@link MapTiles}, draws the minimap in the top-right
 * corner (N turns it on and off) and opens the full map with M. It only does anything on servers with Ahrivaea Map.
 */
public class MapClient implements ClientModInitializer {
	public static KeyMapping OPEN, MINIMAP;
	public static boolean minimap = true;

	/** The latest players and creatures from the server, and when they came. */
	public static volatile Markers.Payload markers = new Markers.Payload(List.of(), List.of());
	public static volatile long markersAt;

	private static int ticks;

	@Override
	public void onInitializeClient() {
		loadSettings();
		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("ahrivaea_map", "map"));
		OPEN = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.ahrivaea_map.open", InputConstants.Type.KEYBOARD, SDLScancode.SDL_SCANCODE_M, category));
		MINIMAP = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.ahrivaea_map.minimap", InputConstants.Type.KEYBOARD, SDLScancode.SDL_SCANCODE_N, category));

		ClientPlayNetworking.registerGlobalReceiver(Markers.Payload.TYPE, (payload, ctx) -> {
			markers = payload;
			markersAt = System.currentTimeMillis();
		});
		ClientChunkEvents.CHUNK_LOAD.register((level, chunk) -> {
			if (serverHasMod()) MapTiles.queue(chunk.getPos().x(), chunk.getPos().z());
		});
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			boolean on = serverHasMod();
			while (OPEN.consumeClick()) if (on && mc.gui.screen() == null) mc.gui.setScreen(new MapScreen());
			while (MINIMAP.consumeClick()) if (on) setMinimap(!minimap);
			ticks++;
			// the chunks right around you every second; everything in view every 5 seconds (so changes get drawn)
			if (on && ticks % 20 == 0) MapTiles.queueAround(mc, 1);
			if (on && ticks % 100 == 0 && MapTiles.queueEmpty()) MapTiles.queueAround(mc, mc.options.getEffectiveRenderDistance());
			MapTiles.tick(mc, on && mc.level != null);
			if (ticks % 600 == 0) MapTiles.saveAll();
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> {
			markers = new Markers.Payload(List.of(), List.of());
			mc.execute(MapTiles::closeAll);
		});
		HudElementRegistry.attachElementAfter(VanillaHudElements.MOB_EFFECTS, Identifier.fromNamespaceAndPath("ahrivaea_map", "minimap"), (g, delta) -> {
			Minecraft mc = Minecraft.getInstance();
			if (!minimap || mc.player == null || mc.level == null || mc.gui.hud.isHidden() || !serverHasMod()) return;
			MapView.minimap(g, mc);
		});
	}

	/** Whether the server the player is on has Ahrivaea Map (the map is off everywhere else). */
	public static boolean serverHasMod() {
		try {
			return Minecraft.getInstance().getConnection() != null && ClientPlayNetworking.canSend(Present.TYPE);
		} catch (IllegalStateException notInGame) {
			return false;
		}
	}

	/** Markers older than this are left off (the server's stopped sending them). */
	static boolean markersFresh() {
		return System.currentTimeMillis() - markersAt < 3000;
	}

	private static Path settingsFile() {
		return FabricLoader.getInstance().getConfigDir().resolve("ahrivaea_map.json");
	}

	private static void loadSettings() {
		try {
			if (Files.exists(settingsFile())) {
				JsonObject o = JsonParser.parseString(Files.readString(settingsFile())).getAsJsonObject();
				if (o.has("minimap")) minimap = o.get("minimap").getAsBoolean();
			}
		} catch (Exception ignored) {
		}
	}

	public static void setMinimap(boolean on) {
		minimap = on;
		try {
			JsonObject o = new JsonObject();
			o.addProperty("minimap", on);
			Files.writeString(settingsFile(), o.toString());
		} catch (Exception ignored) {
		}
	}
}
