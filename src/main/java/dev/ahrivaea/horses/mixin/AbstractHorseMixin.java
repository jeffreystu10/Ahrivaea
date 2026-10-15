package dev.ahrivaea.horses.mixin;

import dev.ahrivaea.horses.HorseSpeed;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Foals inherit a speed tier from their parents (vanilla still mixes the parents' own speeds as usual). */
@Mixin(AbstractHorse.class)
public class AbstractHorseMixin {
	@Inject(method = "setOffspringAttributes", at = @At("TAIL"))
	private void ahrivaea$inheritSpeedTier(AgeableMob partner, AbstractHorse baby, CallbackInfo ci) {
		AbstractHorse self = (AbstractHorse) (Object) this;
		if (!HorseSpeed.covers(baby) || !(partner instanceof AbstractHorse other)) return;
		int a = HorseSpeed.covers(self) ? HorseSpeed.of(self).tier() : 0;
		int b = HorseSpeed.covers(other) ? HorseSpeed.of(other).tier() : 0;
		HorseSpeed.set(baby, new HorseSpeed(HorseSpeed.foalTier(a, b, self.getRandom()), 0));
	}
}
