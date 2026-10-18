package dev.ahrivaea.lights;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Ahrivaea Handheld Lights: hold a torch (or any light) and the space around you lights up, no placing needed. */
public class LightsMod implements ModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger("ahrivaea_lights");

	@Override
	public void onInitialize() {
		Present.register();
		HeldLights.init();
		LOGGER.info("Ahrivaea Handheld Lights loaded");
	}
}
