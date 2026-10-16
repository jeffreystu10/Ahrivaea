package dev.ahrivaea.stellar;

import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Ahrivaea Stellar Gear: netherite tools and armor upgraded with a nether star. */
public class StellarMod implements ModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger("ahrivaea_stellar");

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath("ahrivaea", path);
	}

	@Override
	public void onInitialize() {
		Present.register(); // so players' copies of this mod switch on only on servers that have it
		Stellar.init();
		LOGGER.info("Ahrivaea Stellar Gear loaded");
	}
}
