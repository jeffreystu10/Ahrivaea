package dev.ahrivaea.map;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ahrivaea Map: a minimap and a full-screen map (M) of everywhere you've been, with the creatures around you and
 * every player on the server. The map itself is drawn by each player's game from what it has seen; the server tells
 * it where the other players and the creatures are.
 */
public class MapMod implements ModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger("ahrivaea_map");

	@Override
	public void onInitialize() {
		Present.register(); // so players' copies of this mod switch on only on servers that have it
		Markers.init();
		LOGGER.info("Ahrivaea Map loaded");
	}
}
