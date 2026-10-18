package dev.ahrivaea.base;

import com.mojang.serialization.Codec;
import dev.ahrivaea.Ahrivaea;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.List;

/**
 * Blocks players placed under and around their bases (saved with the chunk), so quarry miners never dig up
 * anything someone built. Only blocks placed inside a base's chunks are noted, from the time this was added.
 */
public final class PlacedBlocks {
	private PlacedBlocks() {}

	public static final AttachmentType<List<Long>> PLACED = AttachmentRegistry.create(Ahrivaea.id("placed_blocks"),
			b -> b.persistent(Codec.LONG.listOf()).initializer(List::of));

	public static boolean isPlaced(ServerLevel level, BlockPos pos) {
		LevelChunk c = level.getChunkAt(pos);
		List<Long> list = c.getAttached(PLACED);
		return list != null && list.contains(pos.asLong());
	}

	/** Called whenever a player places a block: noted if it's in a base's chunks. */
	public static void placed(ServerLevel level, BlockPos pos) {
		if (!Bases.inAnyBaseColumn(level, pos)) return;
		LevelChunk c = level.getChunkAt(pos);
		List<Long> list = c.getAttachedOrCreate(PLACED);
		if (list.contains(pos.asLong())) return;
		List<Long> copy = new ArrayList<>(list);
		copy.add(pos.asLong());
		c.setAttached(PLACED, copy);
		c.markUnsaved();
	}

	/** A block that's gone no longer counts (so a quarry can dig whatever later fills that spot). */
	public static void removed(ServerLevel level, BlockPos pos) {
		LevelChunk c = level.getChunkAt(pos);
		List<Long> list = c.getAttached(PLACED);
		if (list == null || !list.contains(pos.asLong())) return;
		List<Long> copy = new ArrayList<>(list);
		copy.remove((Long) pos.asLong());
		c.setAttached(PLACED, copy);
		c.markUnsaved();
	}
}
