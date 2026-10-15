package dev.ahrivaea.creature;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/**
 * Kinds of work a creature does at a base (the creature list's ~190 job descriptions sorted into these). Phase 1 has
 * {@link #working() working} versions of the first eight; the rest are coming.
 */
public enum Job implements StringRepresentable {
	FARMING("Farming", "harvests ripe crops in the base and replants them", true),
	GROWTH("Growth", "makes crops, saplings and plants in the base grow faster", true),
	LOGGING("Logging", "fells grown trees in the base (never ones you built) and replants saplings", true),
	MINING("Mining", "works a Mining Post: stone and ores, depending on where the base is", true),
	SMELTING("Smelting", "keeps furnaces, smokers and blast furnaces in the base burning with no fuel, twice as fast", true),
	TRANSPORT("Transport", "carries loose items and furnace output in the base to its chests", true),
	RANCHING("Ranching", "produces its own drops now and then", true),
	FISHING("Fishing", "catches fish and treasure from water in the base", true),
	DEFENSE("Defense", "fights off raids on the base (raids come in phase 2)", false),
	BREWING("Brewing", "phase 2", false),
	ENCHANTING("Enchanting", "phase 2", false),
	REDSTONE("Redstone", "phase 2", false),
	CRAFTING("Crafting", "phase 2", false),
	ICE("Ice", "phase 2", false),
	LIGHTING("Lighting", "phase 2", false),
	RIDING("Riding", "phase 2", false);

	public static final Codec<Job> CODEC = StringRepresentable.fromEnum(Job::values);

	private final String displayName, description;
	private final boolean working;

	Job(String displayName, String description, boolean working) {
		this.displayName = displayName;
		this.description = description;
		this.working = working;
	}

	public String displayName() {
		return displayName;
	}

	public String description() {
		return description;
	}

	/** Whether creatures actually do this job yet. */
	public boolean working() {
		return working;
	}

	@Override
	public String getSerializedName() {
		return name().toLowerCase(Locale.ROOT);
	}
}
