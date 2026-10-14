package dev.ahrivaea.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.ahrivaea.creature.CreatureEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;

/** Draws each creature with the model and texture of its species and influence (see {@link GeoModels}). */
public class CreatureRenderer extends MobRenderer<CreatureEntity, CreatureRenderState, EntityModel<CreatureRenderState>> {
	private final CreatureModel placeholder;

	public CreatureRenderer(EntityRendererProvider.Context context) {
		this(context, new CreatureModel(context.bakeLayer(AhrivaeaClient.CREATURE_LAYER)));
	}

	private CreatureRenderer(EntityRendererProvider.Context context, CreatureModel placeholder) {
		super(context, placeholder, 0.4f);
		this.placeholder = placeholder;
	}

	@Override
	public CreatureRenderState createRenderState() {
		return new CreatureRenderState();
	}

	@Override
	public void extractRenderState(CreatureEntity entity, CreatureRenderState state, float partialTick) {
		super.extractRenderState(entity, state, partialTick);
		String species = entity.getSpeciesId(), influence = entity.getInfluence().getSerializedName();
		state.geo = species.isEmpty() ? null : GeoModels.model(species, influence);
		state.modelScale = state.geo != null ? GeoModels.scale(species) : 1f;
		state.extent = (state.geo != null ? GeoModels.extent(species, influence) : 0.7f) * state.modelScale * entity.getScale();
		state.texture = GeoModels.texture(species, influence);
		state.sitting = entity.isInSittingPose();
	}

	@Override
	public void submit(CreatureRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		// one renderer serves every species, so pick this creature's model before drawing
		this.model = state.geo != null ? state.geo : placeholder;
		super.submit(state, poseStack, collector, camera);
	}

	@Override
	protected void scale(CreatureRenderState state, PoseStack poseStack) {
		float s = state.modelScale;
		// in a battle seen from above, every creature fits its square
		if (dev.ahrivaea.client.battle.BattleClient.active() && state.extent > 1.3f) s *= 1.3f / state.extent;
		poseStack.scale(s, s, s);
	}

	@Override
	protected float getShadowRadius(CreatureRenderState state) {
		return super.getShadowRadius(state) * (state.geo != null ? 2.5f : 1f);
	}

	@Override
	public Identifier getTextureLocation(CreatureRenderState state) {
		return state.texture;
	}
}
