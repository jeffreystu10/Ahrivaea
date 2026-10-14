package dev.ahrivaea.client;

import dev.ahrivaea.Ahrivaea;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.model.geom.ModelLayerLocation;

public class AhrivaeaClient implements ClientModInitializer {
	public static final ModelLayerLocation CREATURE_LAYER = new ModelLayerLocation(Ahrivaea.id("creature"), "main");

	@Override
	public void onInitializeClient() {
		ModelLayerRegistry.registerModelLayer(CREATURE_LAYER, CreatureModel::createBodyLayer);
		EntityRendererRegistry.register(Ahrivaea.CREATURE, CreatureRenderer::new);
	}
}
