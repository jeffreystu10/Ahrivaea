package dev.ahrivaea.villager;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * A villager's training: its profession ("" until one is picked), how many of the profession's ranks it has earned
 * (0 while training as a Novice, up to the number of ranks: Master), and the player's count for the current rank's
 * deed when that rank's training began (deeds count what's done from then on).
 */
public record VillagerJob(String profession, int rank, int baseline) {
	public static final VillagerJob NONE = new VillagerJob("", 0, 0);

	public static final Codec<VillagerJob> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.optionalFieldOf("profession", "").forGetter(VillagerJob::profession),
			Codec.INT.optionalFieldOf("rank", 0).forGetter(VillagerJob::rank),
			Codec.INT.optionalFieldOf("baseline", 0).forGetter(VillagerJob::baseline)
	).apply(i, VillagerJob::new));

	public static final StreamCodec<ByteBuf, VillagerJob> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, VillagerJob::profession, ByteBufCodecs.VAR_INT, VillagerJob::rank, ByteBufCodecs.VAR_INT, VillagerJob::baseline, VillagerJob::new);

	public boolean trained() {
		return !profession.isEmpty();
	}
}
