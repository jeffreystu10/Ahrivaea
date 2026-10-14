package dev.ahrivaea.challenge;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Where a challenge was placed and how far it has got. Donor keys are player UUIDs as strings.
 * Saved with the overworld; mutate, then call {@link Challenges#save}.
 */
public final class ChallengeState {
	public static final Codec<ChallengeState> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("dimension").forGetter(s -> s.dimension),
			BlockPos.CODEC.fieldOf("origin").forGetter(s -> s.origin),
			Codec.INT.fieldOf("total").forGetter(s -> s.total),
			Codec.INT.fieldOf("stage").forGetter(s -> s.stage),
			Codec.BOOL.fieldOf("complete").forGetter(s -> s.complete),
			Codec.unboundedMap(Codec.STRING, Codec.INT).fieldOf("donations").forGetter(s -> s.donations),
			Codec.unboundedMap(Codec.STRING, Codec.STRING).fieldOf("names").forGetter(s -> s.names),
			Codec.unboundedMap(Codec.STRING, ChallengeDef.RewardItem.CODEC.listOf()).fieldOf("pending_rewards").forGetter(s -> s.pendingRewards)
	).apply(i, ChallengeState::new));

	public final String dimension;
	public final BlockPos origin;
	public int total;
	public int stage;
	public boolean complete;
	public final Map<String, Integer> donations;
	public final Map<String, String> names;
	public final Map<String, List<ChallengeDef.RewardItem>> pendingRewards;

	public ChallengeState(String dimension, BlockPos origin, int total, int stage, boolean complete,
			Map<String, Integer> donations, Map<String, String> names, Map<String, List<ChallengeDef.RewardItem>> pendingRewards) {
		this.dimension = dimension;
		this.origin = origin;
		this.total = total;
		this.stage = stage;
		this.complete = complete;
		this.donations = new HashMap<>(donations);
		this.names = new HashMap<>(names);
		this.pendingRewards = new HashMap<>();
		pendingRewards.forEach((k, v) -> this.pendingRewards.put(k, new ArrayList<>(v)));
	}

	public static ChallengeState fresh(String dimension, BlockPos origin) {
		return new ChallengeState(dimension, origin, 0, 0, false, Map.of(), Map.of(), Map.of());
	}

	/** Donor keys, most generous first (ties broken by name). */
	public List<String> ranking() {
		List<String> keys = new ArrayList<>(donations.keySet());
		keys.sort(Comparator.<String>comparingInt(k -> -donations.get(k)).thenComparing(k -> names.getOrDefault(k, k)));
		return keys;
	}

	public int rankOf(String key) {
		return ranking().indexOf(key) + 1;
	}
}
