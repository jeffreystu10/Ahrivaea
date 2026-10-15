package dev.ahrivaea.base;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.ahrivaea.party.CaughtCreature;
import net.minecraft.core.UUIDUtil;

import java.util.UUID;

/** A creature working at a base: what it is (to bring it back if its body goes missing) and its body in the world. */
public record Worker(CaughtCreature creature, UUID entity) {
	public static final Codec<Worker> CODEC = RecordCodecBuilder.create(i -> i.group(
			CaughtCreature.CODEC.fieldOf("creature").forGetter(Worker::creature),
			UUIDUtil.CODEC.fieldOf("entity").forGetter(Worker::entity)
	).apply(i, Worker::new));
}
