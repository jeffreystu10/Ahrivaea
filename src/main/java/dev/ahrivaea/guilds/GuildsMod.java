package dev.ahrivaea.guilds;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Ahrivaea Guilds: groups of players who can't hurt each other and share the towers their members own. */
public class GuildsMod implements ModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger("ahrivaea_guilds");

	@Override
	public void onInitialize() {
		Present.register(); // so players' copies of this mod switch on only on servers that have it
		Guilds.init();
		CommandRegistrationCallback.EVENT.register((dispatcher, access, env) -> Guilds.registerCommands(dispatcher));
		LOGGER.info("Ahrivaea Guilds loaded");
	}
}
