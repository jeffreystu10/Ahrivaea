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
			Codec.STRING.optionalFieldOf("challenge", "").forGetter(s -> s.challenge),
			Codec.STRING.optionalFieldOf("palette", "none").forGetter(s -> s.palette),
			Codec.STRING.fieldOf("dimension").forGetter(s -> s.dimension),
			BlockPos.CODEC.fieldOf("origin").forGetter(s -> s.origin),
			Codec.INT.fieldOf("total").forGetter(s -> s.total),
			Codec.INT.fieldOf("stage").forGetter(s -> s.stage),
			Codec.BOOL.fieldOf("complete").forGetter(s -> s.complete),
			Codec.unboundedMap(Codec.STRING, Codec.INT).fieldOf("donations").forGetter(s -> s.donations),
			Codec.unboundedMap(Codec.STRING, Codec.STRING).fieldOf("names").forGetter(s -> s.names),
			Codec.unboundedMap(Codec.STRING, ChallengeDef.RewardItem.CODEC.listOf()).fieldOf("pending_rewards").forGetter(s -> s.pendingRewards),
			Codec.STRING.optionalFieldOf("owner", "").forGetter(s -> s.owner),
			Codec.INT.listOf().optionalFieldOf("portal_room", List.of()).forGetter(s -> s.portalRoom),
			BlockPos.CODEC.listOf().optionalFieldOf("gates", List.of()).forGetter(s -> s.gates)
	).apply(i, ChallengeState::new));

	/** Which challenge file this tower uses. */
	public String challenge;
	/** The biome palette chosen when it was placed, reused for every stage. */
	public final String palette;
	public final String dimension;
	public final BlockPos origin;
	public int total;
	public int stage;
	public boolean complete;
	public final Map<String, Integer> donations;
	public final Map<String, String> names;
	public final Map<String, List<ChallengeDef.RewardItem>> pendingRewards;
	/** The player (UUID) who controls the tower: its top donor when it was completed. Empty until then. */
	public String owner;
	/** The End portal room under the tower (minX, minY, minZ, maxX, maxY, maxZ), or empty if not found yet. */
	public List<Integer> portalRoom;
	/** The openings into the portal room that are sealed (reinforced deepslate) until the tower is complete. */
	public List<BlockPos> gates;

	public ChallengeState(String challenge, String palette, String dimension, BlockPos origin, int total, int stage, boolean complete,
			Map<String, Integer> donations, Map<String, String> names, Map<String, List<ChallengeDef.RewardItem>> pendingRewards, String owner,
			List<Integer> portalRoom, List<BlockPos> gates) {
		this.owner = owner;
		this.portalRoom = List.copyOf(portalRoom);
		this.gates = List.copyOf(gates);
		this.challenge = challenge;
		this.palette = palette;
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

	public static ChallengeState fresh(String challenge, String palette, String dimension, BlockPos origin) {
		return new ChallengeState(challenge, palette, dimension, origin, 0, 0, false, Map.of(), Map.of(), Map.of(), "", List.of(), List.of());
	}

	/** Donor keys, most generous first (ties broken by name). */
	public List<String> ranking() {
		List<String> keys = new ArrayList<>(donations.keySet());
		keys.sort(Comparator.<String>comparingInt(k -> -donations.get(k)).thenComparing(k -> names.getOrDefault(k, k)));
		return keys;
	}

	/** Who controls it (a UUID string), or "" if nobody yet; towers completed before owners existed go to their top donor. */
	public String controller() {
		if (!owner.isEmpty()) return owner;
		return complete && !donations.isEmpty() ? ranking().getFirst() : "";
	}

	public net.minecraft.world.level.levelgen.structure.BoundingBox portalRoomBox() {
		if (portalRoom.size() != 6) return null;
		return new net.minecraft.world.level.levelgen.structure.BoundingBox(portalRoom.get(0), portalRoom.get(1), portalRoom.get(2), portalRoom.get(3), portalRoom.get(4), portalRoom.get(5));
	}

	public int rankOf(String key) {
		return ranking().indexOf(key) + 1;
	}
}
