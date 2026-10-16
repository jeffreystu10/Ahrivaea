package dev.ahrivaea.minecarts.client;

import dev.ahrivaea.minecarts.MinecartsMod;
import dev.ahrivaea.minecarts.Present;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** Keeps track of whether the server the player is on has Fast Minecarts (see {@link MinecartsMod#serverHasMod}). */
public class MinecartsClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientTickEvents.START_CLIENT_TICK.register(mc -> {
			boolean has;
			try {
				has = mc.getConnection() != null && ClientPlayNetworking.canSend(Present.TYPE);
			} catch (IllegalStateException notInGame) {
				has = false;
			}
			MinecartsMod.serverHasMod = has;
		});
	}
}
