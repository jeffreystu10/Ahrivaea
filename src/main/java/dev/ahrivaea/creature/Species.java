package dev.ahrivaea.creature;

import dev.ahrivaea.Ahrivaea;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A kind of creature. For now the two placeholder species are defined here in code;
 * a later milestone loads species from data pack JSON so new ones can be added without code.
 */
public record Species(String id, String displayName, float baseHealth, float speed, float catchRate) {
	private static final Map<String, Species> ALL = new LinkedHashMap<>();

	public static final Species EMBERKIT = register(new Species("emberkit", "Emberkit", 18f, 0.28f, 0.45f));
	public static final Species PUDDLEPUP = register(new Species("puddlepup", "Puddlepup", 22f, 0.24f, 0.45f));

	private static Species register(Species s) {
		ALL.put(s.id, s);
		return s;
	}

	public static Species get(String id) {
		return ALL.getOrDefault(id, EMBERKIT);
	}

	public static List<Species> all() {
		return List.copyOf(ALL.values());
	}

	public Identifier texture() {
		return Ahrivaea.id("textures/entity/creature/" + id + ".png");
	}

	/** Max health at a given level. */
	public float healthAt(int level) {
		return baseHealth * (1f + level * 0.08f);
	}
}
