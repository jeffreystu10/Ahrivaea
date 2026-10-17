package dev.ahrivaea.client;

import dev.ahrivaea.Ahrivaea;
import dev.ahrivaea.creature.CreatureEntity;
import dev.ahrivaea.creature.Mounts;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** While riding a creature: its speed (top and now), tier and what its next tier needs, in the top-left corner. */
public final class MountHud {
	private MountHud() {}

	/** How fast the mount is going now, from how far it moved each tick (smoothed over half a second). */
	public static double currentSpeed;
	private static Vec3 lastPos;

	private record Line(String text, int color) {}

	/** Where the top-left panels start: the top, or a third of the way down while the F3 debug text is on screen. */
	static int hudTop(Minecraft mc, int guiHeight) {
		return mc.getDebugOverlay().showDebugScreen() ? guiHeight / 3 : 6;
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (mc.player != null && mc.player.getVehicle() instanceof CreatureEntity c) {
				Vec3 now = c.position();
				// flying and swimming count up and down too
				double step = lastPos == null ? 0 : now.distanceTo(lastPos) * 20;
				currentSpeed += (Math.min(step, 150) - currentSpeed) * 0.2;
				lastPos = now;
			} else {
				lastPos = null;
				currentSpeed = 0;
			}
		});
		HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, Ahrivaea.id("mount_stats"), (g, delta) -> {
			Minecraft mc = Minecraft.getInstance();
			if (mc.player == null || mc.gui.hud.isHidden() || !(mc.player.getVehicle() instanceof CreatureEntity c)) return;
			List<Line> lines = new ArrayList<>();
			lines.add(new Line(c.getCustomName() != null ? c.getCustomName().getString() : "Creature", 0xFFFFFF55));
			lines.add(new Line("Speed: " + String.format("%.1f", c.getTopSpeed()) + " blocks/sec", 0xFFFFFFFF));
			lines.add(new Line("  now " + String.format("%.1f", currentSpeed), 0xFFAAAAAA));
			int tier = c.getRideTier();
			lines.add(new Line("Tier " + tier + " (x" + Mounts.MULTIPLIER[Math.clamp(tier, 0, Mounts.MAX_TIER)] + ")"
					+ (c.ridesFlying() ? ", flies" : c.ridesSwimming() ? ", swims" : ""), 0xFFFFD27F));
			if (c.ridesFlying()) lines.add(new Line("Jump: take off / rise. Look where to fly.", 0xFFAAAAAA));
			else if (c.ridesSwimming()) lines.add(new Line("In water: look where to swim, jump to rise.", 0xFFAAAAAA));
			Mounts.Step step = Mounts.stepFrom(c.getMountTier());
			if (step != null) lines.add(new Line("Next tier: " + c.getMountFed() + "/" + step.amount() + " " + step.treat().getDefaultInstance().getHoverName().getString(), 0xFF9FD0FF));
			else if (tier < Mounts.MAX_TIER) lines.add(new Line("Tier " + (tier + 1) + ": Speed " + (tier < 4 ? Mounts.TIER_4_SPEED : Mounts.TIER_5_SPEED) + " (levels raise it)", 0xFF9FD0FF));
			var font = mc.font;
			int w = 0;
			for (Line l : lines) w = Math.max(w, font.width(l.text()));
			int x = 6, y = hudTop(mc, g.guiHeight());
			g.fill(x - 3, y - 3, x + w + 3, y + lines.size() * 10 + 1, 0xB0000000);
			for (int i = 0; i < lines.size(); i++) g.text(font, lines.get(i).text(), x, y + i * 10, lines.get(i).color());
		});
	}
}
