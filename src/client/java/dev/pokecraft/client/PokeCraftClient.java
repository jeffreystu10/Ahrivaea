package dev.pokecraft.client;

import dev.pokecraft.PokeCraft;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.model.geom.ModelLayerLocation;

public class PokeCraftClient implements ClientModInitializer {
	public static final ModelLayerLocation CREATURE_LAYER = new ModelLayerLocation(PokeCraft.id("creature"), "main");

	@Override
	public void onInitializeClient() {
		EntityModelLayerRegistry.registerModelLayer(CREATURE_LAYER, CreatureModel::createBodyLayer);
		EntityRendererRegistry.register(PokeCraft.CREATURE, CreatureRenderer::new);
	}
}
