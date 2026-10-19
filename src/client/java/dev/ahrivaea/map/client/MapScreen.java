package dev.ahrivaea.map.client;

import dev.ahrivaea.map.Markers;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The full map (M): everywhere you've explored in this dimension, the creatures near you and the other players.
 * Scroll to zoom (around the mouse), drag to move around; "Centre" goes back to following you.
 */
public class MapScreen extends Screen {
	public static final float MIN_SCALE = 0.125f, MAX_SCALE = 8f;
	private static final int TOP = 24, BOTTOM = 14;

	public double cx, cz;
	public float scale = 2f;
	public boolean follow = true;

	public MapScreen() {
		super(Component.literal("Map"));
	}

	@Override
	protected void init() {
		addRenderableWidget(Button.builder(minimapLabel(), b -> {
			MapClient.setMinimap(!MapClient.minimap);
			b.setMessage(minimapLabel());
		}).bounds(width - 254, 2, 90, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Centre on me"), b -> follow = true).bounds(width - 160, 2, 90, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose()).bounds(width - 66, 2, 60, 20).build());
	}

	private static Component minimapLabel() {
		return Component.literal("Minimap: " + (MapClient.minimap ? "On" : "Off"));
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		g.fill(0, 0, width, height, 0xFF0C0C10);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		if (minecraft.player == null || minecraft.level == null) return;
		if (follow) {
			cx = minecraft.player.getX();
			cz = minecraft.player.getZ();
		}
		MapView.Hover hover = new MapView.Hover();
		MapView.draw(g, 0, TOP, width, height - TOP - BOTTOM, cx, cz, scale, true, mouseX, mouseY, hover);
		super.extractRenderState(g, mouseX, mouseY, partialTick);
		g.text(font, "Map: " + dimensionName(minecraft.level.dimension().identifier().toString()), 6, 8, 0xFFFFD040);
		// the bottom line: the block under the mouse, the zoom, and anyone in another dimension
		String info;
		if (mouseY >= TOP && mouseY < height - BOTTOM) {
			int bx = (int) Math.floor(cx + (mouseX - width / 2.0) / scale), bz = (int) Math.floor(cz + (mouseY - (TOP + (height - TOP - BOTTOM) / 2.0)) / scale);
			info = "x " + bx + ", z " + bz;
		} else info = "";
		info += (info.isEmpty() ? "" : "   ") + (scale >= 1 ? "1 block = " + (int) scale + " px" : (int) (1 / scale) + " blocks = 1 px")
				+ "   Scroll to zoom, drag to move";
		g.text(font, info, 6, height - BOTTOM + 3, 0xFFAAAAAA);
		List<String> elsewhere = new ArrayList<>();
		String here = minecraft.level.dimension().identifier().toString();
		if (MapClient.markersFresh())
			for (Markers.PlayerMark p : MapClient.markers.players())
				if (!p.dimension().equals(here)) elsewhere.add(p.name() + " (" + dimensionName(p.dimension()) + ")");
		if (!elsewhere.isEmpty()) {
			String s = "Elsewhere: " + String.join(", ", elsewhere);
			g.text(font, s, width - 6 - font.width(s), height - BOTTOM + 3, 0xFF9FC8FF);
		}
		if (!hover.lines.isEmpty()) g.setComponentTooltipForNextFrame(font, hover.lines, mouseX, mouseY);
	}

	static String dimensionName(String id) {
		return switch (id) {
			case "minecraft:overworld" -> "Overworld";
			case "minecraft:the_nether" -> "Nether";
			case "minecraft:the_end" -> "The End";
			default -> {
				String path = id.substring(id.indexOf(':') + 1).replace('_', ' ');
				yield path.isEmpty() ? id : Character.toUpperCase(path.charAt(0)) + path.substring(1);
			}
		};
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double dx, double dy) {
		if (dy == 0) return false;
		float next = Math.clamp(dy > 0 ? scale * 2 : scale / 2, MIN_SCALE, MAX_SCALE);
		if (next == scale) return true;
		double midY = TOP + (height - TOP - BOTTOM) / 2.0;
		// keep the block under the mouse where it is
		double wx = cx + (mouseX - width / 2.0) / scale, wz = cz + (mouseY - midY) / scale;
		cx = wx - (mouseX - width / 2.0) / next;
		cz = wz - (mouseY - midY) / next;
		scale = next;
		follow = false;
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (super.mouseDragged(event, dx, dy)) return true;
		if (event.button() != 0 || event.y() < TOP) return false;
		cx -= dx / scale;
		cz -= dy / scale;
		follow = false;
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (MapClient.OPEN.matches(event)) {
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
