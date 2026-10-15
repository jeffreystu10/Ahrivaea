package dev.ahrivaea.minecarts.mixin;

import dev.ahrivaea.minecarts.MinecartsMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.MinecartBehavior;
import net.minecraft.world.entity.vehicle.minecart.NewMinecartBehavior;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The top speed: our own instead of the experiment-only max_minecart_speed game rule. */
@Mixin(NewMinecartBehavior.class)
public abstract class NewMinecartBehaviorMixin extends MinecartBehavior {
	protected NewMinecartBehaviorMixin(AbstractMinecart minecart) {
		super(minecart);
	}

	@Inject(method = "getMaxSpeed", at = @At("HEAD"), cancellable = true)
	private void ahrivaea$fastMinecarts(ServerLevel level, CallbackInfoReturnable<Double> cir) {
		cir.setReturnValue(MinecartsMod.SPEED * (this.minecart.isInWater() ? 0.5 : 1.0) / 20.0);
	}
}
