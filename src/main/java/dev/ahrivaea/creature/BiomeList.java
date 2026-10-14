package dev.ahrivaea.creature;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;

import java.util.List;

/** Biome ids ("minecraft:plains") or biome tags ("#minecraft:is_forest"). */
public final class BiomeList {
	private BiomeList() {}

	public static boolean matches(List<String> entries, Holder<Biome> biome) {
		for (String b : entries) {
			if (b.startsWith("#")) {
				if (biome.is(TagKey.create(Registries.BIOME, Identifier.parse(b.substring(1))))) return true;
			} else if (biome.is(Identifier.parse(b))) {
				return true;
			}
		}
		return false;
	}
}
