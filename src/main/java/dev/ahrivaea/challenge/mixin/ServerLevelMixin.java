package dev.ahrivaea.challenge.mixin;

import dev.ahrivaea.challenge.Challenges;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Tower protection works like spawn protection: Minecraft asks the level whether a player may change a block
 * (breaking, placing, using an item on it, buckets) and around a tower the answer is no, except for its owner.
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {
	@Inject(method = "mayInteract", at = @At("HEAD"), cancellable = true)
	private void ahrivaea$towerProtection(Entity entity, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
		if (Challenges.protectedFrom((ServerLevel) (Object) this, entity, pos, true) != null) cir.setReturnValue(false);
	}
}
