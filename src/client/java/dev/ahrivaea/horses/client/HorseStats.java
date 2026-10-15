package dev.ahrivaea.horses.client;

import dev.ahrivaea.horses.HorseSpeed;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.equine.SkeletonHorse;

import java.util.ArrayList;
import java.util.List;

/** A horse's stats as a little panel: speed (and tier), jump height, health, and what its next tier needs. */
public final class HorseStats {
	private HorseStats() {}

	public record Line(String text, int color) {}

	public static List<Line> lines(AbstractHorse h, boolean riding) {
		HorseSpeed s = HorseSpeed.of(h);
		List<Line> out = new ArrayList<>();
		out.add(new Line(h.getName().getString() + (h.isTamed() ? "" : " (wild)"), 0xFFFFFF55));
		out.add(new Line("Speed: " + String.format("%.1f", HorseSpeed.blocksPerSecond(h)) + " blocks/sec", 0xFFFFFFFF));
		if (riding) {
			double now = h.getDeltaMovement().horizontalDistance() * 20;
			out.add(new Line("  now " + String.format("%.1f", now), 0xFFAAAAAA));
		}
		out.add(new Line("Tier " + s.tier() + " (x" + HorseSpeed.MULTIPLIER[Math.clamp(s.tier(), 0, HorseSpeed.MAX_TIER)] + ")"
				+ (h instanceof SkeletonHorse ? ", always top speed" : ""), 0xFFFFD27F));
		out.add(new Line("Jump: " + String.format("%.1f", HorseSpeed.jumpHeight(h)) + " blocks", 0xFFFFFFFF));
		out.add(new Line("Health: " + String.format("%.1f", h.getMaxHealth() / 2) + " hearts", 0xFFFF8080));
		if (h instanceof SkeletonHorse || s.tier() >= HorseSpeed.MAX_TIER) return out;
		HorseSpeed.Step step = HorseSpeed.stepFrom(s.tier());
		if (step == null) out.add(new Line("Next tier: breed two tier " + s.tier() + " horses", 0xFF9FD0FF));
		else out.add(new Line("Next tier: " + s.fed() + "/" + step.amount() + " " + step.feed().getDefaultInstance().getHoverName().getString(), 0xFF9FD0FF));
		return out;
	}

	/** Draws the panel with its top-left corner at x, y. */
	public static void draw(GuiGraphicsExtractor g, AbstractHorse h, boolean riding, int x, int y) {
		var font = Minecraft.getInstance().font;
		List<Line> lines = lines(h, riding);
		int w = 0;
		for (Line l : lines) w = Math.max(w, font.width(l.text()));
		g.fill(x - 3, y - 3, x + w + 3, y + lines.size() * 10 + 1, 0xB0000000);
		for (int i = 0; i < lines.size(); i++) g.text(font, lines.get(i).text(), x, y + i * 10, lines.get(i).color());
	}
}
