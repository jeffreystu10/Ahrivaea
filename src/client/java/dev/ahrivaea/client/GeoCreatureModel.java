package dev.ahrivaea.client;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * A creature model loaded from a .geo.json file. If the model came with animations, "idle" plays (or "fly" while
 * the creature is off the ground). On top of that, bones are moved by name: legs (and a biped's arms) swing when
 * walking, head and neck_* follow the look direction, and models without their own animations get gently
 * flapping wing_left/wing_right and swaying tail*.
 */
public class GeoCreatureModel extends EntityModel<CreatureRenderState> {
	private final List<ModelPart> legsA = new ArrayList<>(), legsB = new ArrayList<>(), necks = new ArrayList<>(), tail = new ArrayList<>();
	private final ModelPart head, wingLeft, wingRight;
	private final Function<String, ModelPart> lookup;
	private Map<String, GeoAnimation> animations = Map.of();

	public GeoCreatureModel(ModelPart root) {
		super(root);
		Function<String, ModelPart> find = root.createPartLookup();
		lookup = find;
		head = find.apply("head");
		wingLeft = find.apply("wing_left");
		wingRight = find.apply("wing_right");
		add(legsA, find, "front_left_leg", "back_right_leg", "leg_left", "arm_right");
		add(legsB, find, "front_right_leg", "back_left_leg", "leg_right", "arm_left");
		add(necks, find, "neck", "neck_2", "neck_3");
		add(tail, find, "tail", "tail_2", "tail_3", "tail_4", "tail_5", "tail_6", "tail_7", "tail_8");
	}

	void setAnimations(Map<String, GeoAnimation> animations) {
		this.animations = Map.copyOf(animations);
	}

	private static void add(List<ModelPart> list, Function<String, ModelPart> find, String... names) {
		for (String n : names) {
			ModelPart p = find.apply(n);
			if (p != null) list.add(p);
		}
	}

	@Override
	public void setupAnim(CreatureRenderState state) {
		super.setupAnim(state);
		GeoAnimation anim = state.flying && animations.containsKey("fly") ? animations.get("fly") : animations.get("idle");
		if (anim != null) anim.apply(lookup, state.ageInTicks / 20.0);
		float swing = Mth.cos(state.walkAnimationPos * 0.6f) * 0.8f * state.walkAnimationSpeed;
		for (ModelPart p : legsA) p.xRot += swing;
		for (ModelPart p : legsB) p.xRot -= swing;
		// spread the look across the neck so it bends rather than snapping at the head
		float yaw = state.yRot * Mth.DEG_TO_RAD, pitch = state.xRot * Mth.DEG_TO_RAD;
		int joints = necks.size() + (head != null ? 1 : 0);
		if (joints > 0) {
			for (ModelPart p : necks) p.yRot += yaw / joints;
			if (head != null) {
				head.yRot += yaw / joints;
				head.xRot += pitch * 0.5f;
			}
		}
		if (animations.isEmpty()) {
			float flap = Mth.sin(state.ageInTicks * 0.08f) * 0.12f;
			if (wingLeft != null) wingLeft.zRot -= flap;
			if (wingRight != null) wingRight.zRot += flap;
			for (int i = 0; i < tail.size(); i++) tail.get(i).yRot += Mth.cos(state.ageInTicks * 0.07f - i * 0.6f) * 0.06f;
		}
		if (state.sitting) {
			for (ModelPart p : legsA) p.xRot = -0.9f;
			for (ModelPart p : legsB) p.xRot = -0.9f;
		}
	}
}
