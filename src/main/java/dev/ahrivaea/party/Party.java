package dev.ahrivaea.party;

import com.mojang.serialization.Codec;
import dev.ahrivaea.Ahrivaea;
import dev.ahrivaea.creature.BattleSettings;
import dev.ahrivaea.creature.Stats;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Each player's party of up to six creatures (four go into a battle) and their pool of unspent XP,
 * both saved with the player and kept on death.
 */
public final class Party {
	public static final int MAX = 6;
	public static final int BATTLE_SIZE = 4;

	public static final AttachmentType<List<CaughtCreature>> PARTY = AttachmentRegistry.create(
			Ahrivaea.id("party"),
			builder -> builder.persistent(CaughtCreature.CODEC.listOf()).copyOnDeath().initializer(List::of));

	public static final AttachmentType<Integer> XP = AttachmentRegistry.create(
			Ahrivaea.id("xp"),
			builder -> builder.persistent(Codec.INT).copyOnDeath().initializer(() -> 0));

	/** Creatures that don't fit in the party (and workers sent back from a base): no limit. */
	public static final AttachmentType<List<CaughtCreature>> BOX = AttachmentRegistry.create(
			Ahrivaea.id("box"),
			builder -> builder.persistent(CaughtCreature.CODEC.listOf()).copyOnDeath().initializer(List::of));

	private Party() {}

	public static List<CaughtCreature> box(Player player) {
		return player.getAttachedOrCreate(BOX);
	}

	public static void addToBox(Player player, CaughtCreature creature) {
		List<CaughtCreature> list = new ArrayList<>(box(player));
		list.add(creature);
		player.setAttached(BOX, List.copyOf(list));
	}

	public static CaughtCreature removeFromBox(Player player, int index) {
		List<CaughtCreature> list = new ArrayList<>(box(player));
		CaughtCreature c = list.remove(index);
		player.setAttached(BOX, List.copyOf(list));
		return c;
	}

	/** Into the party if there's room, else the box. Returns true if it went into the party. */
	public static boolean addAnywhere(Player player, CaughtCreature creature) {
		if (add(player, creature)) return true;
		addToBox(player, creature);
		return false;
	}

	public static void init() {
		// touching this class registers the attachments
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

	public static void set(Player player, int index, CaughtCreature creature) {
		List<CaughtCreature> list = new ArrayList<>(get(player));
		list.set(index, creature);
		player.setAttached(PARTY, List.copyOf(list));
	}

	public static int xp(Player player) {
		return player.getAttachedOrCreate(XP);
	}

	public static void addXp(Player player, int amount) {
		player.setAttached(XP, Math.max(0, xp(player) + amount));
	}

	/**
	 * Spends up to {@code amount} XP from the player's pool on one party creature: 100 XP per level, up to
	 * level 50. Returns the XP actually spent.
	 */
	public static int feed(Player player, int index, int amount) {
		CaughtCreature c = get(player).get(index);
		int spend = Math.min(amount, xp(player));
		int level = c.level(), xp = c.xp(), spent = 0;
		while (spent < spend && level < Stats.MAX_LEVEL) {
			int step = Math.min(spend - spent, BattleSettings.XP_PER_LEVEL - xp);
			xp += step;
			spent += step;
			if (xp >= BattleSettings.XP_PER_LEVEL) {
				level++;
				xp = 0;
			}
		}
		set(player, index, c.withProgress(level, xp));
		addXp(player, -spent);
		return spent;
	}
}
