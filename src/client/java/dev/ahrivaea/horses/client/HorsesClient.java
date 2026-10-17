package dev.ahrivaea.horses.client;

import dev.ahrivaea.horses.HorseSpeed;
import dev.ahrivaea.horses.HorsesMod;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.Vec3;

/**
 * Horse stats on screen: look at a horse (up to 16 blocks away) to see its speed, tier, jump and health next to the
 * crosshair; while riding one they show in the top-left corner (with the speed you're going now). They're also
 * beside the horse's inventory screen.
 */
public class HorsesClient implements ClientModInitializer {
	public static final double LOOK_RANGE = 16;

	/** How fast the horse you're riding is going now, from how far it moved each tick (smoothed over half a second). */
	static double currentSpeed;
	private static Vec3 lastPos;

	@Override
	public void onInitializeClient() {
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (mc.player != null && mc.player.getVehicle() instanceof AbstractHorse h) {
				Vec3 now = h.position();
				double step = lastPos == null ? 0 : Math.hypot(now.x - lastPos.x, now.z - lastPos.z) * 20;
				currentSpeed += (Math.min(step, 100) - currentSpeed) * 0.2;
				lastPos = now;
			} else {
				lastPos = null;
				currentSpeed = 0;
			}
		});
		HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, HorsesMod.id("horse_stats"), (g, delta) -> {
			Minecraft mc = Minecraft.getInstance();
			if (mc.player == null || mc.level == null || mc.gui.hud.isHidden() || !serverHasMod()) return;
			if (mc.player.getVehicle() instanceof AbstractHorse h && HorseSpeed.covers(h)) {
				// top left, or a quarter of the way down while the F3 debug text is on screen
				HorseStats.draw(g, h, true, 6, mc.getDebugOverlay().showDebugScreen() ? g.guiHeight() / 4 : 6);
				return;
			}
			AbstractHorse looked = lookedAt(mc);
			if (looked != null) HorseStats.draw(g, looked, false, g.guiWidth() / 2 + 14, g.guiHeight() / 2 - 8);
		});
	}

	/** Whether the server the player is on has Swift Horses; without it the stats would be wrong, so they're hidden. */
	public static boolean serverHasMod() {
		try {
			return Minecraft.getInstance().getConnection() != null
					&& net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.canSend(dev.ahrivaea.horses.Present.TYPE);
		} catch (IllegalStateException notInGame) {
			return false;
		}
	}

	static AbstractHorse lookedAt(Minecraft mc) {
		var player = mc.player;
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getViewVector(1f).scale(LOOK_RANGE));
		var hit = ProjectileUtil.getEntityHitResult(player, eye, end, player.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1),
				e -> HorseSpeed.covers(e) && !e.isSpectator(), LOOK_RANGE * LOOK_RANGE);
		if (hit == null) return null;
		// not through walls
		var block = mc.level.clip(new net.minecraft.world.level.ClipContext(eye, hit.getLocation(), net.minecraft.world.level.ClipContext.Block.COLLIDER,
				net.minecraft.world.level.ClipContext.Fluid.NONE, player));
		if (block.getType() != net.minecraft.world.phys.HitResult.Type.MISS && block.getLocation().distanceToSqr(eye) < hit.getLocation().distanceToSqr(eye)) return null;
		return (AbstractHorse) hit.getEntity();
	}
}
