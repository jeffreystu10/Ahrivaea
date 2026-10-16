package dev.ahrivaea.woodcutting;

import net.fabricmc.api.ModInitializer;

/**
 * Ahrivaea Woodcutting: logs and planks in the stonecutter. The recipes are data (data/ahrivaea/recipe/woodcutting/);
 * this only tells players' games the server has the mod (see {@link Present}).
 */
public class WoodcuttingMod implements ModInitializer {
	@Override
	public void onInitialize() {
		Present.register();
	}
}
