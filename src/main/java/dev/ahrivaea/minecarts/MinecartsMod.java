package dev.ahrivaea.minecarts;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ahrivaea Fast Minecarts: minecarts top out at 30 blocks a second (vanilla: 8). Uses Minecraft's newer minecart
 * physics, which stays on the rails at high speed, in every world (vanilla only has it behind an experiment).
 */
public class MinecartsMod implements ModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger("ahrivaea_minecarts");

	/** Top speed on rails, blocks per second (half in water; furnace minecarts go half this). */
	public static final double SPEED = 30;

	/**
	 * On a player's computer: whether the server they're on has this mod (set every tick by MinecartsClient). Their
	 * game only switches to the newer minecart physics then, so on a server without the mod carts behave as usual.
	 */
	public static volatile boolean serverHasMod = true;

	@Override
	public void onInitialize() {
		Present.register(); // so players' copies of this mod switch on only on servers that have it
		LOGGER.info("Ahrivaea Fast Minecarts loaded: top speed {} blocks/sec", SPEED);
	}
}
