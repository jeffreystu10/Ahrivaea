package dev.ahrivaea.challenge;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Ahrivaea Tower Challenges: a ruined tower over every stronghold for the server to rebuild together. */
public class TowersMod implements ModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger("ahrivaea_towers");

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath("ahrivaea", path);
	}

	@Override
	public void onInitialize() {
		Present.register(); // so players' copies of this mod switch on only on servers that have it
		ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(id("challenges"), (ResourceManagerReloadListener) ChallengeDef::reload);
		ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(id("tower_palettes"), (ResourceManagerReloadListener) TowerPalette::reload);
		Challenges.init();
		StrongholdTowers.init();
		CommandRegistrationCallback.EVENT.register((dispatcher, access, env) -> Challenges.registerCommands(dispatcher));
		LOGGER.info("Ahrivaea Tower Challenges loaded");
	}
}
