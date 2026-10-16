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
		state.realSize = state.geo != null && GeoModels.realSize(species, influence);
		state.modelScale = state.geo != null ? GeoModels.scale(species, influence) : 1f;
		state.extent = state.realSize ? GeoModels.extent(species, influence) * state.modelScale
				: (state.geo != null ? GeoModels.extent(species, influence) : 0.7f) * state.modelScale * entity.getScale();
		// off the ground: nothing solid just below (the client's onGround isn't reliable for mobs standing still)
		state.flying = !entity.isInWater() && !entity.isPassenger()
				&& entity.level().noCollision(entity, entity.getBoundingBox().move(0, -0.3, 0));
		if (state.realSize && state.nameTagAttachment != null) {
			// above the model, not the (smaller) hitbox
			float h = GeoModels.height(species, influence) * state.modelScale * battleShrink(state.extent);
			state.nameTagAttachment = new net.minecraft.world.phys.Vec3(0, h + 0.3, 0);
		}
		state.texture = GeoModels.texture(species, influence);
		state.sitting = entity.isInSittingPose();
		// creatures without their own model yet: the placeholder, tinted the colour of their influence
		state.tint = state.geo == null ? 0xFF000000 | entity.getInfluence().color() : -1;
	}

	@Override
	public void submit(CreatureRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		// one renderer serves every species, so pick this creature's model before drawing
		this.model = state.geo != null ? state.geo : placeholder;
		super.submit(state, poseStack, collector, camera);
	}

	/** In a battle seen from above, every creature fits its square. */
	private static float battleShrink(float extent) {
		return dev.ahrivaea.client.battle.BattleClient.active() && extent > 1.3f ? 1.3f / extent : 1f;
	}

	@Override
	protected void scale(CreatureRenderState state, PoseStack poseStack) {
		// a real-size model undoes the hitbox scale the renderer has already applied
		float s = state.modelScale * (state.realSize && state.scale > 0 ? 1f / state.scale : 1f) * battleShrink(state.extent);
		poseStack.scale(s, s, s);
	}

	@Override
	protected float getShadowRadius(CreatureRenderState state) {
		if (state.realSize) return Math.min(state.extent * battleShrink(state.extent) * 0.45f, 6f);
		return super.getShadowRadius(state) * (state.geo != null ? 2.5f : 1f);
	}

	@Override
	protected net.minecraft.world.phys.AABB getBoundingBoxForCulling(CreatureEntity entity, float partialTick) {
		// big models reach well past their hitbox; don't let them vanish when the hitbox goes off screen
		var box = super.getBoundingBoxForCulling(entity, partialTick);
		String species = entity.getSpeciesId(), influence = entity.getInfluence().getSerializedName();
		if (species.isEmpty() || !GeoModels.realSize(species, influence)) return box;
		float half = GeoModels.extent(species, influence) * GeoModels.scale(species, influence) / 2f;
		float h = GeoModels.height(species, influence) * GeoModels.scale(species, influence);
		return box.minmax(new net.minecraft.world.phys.AABB(entity.getX() - half, entity.getY(), entity.getZ() - half,
				entity.getX() + half, entity.getY() + h, entity.getZ() + half));
	}

	@Override
	protected int getModelTint(CreatureRenderState state) {
		return state.tint;
	}

	@Override
	public Identifier getTextureLocation(CreatureRenderState state) {
		return state.texture;
	}
}
