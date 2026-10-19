package dev.ahrivaea.map.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.ahrivaea.map.MapMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.tags.FluidTags;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * The explored map: every column the player's game has seen, coloured like a vanilla map (shaded by slope, water by
 * depth), kept in 256x256-block tiles. Each tile is an image (transparent where nothing's been seen yet), drawn as a
 * texture and saved as a PNG under {@code ahrivaea_map/<world>/<dimension>/} in the game folder, so the map is still
 * there next time. Single-player worlds and servers each get their own folder.
 */
public final class MapTiles {
	private MapTiles() {}

	public static final int SIZE = 256;
	/** Chunks scanned per tick. */
	private static final int PER_TICK = 24;

	public static final class Tile {
		public final int tx, tz;
		final NativeImage image;
		final DynamicTexture texture;
		public final Identifier id;
		boolean upload = true, save;

		Tile(int tx, int tz, NativeImage image, Identifier id) {
			this.tx = tx;
			this.tz = tz;
			this.image = image;
			this.id = id;
			this.texture = new DynamicTexture(() -> "ahrivaea_map " + id, image);
			Minecraft.getInstance().getTextureManager().register(id, texture);
		}
	}

	private static String folder; // world/dimension this is for
	private static final Map<Long, Tile> TILES = new HashMap<>();
	/** Tiles known to have nothing on disk (so we don't keep looking). */
	private static final Set<Long> NONE = new HashSet<>();
	private static final LinkedHashSet<Long> QUEUE = new LinkedHashSet<>();
	private static int nextTexture;

	/** The folder for the world and dimension the player is in, or null when not in one. */
	static String folderNow(Minecraft mc) {
		if (mc.level == null) return null;
		String world;
		if (mc.getSingleplayerServer() != null) {
			Path root = mc.getSingleplayerServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
			world = "singleplayer_" + clean(String.valueOf(root.getFileName()));
		} else world = "server_" + clean(mc.getCurrentServer() == null ? "unknown" : mc.getCurrentServer().ip);
		Identifier dim = mc.level.dimension().identifier();
		return world + "/" + clean(dim.getNamespace()) + "_" + clean(dim.getPath());
	}

	private static String clean(String s) {
		return s.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
	}

	public static Path dir() {
		return Minecraft.getInstance().gameDirectory.toPath().resolve("ahrivaea_map").resolve(folder);
	}

	/** Called every tick: keep up with the world/dimension and scan some of the queued chunks. */
	static void tick(Minecraft mc, boolean on) {
		String now = on ? folderNow(mc) : null;
		if (!java.util.Objects.equals(now, folder)) {
			closeAll();
			folder = now;
			// everything already loaded in the new world or dimension
			if (now != null) queueAround(mc, mc.options.getEffectiveRenderDistance() + 1);
		}
		if (folder == null) return;
		ClientLevel level = mc.level;
		int n = 0;
		for (var it = QUEUE.iterator(); it.hasNext() && n < PER_TICK; ) {
			long c = it.next();
			it.remove();
			int cx = ChunkPos.getX(c), cz = ChunkPos.getZ(c);
			if (!level.hasChunk(cx, cz)) continue;
			scan(level, level.getChunk(cx, cz));
			n++;
		}
	}

	static void queue(int cx, int cz) {
		QUEUE.add(ChunkPos.pack(cx, cz));
	}

	/** Re-checks the chunks right around the player (so building and digging show up). */
	static void queueAround(Minecraft mc, int radius) {
		if (mc.player == null) return;
		int px = mc.player.getBlockX() >> 4, pz = mc.player.getBlockZ() >> 4;
		for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) queue(px + dx, pz + dz);
	}

	/** Colours a chunk's 16x16 columns into its tile. */
	public static void scan(ClientLevel level, LevelChunk chunk) {
		if (folder == null) return;
		int x0 = chunk.getPos().getMinBlockX(), z0 = chunk.getPos().getMinBlockZ();
		Tile t = tile(Math.floorDiv(x0, SIZE), Math.floorDiv(z0, SIZE), true);
		boolean ceiling = level.dimensionType().hasCeiling();
		int[] heights = new int[17]; // the row to the north first, then each row in turn
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		boolean north = level.hasChunk(chunk.getPos().x(), chunk.getPos().z() - 1);
		LevelChunk northChunk = north ? level.getChunk(chunk.getPos().x(), chunk.getPos().z() - 1) : null;
		for (int lx = 0; lx < 16; lx++) heights[lx] = northChunk == null ? Integer.MIN_VALUE : top(level, northChunk, lx, 15, ceiling, pos);
		for (int lz = 0; lz < 16; lz++) {
			for (int lx = 0; lx < 16; lx++) {
				int y = top(level, chunk, lx, lz, ceiling, pos);
				int above = heights[lx];
				heights[lx] = y;
				int color;
				if (y == Integer.MIN_VALUE) color = 0xFF101010;
				else {
					pos.set(x0 + lx, y, z0 + lz);
					BlockState s = chunk.getBlockState(pos);
					FluidState f = s.getFluidState();
					if (f.is(FluidTags.WATER)) {
						int depth = 0;
						while (depth < 12 && chunk.getBlockState(pos.move(0, -1, 0)).getFluidState().is(FluidTags.WATER)) depth++;
						double d = depth * 0.1 + ((lx + lz) & 1) * 0.2;
						color = MapColor.WATER.calculateARGBColor(d < 0.5 ? MapColor.Brightness.HIGH : d > 0.9 ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL);
					} else {
						MapColor mc = s.getMapColor(level, pos);
						MapColor.Brightness b = above == Integer.MIN_VALUE || above == y ? MapColor.Brightness.NORMAL
								: y > above ? MapColor.Brightness.HIGH : MapColor.Brightness.LOW;
						color = mc.calculateARGBColor(b);
					}
				}
				int px = Math.floorMod(x0 + lx, SIZE), pz = Math.floorMod(z0 + lz, SIZE);
				if (t.image.getPixel(px, pz) != color) {
					t.image.setPixel(px, pz, color);
					t.upload = true;
					t.save = true;
				}
			}
		}
	}

	/**
	 * The y of the block a map shows for a column (the highest with a map colour), or MIN_VALUE for none. Under a
	 * ceiling (the Nether) it's the highest one below the roof.
	 */
	private static int top(ClientLevel level, LevelChunk chunk, int lx, int lz, boolean ceiling, BlockPos.MutableBlockPos pos) {
		int x = chunk.getPos().getMinBlockX() + lx, z = chunk.getPos().getMinBlockZ() + lz;
		int min = level.getMinY();
		int y;
		if (!ceiling) y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, lx, lz);
		else {
			y = min + level.dimensionType().logicalHeight() - 1;
			while (y > min && !chunk.getBlockState(pos.set(x, y, z)).isAir()) y--; // through the roof
		}
		for (; y >= min; y--) {
			BlockState s = chunk.getBlockState(pos.set(x, y, z));
			if (!s.getFluidState().isEmpty() || s.getMapColor(level, pos) != MapColor.NONE) return y;
		}
		return Integer.MIN_VALUE;
	}

	/** The tile at tile coordinates, loaded from disk if it's there; null if there's none and {@code create} is false. */
	public static Tile tile(int tx, int tz, boolean create) {
		if (folder == null) return null;
		long key = ChunkPos.pack(tx, tz);
		Tile t = TILES.get(key);
		if (t != null) return t;
		NativeImage img = null;
		if (!NONE.contains(key)) {
			Path f = dir().resolve("tile." + tx + "." + tz + ".png");
			if (Files.exists(f)) {
				try (InputStream in = Files.newInputStream(f)) {
					img = NativeImage.read(in);
					if (img.getWidth() != SIZE || img.getHeight() != SIZE) {
						img.close();
						img = null;
					}
				} catch (IOException e) {
					MapMod.LOGGER.warn("Couldn't read map tile {}: {}", f, e.toString());
				}
			}
			if (img == null) NONE.add(key);
		}
		if (img == null) {
			if (!create) return null;
			img = new NativeImage(SIZE, SIZE, true);
		}
		t = new Tile(tx, tz, img, Identifier.fromNamespaceAndPath("ahrivaea_map", "tile/" + (nextTexture++)));
		TILES.put(key, t);
		NONE.remove(key);
		return t;
	}

	/** Uploads a tile's latest pixels before it's drawn. */
	static void ready(Tile t) {
		if (t.upload) {
			t.texture.upload();
			t.upload = false;
		}
	}

	/** Writes changed tiles to disk. */
	public static void saveAll() {
		if (folder == null) return;
		try {
			Files.createDirectories(dir());
		} catch (IOException e) {
			MapMod.LOGGER.warn("Couldn't make the map folder: {}", e.toString());
			return;
		}
		for (Tile t : TILES.values()) {
			if (!t.save) continue;
			try {
				t.image.writeToFile(dir().resolve("tile." + t.tx + "." + t.tz + ".png"));
				t.save = false;
			} catch (IOException e) {
				MapMod.LOGGER.warn("Couldn't save map tile: {}", e.toString());
			}
		}
	}

	static void closeAll() {
		saveAll();
		for (Tile t : new ArrayList<>(TILES.values())) Minecraft.getInstance().getTextureManager().release(t.id);
		TILES.clear();
		NONE.clear();
	}

	/** How many columns have been seen in the tiles loaded now (for the tests). */
	public static int explored() {
		int n = 0;
		for (Tile t : TILES.values())
			for (int x = 0; x < SIZE; x++) for (int z = 0; z < SIZE; z++) if ((t.image.getPixel(x, z) >>> 24) != 0) n++;
		return n;
	}
}
