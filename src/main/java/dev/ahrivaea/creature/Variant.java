package dev.ahrivaea.creature;

import com.mojang.serialization.Codec;
import dev.ahrivaea.Ahrivaea;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;

/**
 * Very rarely a wild creature is Enraged or Mutated: the same creature in different colours (its model's
 * {@code _enraged} or {@code _mutated} texture), with the word in front of its name. It stays that way when you
 * catch or recruit it. The chance is 1 in {@code ahrivaea:variant_odds} (10,000 by default; 0 turns them off),
 * Enraged and Mutated equally likely.
 */
public enum Variant implements StringRepresentable {
	NONE(""), ENRAGED("Enraged"), MUTATED("Mutated");

	public static final Codec<Variant> CODEC = StringRepresentable.fromEnum(Variant::values);

	public static final GameRule<Integer> ODDS = GameRuleBuilder.forInteger(10000).minValue(0).category(GameRuleCategory.SPAWNING)
			.buildAndRegister(Ahrivaea.id("variant_odds"));

	private final String word;

	Variant(String word) {
		this.word = word;
	}

	@Override
	public String getSerializedName() {
		return name().toLowerCase(java.util.Locale.ROOT);
	}

	/** "Enraged ", "Mutated " or "", for the front of its name. */
	public String prefix() {
		return word.isEmpty() ? "" : word + " ";
	}

	public static Variant byName(String name) {
		for (Variant v : values()) if (v.getSerializedName().equals(name)) return v;
		return NONE;
	}

	/** The variant for a new wild creature: almost always none. */
	public static Variant roll(ServerLevel level, RandomSource random) {
		int odds = level.getGameRules().get(ODDS);
		if (odds <= 0 || random.nextInt(odds) != 0) return NONE;
		return random.nextBoolean() ? ENRAGED : MUTATED;
	}

	public static void init() {
		// touching this class registers the game rule
	}
}
