package dev.pokecraft.party;

import dev.pokecraft.PokeCraft;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

/** Each player's party of up to six creatures, saved with the player and kept on death. */
public final class Party {
	public static final int MAX = 6;

	public static final AttachmentType<List<CaughtCreature>> PARTY = AttachmentRegistry.create(
			PokeCraft.id("party"),
			builder -> builder.persistent(CaughtCreature.CODEC.listOf()).copyOnDeath().initializer(List::of));

	private Party() {}

	public static void init() {
		// touching this class registers the attachment
	}

	public static List<CaughtCreature> get(Player player) {
		return player.getAttachedOrCreate(PARTY);
	}

	/** Adds a creature; returns false if the party is full. */
	public static boolean add(Player player, CaughtCreature creature) {
		List<CaughtCreature> list = new ArrayList<>(get(player));
		if (list.size() >= MAX) return false;
		list.add(creature);
		player.setAttached(PARTY, List.copyOf(list));
		return true;
	}

	public static CaughtCreature remove(Player player, int index) {
		List<CaughtCreature> list = new ArrayList<>(get(player));
		CaughtCreature c = list.remove(index);
		player.setAttached(PARTY, List.copyOf(list));
		return c;
	}
}
