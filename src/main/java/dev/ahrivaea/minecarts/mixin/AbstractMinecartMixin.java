package dev.ahrivaea.minecarts.mixin;

import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Always use the newer minecart physics (it follows the track at any speed instead of flying off at corners). */
@Mixin(AbstractMinecart.class)
public class AbstractMinecartMixin {
	@Inject(method = "useExperimentalMovement", at = @At("HEAD"), cancellable = true)
	private static void ahrivaea$alwaysNewPhysics(Level level, CallbackInfoReturnable<Boolean> cir) {
		// on a player's computer, only when the server they're on has Fast Minecarts too
		if (level != null && level.isClientSide() && !dev.ahrivaea.minecarts.MinecartsMod.serverHasMod) return;
		cir.setReturnValue(true);
	}
}
