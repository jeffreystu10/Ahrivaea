package dev.ahrivaea.client.mixin;

import dev.ahrivaea.client.battle.BattleClient;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** During a battle the camera looks straight down on the battlefield (north at the top of the screen). */
@Mixin(Camera.class)
public abstract class CameraMixin {
	@Shadow
	private boolean detached;

	@Shadow
	protected abstract void setPosition(double x, double y, double z);

	@Shadow
	protected abstract void setRotation(float yRot, float xRot);

	@Inject(method = "alignWithEntity", at = @At("TAIL"))
	private void ahrivaea$battleView(float partialTicks, CallbackInfo ci) {
		if (!BattleClient.active()) return;
		this.detached = true;
		this.setPosition(BattleClient.camX(), BattleClient.camY(), BattleClient.camZ());
		this.setRotation(180f, 90f);
	}
}
