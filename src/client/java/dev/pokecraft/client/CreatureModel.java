package dev.pokecraft.client;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/** Placeholder four-legged body shared by the test species; real per-species models come later. */
public class CreatureModel extends EntityModel<CreatureRenderState> {
	private final ModelPart head, body, tail, legFL, legFR, legBL, legBR;

	public CreatureModel(ModelPart root) {
		super(root);
		head = root.getChild("head");
		body = root.getChild("body");
		tail = root.getChild("tail");
		legFL = root.getChild("leg_fl");
		legFR = root.getChild("leg_fr");
		legBL = root.getChild("leg_bl");
		legBR = root.getChild("leg_br");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-3f, -3f, -6f, 6, 6, 6), PartPose.offset(0f, 13f, -5f));
		head.addOrReplaceChild("ears", CubeListBuilder.create().texOffs(24, 0).addBox(-3f, -5f, -3f, 2, 2, 1).texOffs(24, 0).addBox(1f, -5f, -3f, 2, 2, 1), PartPose.ZERO);
		root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 12).addBox(-4f, -3f, -5f, 8, 6, 10), PartPose.offset(0f, 16f, 0f));
		root.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(44, 12).addBox(-1f, -1f, 0f, 2, 2, 6), PartPose.offsetAndRotation(0f, 14f, 5f, 0.5f, 0f, 0f));
		CubeListBuilder leg = CubeListBuilder.create().texOffs(36, 12).addBox(-1f, 0f, -1f, 2, 5, 2);
		root.addOrReplaceChild("leg_fl", leg, PartPose.offset(-2.5f, 19f, -3.5f));
		root.addOrReplaceChild("leg_fr", leg, PartPose.offset(2.5f, 19f, -3.5f));
		root.addOrReplaceChild("leg_bl", leg, PartPose.offset(-2.5f, 19f, 3.5f));
		root.addOrReplaceChild("leg_br", leg, PartPose.offset(2.5f, 19f, 3.5f));
		return LayerDefinition.create(mesh, 64, 32);
	}

	@Override
	public void setupAnim(CreatureRenderState state) {
		super.setupAnim(state);
		head.yRot = state.yRot * Mth.DEG_TO_RAD;
		head.xRot = state.xRot * Mth.DEG_TO_RAD;
		float swing = Mth.cos(state.walkAnimationPos * 0.9f) * 1.2f * state.walkAnimationSpeed;
		legFL.xRot = swing;
		legBR.xRot = swing;
		legFR.xRot = -swing;
		legBL.xRot = -swing;
		tail.yRot = Mth.cos(state.ageInTicks * 0.15f) * 0.3f;
		if (state.sitting) {
			body.y += 2f;
			head.y += 2f;
			legBL.xRot = legBR.xRot = -1.4f;
		}
	}
}
