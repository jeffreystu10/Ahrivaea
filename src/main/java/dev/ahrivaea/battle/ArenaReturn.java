package dev.ahrivaea.battle;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.ahrivaea.Ahrivaea;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

/** Where a player was when a battle took them to the arena. Saved with the player, so a crash or log-out mid-battle still brings them back. */
public record ArenaReturn(String dimension, double x, double y, double z, float yaw, float pitch) {
	public static final Codec<ArenaReturn> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("dimension").forGetter(ArenaReturn::dimension),
			Codec.DOUBLE.fieldOf("x").forGetter(ArenaReturn::x),
			Codec.DOUBLE.fieldOf("y").forGetter(ArenaReturn::y),
			Codec.DOUBLE.fieldOf("z").forGetter(ArenaReturn::z),
			Codec.FLOAT.fieldOf("yaw").forGetter(ArenaReturn::yaw),
			Codec.FLOAT.fieldOf("pitch").forGetter(ArenaReturn::pitch)
	).apply(i, ArenaReturn::new));

	public static final AttachmentType<ArenaReturn> ATTACHMENT = AttachmentRegistry.create(Ahrivaea.id("arena_return"), b -> b.persistent(CODEC));

	public static ArenaReturn of(ServerPlayer p) {
		return new ArenaReturn(p.level().dimension().identifier().toString(), p.getX(), p.getY(), p.getZ(), p.getYRot(), p.getXRot());
	}

	/** Sends the player back where they came from (or to the world spawn if that's gone), and forgets the spot. */
	public static void sendBack(ServerPlayer p) {
		ArenaReturn r = p.getAttached(ATTACHMENT);
		p.removeAttached(ATTACHMENT);
		var server = p.level().getServer();
		ServerLevel to = null;
		if (r != null) to = server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(r.dimension())));
		if (r == null || to == null) {
			if (!p.level().dimension().equals(Arena.DIMENSION)) return;
			ServerLevel overworld = server.overworld();
			var spawn = overworld.getRespawnData().pos();
			Vec3 at = safeSpot(overworld, Vec3.atBottomCenterOf(spawn));
			if (at == null) at = Vec3.atBottomCenterOf(overworld.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn));
			p.teleportTo(overworld, at.x, at.y, at.z, Set.of(), p.getYRot(), p.getXRot(), true);
			p.resetFallDistance();
			return;
		}
		Vec3 at = safeSpot(to, new Vec3(r.x(), r.y(), r.z()));
		if (at == null) {
			// nowhere safe near the old spot (e.g. it was flooded with lava): the world spawn instead
			ServerLevel overworld = server.overworld();
			to = overworld;
			var spawn = overworld.getRespawnData().pos();
			at = safeSpot(overworld, Vec3.atBottomCenterOf(spawn));
			if (at == null) at = Vec3.atBottomCenterOf(overworld.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn));
		}
		p.teleportTo(to, at.x, at.y, at.z, Set.of(), r.yaw(), r.pitch(), true);
		p.resetFallDistance();
	}

	/**
	 * The nearest place to {@code want} where someone can stand safely: solid, harmless ground, and room for their
	 * feet and head (no blocks, lava, fire, powder snow ...). The world may have changed while they were away.
	 * Looks up to 8 blocks sideways and 12 up or down, then on top of the ground right there; null if nothing works.
	 */
	public static Vec3 safeSpot(ServerLevel level, Vec3 want) {
		BlockPos start = BlockPos.containing(want);
		level.getChunk(start.getX() >> 4, start.getZ() >> 4); // make sure it's loaded
		if (safeExact(level, want)) return want;
		for (int r = 0; r <= 8; r++) {
			for (int dy : new int[] {0, 1, -1, 2, -2, 3, -3, 4, -4, 6, -6, 8, -8, 12, -12}) {
				for (int dx = -r; dx <= r; dx++) {
					for (int dz = -r; dz <= r; dz++) {
						if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
						BlockPos q = start.offset(dx, dy, dz);
						if (r > 0 && (dx != 0 || dz != 0)) level.getChunk(q.getX() >> 4, q.getZ() >> 4);
						if (safe(level, q)) return Vec3.atBottomCenterOf(q);
					}
				}
			}
		}
		BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, start);
		return safe(level, top) ? Vec3.atBottomCenterOf(top) : null;
	}

	/** Whether the exact spot still works (they may have been standing on a slab, path or farmland). */
	private static boolean safeExact(ServerLevel level, Vec3 at) {
		AABB body = new AABB(at.x - 0.3, at.y, at.z - 0.3, at.x + 0.3, at.y + 1.8, at.z + 0.3);
		if (!level.noCollision(body)) return false;
		BlockPos ground = BlockPos.containing(at.x, at.y - 0.05, at.z);
		BlockState g = level.getBlockState(ground);
		if (g.getCollisionShape(level, ground).isEmpty() || harmfulGround(g)) return false;
		BlockPos feet = BlockPos.containing(at);
		return harmless(level.getBlockState(feet)) && harmless(level.getBlockState(feet.above())) && harmless(level.getBlockState(feet.above(2)));
	}

	private static boolean harmfulGround(BlockState s) {
		return s.is(Blocks.MAGMA_BLOCK) || s.is(Blocks.CACTUS) || s.is(BlockTags.CAMPFIRES) || s.is(Blocks.POINTED_DRIPSTONE) || !s.getFluidState().isEmpty();
	}

	private static boolean harmless(BlockState s) {
		return !s.getFluidState().is(net.minecraft.tags.FluidTags.LAVA) && !s.is(BlockTags.FIRE) && !s.is(Blocks.POWDER_SNOW) && !s.is(Blocks.SWEET_BERRY_BUSH)
				&& !s.is(Blocks.COBWEB) && !s.is(Blocks.WITHER_ROSE) && !s.is(Blocks.NETHER_PORTAL) && !s.is(Blocks.END_PORTAL);
	}

	/** Whether someone can stand with their feet in {@code feet}. */
	public static boolean safe(ServerLevel level, BlockPos feet) {
		if (feet.getY() <= level.getMinY() || feet.getY() + 1 >= level.getMaxY()) return false;
		BlockState below = level.getBlockState(feet.below());
		if (!below.isFaceSturdy(level, feet.below(), Direction.UP) || harmfulGround(below)) return false;
		return open(level, feet) && open(level, feet.above());
	}

	private static boolean open(ServerLevel level, BlockPos p) {
		BlockState s = level.getBlockState(p);
		return s.getCollisionShape(level, p).isEmpty() && s.getFluidState().isEmpty() && harmless(s);
	}
}
