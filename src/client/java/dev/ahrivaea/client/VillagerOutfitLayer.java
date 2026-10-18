package dev.ahrivaea.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.Identifier;

/**
 * A villager creature's clothes, drawn over its skin like a vanilla villager's: the plains outfit, and its
 * profession's clothes once it has one (each Ahrivaea profession borrows the look of a vanilla one).
 */
public class VillagerOutfitLayer extends RenderLayer<CreatureRenderState, EntityModel<CreatureRenderState>> {
	private static final Identifier PLAINS = Identifier.withDefaultNamespace("textures/entity/villager/type/plains.png");

	public VillagerOutfitLayer(RenderLayerParent<CreatureRenderState, EntityModel<CreatureRenderState>> parent) {
		super(parent);
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, CreatureRenderState state, float yRot, float xRot) {
		if (!state.villager || state.isInvisible) return;
		renderColoredCutoutModel(getParentModel(), PLAINS, poseStack, collector, light, state, -1, 1);
		if (!state.outfit.isEmpty())
			renderColoredCutoutModel(getParentModel(), Identifier.withDefaultNamespace("textures/entity/villager/profession/" + state.outfit + ".png"),
					poseStack, collector, light, state, -1, 2);
	}
}
