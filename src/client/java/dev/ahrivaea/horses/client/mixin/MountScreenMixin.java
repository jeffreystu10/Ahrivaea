package dev.ahrivaea.horses.client.mixin;

import dev.ahrivaea.horses.HorseSpeed;
import dev.ahrivaea.horses.client.HorseStats;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractMountInventoryScreen;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The horse's stats beside its inventory screen. */
@Mixin(AbstractMountInventoryScreen.class)
public class MountScreenMixin {
	@Shadow
	@Final
	protected LivingEntity mount;

	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void ahrivaea$horseStats(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		if (!(mount instanceof AbstractHorse h) || !HorseSpeed.covers(h)) return;
		Screen screen = (Screen) (Object) this;
		// the inventory is 176 x 166, centred
		HorseStats.draw(g, h, false, (screen.width + 176) / 2 + 8, (screen.height - 166) / 2 + 4);
	}
}
