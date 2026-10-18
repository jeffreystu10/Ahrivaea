package dev.ahrivaea.creature;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/**
 * Kinds of work a creature does at a base (the creature list's ~190 job descriptions sorted into these). All work
 * except Riding, which comes with rideable creatures.
 */
public enum Job implements StringRepresentable {
	FARMING("Farming", "harvests ripe crops in the base and replants them", true),
	GROWTH("Growth", "makes crops, saplings and plants in the base grow faster", true),
	LOGGING("Logging", "fells grown trees in the base (never ones you built) and replants saplings", true),
	MINING("Mining", "works a Mining Post: stone and ores, depending on where the base is", true),
	SMELTING("Smelting", "keeps furnaces, smokers and blast furnaces in the base burning with no fuel", true),
	TRANSPORT("Transport", "carries loose items and furnace output in the base to its chests", true),
	RANCHING("Ranching", "produces its own drops now and then", true),
	FISHING("Fishing", "catches fish and treasure from water in the base", true),
	DEFENSE("Defense", "fights off raids on the base", true),
	BREWING("Brewing", "runs brewing stands with no blaze powder", true),
	ENCHANTING("Enchanting", "studies at an enchanting table: bottles o' enchanting, now and then an enchanted book", true),
	REDSTONE("Redstone", "powers a Creature Dynamo (redstone signal 15), turning up a little redstone", true),
	CRAFTING("Crafting", "works crafters in the base, crafting whatever they're set up for", true),
	ICE("Ice", "makes ice, snowballs and now and then packed or blue ice from water in the base", true),
	LIGHTING("Lighting", "puts torches from the base's chests on dark spots", true),
	RIDING("Riding", "coming with rideable creatures", false);

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
