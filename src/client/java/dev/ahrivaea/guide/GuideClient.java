package dev.ahrivaea.guide;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.sdl.SDLScancode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** The Ahrivaea Guide: press G (or /guide) for what each mod does and all their recipes. */
public class GuideClient implements ClientModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger("ahrivaea_guide");
	public static KeyMapping OPEN;
	/** Mods the player has already read about (kept in config/ahrivaea_guide.json). */
	private static final Set<String> SEEN = new LinkedHashSet<>();
	private static String pendingOpen;
	private static int announceIn = -1;

	@Override
	public void onInitializeClient() {
		loadSeen();
		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("ahrivaea_guide", "guide"));
		OPEN = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.ahrivaea_guide.open", InputConstants.Type.KEYBOARD, SDLScancode.SDL_SCANCODE_G, category));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (OPEN.consumeClick()) if (client.gui.screen() == null) open(null);
			// a command's chat screen closes after it runs, so the guide opens on the next tick
			if (pendingOpen != null && client.gui.screen() == null) {
				String mod = pendingOpen;
				pendingOpen = null;
				open(mod.isEmpty() ? null : mod);
			}
			if (announceIn > 0 && --announceIn == 0) announce(client);
		});
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registries) -> dispatcher.register(ClientCommands.literal("guide")
				.executes(ctx -> {
					pendingOpen = "";
					return 1;
				})
				.then(ClientCommands.argument("mod", StringArgumentType.greedyString()).executes(ctx -> {
					pendingOpen = StringArgumentType.getString(ctx, "mod");
					return 1;
				}))));
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> announceIn = 60);
		LOGGER.info("Ahrivaea Guide loaded: press G");
	}

	/** Opens the guide, on a mod's page if one is named (by key, id or name). */
	public static void open(String mod) {
		Minecraft mc = Minecraft.getInstance();
		GuideData data = GuideData.load().forThisServer();
		if (data.mods.isEmpty()) {
			if (mc.player != null) mc.player.sendOverlayMessage(Component.literal("This server doesn't use any Ahrivaea mods."));
			return;
		}
		mc.gui.setScreen(new GuideScreen(data, mod));
	}

	/** After joining a world: say which mods are new since the guide was last read. */
	private static void announce(Minecraft client) {
		if (client.player == null) return;
		GuideData data = GuideData.load().forThisServer();
		List<String> fresh = data.mods.stream().filter(m -> !seen(m.key())).map(GuideData.Mod::name).toList();
		if (fresh.isEmpty()) return;
		String key = OPEN.getTranslatedKeyMessage().getString();
		client.player.sendSystemMessage(Component.literal("[Ahrivaea Guide] ").withStyle(ChatFormatting.GOLD)
				.append(Component.literal((fresh.size() == data.mods.size() ? "Mods on this server: " : "New: ") + String.join(", ", fresh)
						+ ". Press " + key + " (or type /guide) to see what they do and how to craft their items.").withStyle(ChatFormatting.WHITE)));
	}

	public static boolean seen(String modKey) {
		return SEEN.contains(modKey);
	}

	public static void markSeen(String modKey) {
		if (SEEN.add(modKey)) saveSeen();
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("ahrivaea_guide.json");
	}

	private static void loadSeen() {
		try {
			if (!Files.exists(file())) return;
			JsonObject o = JsonParser.parseString(Files.readString(file())).getAsJsonObject();
			if (o.has("seen")) o.getAsJsonArray("seen").forEach(e -> SEEN.add(e.getAsString()));
		} catch (Exception e) {
			LOGGER.warn("Guide: couldn't read {}: {}", file(), e.toString());
		}
	}

	private static void saveSeen() {
		try {
			JsonObject o = new JsonObject();
			JsonArray a = new JsonArray();
			SEEN.forEach(a::add);
			o.add("seen", a);
			Files.createDirectories(file().getParent());
			Files.writeString(file(), o.toString());
		} catch (Exception e) {
			LOGGER.warn("Guide: couldn't save {}: {}", file(), e.toString());
		}
	}
}
