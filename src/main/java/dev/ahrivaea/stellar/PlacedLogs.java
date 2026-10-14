package dev.ahrivaea.stellar;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.List;

/**
 * Remembers every log a player places (saved with its chunk), so the Stellar Axe never fells anything
 * someone built.
 */
public final class PlacedLogs {
	private PlacedLogs() {}

	public static final AttachmentType<List<Long>> PLACED = AttachmentRegistry.create(StellarMod.id("placed_logs"),
			b -> b.persistent(Codec.LONG.listOf()).initializer(List::of));

	static void init() {
		// touching the class registers the attachment
	}

	private static LevelChunk chunk(Level level, BlockPos pos) {
		return level.getChunkAt(pos);
	}

	public static boolean isPlaced(Level level, BlockPos pos) {
		return chunk(level, pos).getAttachedOrCreate(PLACED).contains(pos.asLong());
	}

	public static void mark(Level level, BlockPos pos) {
		LevelChunk c = chunk(level, pos);
		List<Long> list = c.getAttachedOrCreate(PLACED);
		if (list.contains(pos.asLong())) return;
		List<Long> copy = new ArrayList<>(list);
		copy.add(pos.asLong());
		c.setAttached(PLACED, copy);
		c.markUnsaved();
	}

	public static void unmark(Level level, BlockPos pos) {
		LevelChunk c = chunk(level, pos);
		List<Long> list = c.getAttachedOrCreate(PLACED);
		if (!list.contains(pos.asLong())) return;
		List<Long> copy = new ArrayList<>(list);
		copy.remove((Long) pos.asLong());
		c.setAttached(PLACED, copy);
		c.markUnsaved();
	}
}
