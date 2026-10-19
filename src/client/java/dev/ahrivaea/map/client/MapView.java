package dev.ahrivaea.map.client;

import dev.ahrivaea.map.Markers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.PlayerSkin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Draws the map into a box: the explored tiles (north up), the creatures as dots in their name-tag colour (white
 * rim: yours; gold rim: someone else's; dark rim: wild), other players as their faces and you as an arrow.
 */
public final class MapView {
	private MapView() {}

	public static final int MINIMAP_SIZE = 100;
	public static final int BACKGROUND = 0xFF15161C;

	/** What's under the mouse after drawing (for the full map's tooltip). */
	public static final class Hover {
		public final List<Component> lines = new ArrayList<>();
	}

	/**
	 * Draws the map centred on block (cx, cz) at {@code scale} screen pixels per block. {@code big} is the full map:
	 * bigger markers, player names, and players off the edge aren't pinned to it.
	 */
	public static void draw(GuiGraphicsExtractor g, int x0, int y0, int w, int h, double cx, double cz, float scale, boolean big,
							int mouseX, int mouseY, Hover hover) {
		Minecraft mc = Minecraft.getInstance();
		g.enableScissor(x0, y0, x0 + w, y0 + h);
		g.fill(x0, y0, x0 + w, y0 + h, BACKGROUND);
		float midX = x0 + w / 2f, midY = y0 + h / 2f;
		// where block 0,0 is on screen, on a whole pixel (so tiles meet without a seam)
		float originX = (float) Math.floor(midX - cx * scale), originY = (float) Math.floor(midY - cz * scale);
		double bx0 = cx - w / 2.0 / scale, bx1 = cx + w / 2.0 / scale, bz0 = cz - h / 2.0 / scale, bz1 = cz + h / 2.0 / scale;
		int s = MapTiles.SIZE;
		for (int tx = Math.floorDiv((int) Math.floor(bx0), s); tx <= Math.floorDiv((int) Math.floor(bx1), s); tx++)
			for (int tz = Math.floorDiv((int) Math.floor(bz0), s); tz <= Math.floorDiv((int) Math.floor(bz1), s); tz++) {
				MapTiles.Tile t = MapTiles.tile(tx, tz, false);
				if (t == null) continue;
				MapTiles.ready(t);
				g.pose().pushMatrix();
				g.pose().translate(originX + tx * s * scale, originY + tz * s * scale);
				g.pose().scale(scale, scale);
				g.blit(RenderPipelines.GUI_TEXTURED, t.id, 0, 0, 0f, 0f, s, s, s, s);
				g.pose().popMatrix();
			}

		String dim = mc.level.dimension().identifier().toString();
		Markers.Payload m = MapClient.markersFresh() ? MapClient.markers : new Markers.Payload(List.of(), List.of());
		// creatures
		int dot = big ? 5 : 3;
		for (Markers.CreatureMark c : m.creatures()) {
			int sx = Math.round(midX + (float) ((c.x() + 0.5 - cx) * scale)), sy = Math.round(midY + (float) ((c.z() + 0.5 - cz) * scale));
			if (sx < x0 || sx >= x0 + w || sy < y0 || sy >= y0 + h) continue;
			int rim = c.kind() == Markers.YOURS ? 0xFFFFFFFF : c.kind() == Markers.TAMED ? 0xFFFFD24A : 0xFF000000;
			int lo = dot / 2;
			g.fill(sx - lo - 1, sy - lo - 1, sx - lo + dot + 1, sy - lo + dot + 1, rim);
			g.fill(sx - lo, sy - lo, sx - lo + dot, sy - lo + dot, 0xFF000000 | c.color());
			if (hover != null && Math.abs(mouseX - sx) <= lo + 1 && Math.abs(mouseY - sy) <= lo + 1) {
				hover.lines.add(Component.literal(c.name()).withColor(c.color()));
				hover.lines.add(Component.literal(c.kind() == Markers.YOURS ? "Yours" : c.kind() == Markers.TAMED ? "Someone else's" : "Wild")
						.withColor(0xAAAAAA));
			}
		}
		// other players
		int face = big ? 10 : 8;
		UUID me = mc.player.getUUID();
		for (Markers.PlayerMark p : m.players()) {
			if (p.id().equals(me) || !p.dimension().equals(dim)) continue;
			float fx = midX + (float) ((p.x() + 0.5 - cx) * scale), fy = midY + (float) ((p.z() + 0.5 - cz) * scale);
			boolean off = fx < x0 || fx >= x0 + w || fy < y0 || fy >= y0 + h;
			if (off && big) continue;
			// on the minimap, players further away are pinned to its edge
			int sx = Math.round(Math.clamp(fx, x0 + face / 2f + 1, x0 + w - face / 2f - 1)), sy = Math.round(Math.clamp(fy, y0 + face / 2f + 1, y0 + h - face / 2f - 1));
			g.fill(sx - face / 2 - 1, sy - face / 2 - 1, sx - face / 2 + face + 1, sy - face / 2 + face + 1, off ? 0xFF888888 : 0xFFFFFFFF);
			PlayerFaceExtractor.extractRenderState(g, skin(mc, p.id()), sx - face / 2, sy - face / 2, face);
			if (big) g.centeredText(mc.font, p.name(), sx, sy + face / 2 + 2, 0xFFFFFFFF);
			if (hover != null && Math.abs(mouseX - sx) <= face / 2 + 1 && Math.abs(mouseY - sy) <= face / 2 + 1) {
				hover.lines.add(Component.literal(p.name()));
				hover.lines.add(Component.literal(p.x() + ", " + p.y() + ", " + p.z()).withColor(0xAAAAAA));
			}
		}
		// you
		float px = midX + (float) ((mc.player.getX() - cx) * scale), py = midY + (float) ((mc.player.getZ() - cz) * scale);
		if (px >= x0 && px < x0 + w && py >= y0 && py < y0 + h) arrow(g, px, py, mc.player.getYRot(), big ? 1.4f : 1f);
		g.disableScissor();
	}

	private static PlayerSkin skin(Minecraft mc, UUID id) {
		PlayerInfo info = mc.getConnection() == null ? null : mc.getConnection().getPlayerInfo(id);
		return info != null ? info.getSkin() : DefaultPlayerSkin.get(id);
	}

	/** An arrow pointing the way you face (yaw 180 is north, up the map). */
	static void arrow(GuiGraphicsExtractor g, float x, float y, float yaw, float size) {
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().rotate((float) Math.toRadians(yaw - 180));
		g.pose().scale(size, size);
		for (int i = 0; i < 8; i++) { // dark outline, then the arrow
			int half = i / 2 + 1;
			g.fill(-half, -5 + i, half + 1, -3 + i, 0xFF000000);
		}
		for (int i = 0; i < 6; i++) {
			int half = i / 2;
			g.fill(-half, -4 + i, half + 1, -3 + i, i < 2 ? 0xFFFFFFFF : 0xFFFF4040);
		}
		g.pose().popMatrix();
	}

	/** The minimap: top right (below any effect icons), one pixel a block, with your coordinates under it. */
	public static void minimap(GuiGraphicsExtractor g, Minecraft mc) {
		boolean good = false, bad = false;
		for (MobEffectInstance e : mc.player.getActiveEffects()) {
			if (!e.showIcon()) continue;
			if (e.getEffect().value().isBeneficial()) good = true;
			else bad = true;
		}
		int size = MINIMAP_SIZE;
		int x = g.guiWidth() - size - 4, y = 4 + (bad ? 52 : good ? 26 : 0);
		g.fill(x - 1, y - 1, x + size + 1, y + size + 1, 0xFF5A5A66);
		draw(g, x, y, size, size, mc.player.getX(), mc.player.getZ(), 1f, false, 0, 0, null);
		g.centeredText(mc.font, "N", x + size / 2, y + 2, 0xFFFFFFFF);
		String where = mc.player.getBlockX() + ", " + mc.player.getBlockY() + ", " + mc.player.getBlockZ();
		g.centeredText(mc.font, where, x + size / 2, y + size + 3, 0xFFFFFFFF);
	}
}
