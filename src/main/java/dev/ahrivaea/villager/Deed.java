package dev.ahrivaea.villager;

import com.google.gson.JsonObject;
import dev.ahrivaea.Ahrivaea;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.StatType;
import net.minecraft.stats.Stats;
import net.minecraft.tags.TagKey;

import java.util.ArrayList;
import java.util.List;

/**
 * What a villager needs you to do to earn a rank: a count of something you do (blocks mined, items crafted or used,
 * fish caught, battles won ...), counted from when that rank's training began; an advancement; or how many creature
 * forms your dex has. {@code of} is a block or item id or #tag (for mined / crafted / used / picked_up), a custom
 * statistic (for custom, e.g. minecraft:fish_caught or ahrivaea:battles_won), or an advancement id.
 */
public record Deed(String type, String of, int amount, String text) {
	public static Deed parse(JsonObject o) {
		String type = o.get("type").getAsString();
		String of = o.has("of") ? o.get("of").getAsString() : "";
		int amount = o.has("amount") ? o.get("amount").getAsInt() : 1;
		return new Deed(type, of, amount, o.has("text") ? o.get("text").getAsString() : type + " " + of + " x" + amount);
	}

	/** Advancements and the dex count everything ever done; the rest count from when the training began. */
	public boolean absolute() {
		return type.equals("advancement") || type.startsWith("dex_");
	}

	/** The player's count for this deed right now (not taking the baseline off). */
	public int value(ServerPlayer p) {
		var stats = p.getStats();
		return switch (type) {
			case "mined" -> {
				int n = 0;
				for (var b : blocks()) n += stats.getValue(Stats.BLOCK_MINED.get(b.value()));
				yield n;
			}
			case "crafted" -> items(p, Stats.ITEM_CRAFTED);
			case "used" -> items(p, Stats.ITEM_USED);
			case "picked_up" -> items(p, Stats.ITEM_PICKED_UP);
			case "custom" -> {
				var id = BuiltInRegistries.CUSTOM_STAT.getValue(Identifier.parse(of));
				yield id == null ? 0 : stats.getValue(Stats.CUSTOM.get(id));
			}
			case "advancement" -> {
				AdvancementHolder a = p.level().getServer().getAdvancements().get(Identifier.parse(of));
				yield a != null && p.getAdvancements().getOrStartProgress(a).isDone() ? 1 : 0;
			}
			case "dex_seen" -> dev.ahrivaea.party.Dex.count(p, dev.ahrivaea.party.Dex.SEEN);
			case "dex_caught" -> dev.ahrivaea.party.Dex.count(p, dev.ahrivaea.party.Dex.CAUGHT);
			default -> 0;
		};
	}

	/** Progress toward the deed from a baseline (clamped to 0..amount). */
	public int progress(ServerPlayer p, int baseline) {
		int v = value(p) - (absolute() ? 0 : baseline);
		return Math.clamp(v, 0, amount);
	}

	private List<Holder<net.minecraft.world.level.block.Block>> blocks() {
		List<Holder<net.minecraft.world.level.block.Block>> out = new ArrayList<>();
		if (of.startsWith("#")) {
			for (var h : BuiltInRegistries.BLOCK.getTagOrEmpty(TagKey.create(Registries.BLOCK, Identifier.parse(of.substring(1))))) out.add(h);
		} else {
			BuiltInRegistries.BLOCK.get(Identifier.parse(of)).ifPresent(out::add);
		}
		return out;
	}

	private int items(ServerPlayer p, StatType<net.minecraft.world.item.Item> statType) {
		int n = 0;
		if (of.startsWith("#")) {
			for (var h : BuiltInRegistries.ITEM.getTagOrEmpty(TagKey.create(Registries.ITEM, Identifier.parse(of.substring(1)))))
				n += p.getStats().getValue(statType.get(h.value()));
		} else {
			var item = BuiltInRegistries.ITEM.getOptional(Identifier.parse(of));
			if (item.isPresent()) n += p.getStats().getValue(statType.get(item.get()));
			else Ahrivaea.LOGGER.warn("Villager deed: unknown item {}", of);
		}
		return n;
	}
}
