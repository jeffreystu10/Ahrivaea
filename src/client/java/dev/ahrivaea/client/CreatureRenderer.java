package dev.ahrivaea.client;

import dev.ahrivaea.Ahrivaea;
import dev.ahrivaea.creature.CreatureEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;

public class CreatureRenderer extends MobRenderer<CreatureEntity, CreatureRenderState, CreatureModel> {
	public CreatureRenderer(EntityRendererProvider.Context context) {
		super(context, new CreatureModel(context.bakeLayer(AhrivaeaClient.CREATURE_LAYER)), 0.4f);
	}

	@Override
	public CreatureRenderState createRenderState() {
		return new CreatureRenderState();
	}

	@Override
	public void extractRenderState(CreatureEntity entity, CreatureRenderState state, float partialTick) {
		super.extractRenderState(entity, state, partialTick);
		state.texture = Ahrivaea.id("textures/entity/creature/" + (entity.getSpeciesId().isEmpty() ? "emberkit" : entity.getSpeciesId()) + ".png");
		state.sitting = entity.isInSittingPose();
	}

	@Override
	public Identifier getTextureLocation(CreatureRenderState state) {
		return state.texture;
	}
}
