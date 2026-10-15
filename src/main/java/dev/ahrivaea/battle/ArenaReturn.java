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
			p.teleportTo(overworld, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, Set.of(), p.getYRot(), p.getXRot(), true);
			return;
		}
		p.teleportTo(to, r.x(), r.y(), r.z(), Set.of(), r.yaw(), r.pitch(), true);
	}
}
