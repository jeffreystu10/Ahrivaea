package dev.ahrivaea.battle;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.ahrivaea.Ahrivaea;
import dev.ahrivaea.character.PlayerCharacter;
import dev.ahrivaea.creature.BattleSettings;
import dev.ahrivaea.creature.CreatureEntity;
import dev.ahrivaea.creature.Influence;
import dev.ahrivaea.creature.Species;
import dev.ahrivaea.party.CaughtCreature;
import dev.ahrivaea.party.Party;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.ItemAttributeModifiers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One battle: the grid, the units and the rules (see docs/design.md). The player acts through
 * {@link #act}; the enemy phase runs one unit at a time from {@link Battles}' tick.
 */
public final class Battle {
	public enum Phase { PLAYER, ENEMY, OVER }

	public enum Outcome { WON, LOST, FLED }

	/** What the player asked one unit to do: move to (toX, toZ), then the action. */
	public record Action(String kind, int unit, int toX, int toZ, int move, int target) {}

	/** A defeated wild creature the player may recruit afterwards. */
	public record Defeated(String species, Influence influence, int level, String name, dev.ahrivaea.creature.Variant variant) {
		public int cost() {
			return dev.ahrivaea.creature.Species.get(species).map(s -> dev.ahrivaea.party.RecruitRules.cost(s, influence, level)).orElse(0);
		}

		public String rarity() {
			return dev.ahrivaea.creature.Species.get(species).map(s -> dev.ahrivaea.party.RecruitRules.rarity(s, influence)).orElse("Common");
		}
	}

	public final UUID playerId;
	public final ServerLevel level;
	public final BattleGrid grid;
	public final List<Unit> units = new ArrayList<>();
	public final List<String> log = new ArrayList<>();
	public final List<Defeated> defeated = new ArrayList<>();
	public final RandomSource random;
	public Phase phase = Phase.PLAYER;
	public int turn = 1;
	public Outcome outcome;
	public int poolXp, characterXp;
	/** Why a defeated creature can't be recruited (null if it can). */
	public String whyNotRecruit(Defeated d) {
		var s = dev.ahrivaea.creature.Species.get(d.species());
		return s.isEmpty() ? "it no longer exists" : null;
	}
	final boolean startedWithCreatures;
	private final List<Unit> enemyQueue = new ArrayList<>();
	int enemyDelay;
	private int nextId = 1;

	/** The arena slot this battle uses in the battle dimension (-1 = fought on the real terrain). */
	int arenaSlot = -1;
	/** The wild creature that started it, taken out of the world for the battle; put back unless it was defeated. */
	Wild wild;
	/** Drops still owed to the player (didn't fit in the inventory), and everything dropped, for the results screen. */
	final List<net.minecraft.world.item.ItemStack> loot = new ArrayList<>();
	final List<String> lootNames = new ArrayList<>();
	/** The unit standing in for that wild creature. */
	Unit lead;

	/**
	 * Your creatures that were out in the world with you when the battle started: they fight too (as units whose
	 * partySlot is -2 for the first, -3 for the second ...), and come back out beside you afterwards.
	 */
	final List<CaughtCreature> outCreatures = new ArrayList<>();

	static int outSlot(int index) {
		return -2 - index;
	}

	record Wild(ServerLevel level, String species, dev.ahrivaea.creature.Influence influence, int creatureLevel, double x, double y, double z,
			dev.ahrivaea.creature.Variant variant) {}

	Battle(ServerPlayer player, ServerLevel level, BattleGrid grid, boolean startedWithCreatures) {
		this.playerId = player.getUUID();
		this.level = level;
		this.grid = grid;
		this.random = level.getRandom();
		this.startedWithCreatures = startedWithCreatures;
	}

	int newId() {
		return nextId++;
	}

	// ---- queries ----

	public Unit unit(int id) {
		for (Unit u : units) if (u.id == id) return u;
		return null;
	}

	public Unit unitAt(int x, int z) {
		for (Unit u : units) if (u.alive() && u.x == x && u.z == z) return u;
		return null;
	}

	public Unit playerUnit() {
		for (Unit u : units) if (u.isPlayer) return u;
		return null;
	}

	public Pathing.Occupancy occupancy(Unit mover) {
		return (x, z) -> {
			Unit o = unitAt(x, z);
			if (o == null || o == mover) return 0;
			return o.side == mover.side ? 1 : 2;
		};
	}

	public Map<Integer, Integer> reachable(Unit u) {
		return Pathing.reachable(grid, u.x, u.z, u.squares(), u.flies(), u.influence == Influence.FIRE, occupancy(u));
	}

	public boolean canStop(Unit u, int x, int z) {
		return Pathing.canStop(grid, x, z, u.flies(), u.influence == Influence.FIRE, occupancy(u), u.x, u.z);
	}

	private boolean inWater(Unit u) {
		BattleGrid.Terrain t = grid.at(u.x, u.z);
		return t == BattleGrid.Terrain.WATER || t == BattleGrid.Terrain.DEEP_WATER;
	}

	// ---- the player's actions ----

	/** Carries out one of the player's orders. Returns null if it worked, or why it can't be done. */
	public String act(Action a) {
		// fleeing works at any time, in either phase
		if (a.kind().equals("flee")) return phase == Phase.OVER ? "The battle is over." : flee();
		if (phase != Phase.PLAYER) return "It isn't your turn.";
		if (a.kind().equals("end_turn")) {
			startEnemyPhase();
			return null;
		}
		Unit u = unit(a.unit());
		if (u == null || !u.alive() || u.side != Unit.Side.PLAYER) return "That's not one of your units.";
		if (u.acted) return u.name + " has already acted this turn.";
		if (!grid.inside(a.toX(), a.toZ())) return "That's off the battlefield.";
		Integer cost = reachable(u).get(a.toX() + a.toZ() * grid.size);
		if (cost == null || !canStop(u, a.toX(), a.toZ())) return u.name + " can't move there.";

		// check the action is possible from the new spot before moving
		int ox = u.x, oz = u.z;
		u.x = a.toX();
		u.z = a.toZ();
		String problem = switch (a.kind()) {
			case "attack" -> {
				Unit t = unit(a.target());
				if (a.move() < 0 || a.move() >= u.moves.size()) yield "No such move.";
				if (t == null || !t.alive() || t.side == u.side) yield "Pick an enemy to attack.";
				if (!u.moves.get(a.move()).reaches(Pathing.distance(u.x, u.z, t.x, t.z))) yield "That target is out of range.";
				yield null;
			}
			case "item" -> u.isPlayer ? itemCheck(u, unit(a.target())) : "Only you can use items.";
			case "rally" -> {
				Unit t = unit(a.target());
				if (!u.isPlayer) yield "Only you can rally.";
				if (t == null || t == u || t.side != u.side || !t.alive() || Pathing.distance(u.x, u.z, t.x, t.z) != 1) yield "Rally a creature next to you.";
				yield null;
			}
			case "wait" -> null;
			default -> "Unknown action.";
		};
		if (problem != null) {
			u.x = ox;
			u.z = oz;
			return problem;
		}
		Battles.placeEntity(this, u);
		switch (a.kind()) {
			case "attack" -> attack(u, unit(a.target()), u.moves.get(a.move()), true);
			case "item" -> useItem(u, unit(a.target()));
			case "rally" -> {
				Unit t = unit(a.target());
				t.rally = 1 + u.charisma() / 200.0;
				say(u.name + " rallies " + t.name + " (+" + Math.round((t.rally - 1) * 100) + "% damage on its next attack)");
			}
			default -> {
			}
		}
		u.acted = true;
		if (!checkEnd() && units.stream().noneMatch(x -> x.alive() && x.side == Unit.Side.PLAYER && !x.acted)) startEnemyPhase();
		return null;
	}

	private String itemCheck(Unit u, Unit target) {
		ServerPlayer p = player();
		if (p == null || dev.ahrivaea.creature.Healing.firstPotion(p).isEmpty())
			return "You have no healing potions. Brew Healing or Regeneration potions (food only heals outside battle).";
		if (target == null || target.side != u.side || !target.alive() || Pathing.distance(u.x, u.z, target.x, target.z) > 1) return "Use it on yourself or a unit next to you.";
		return null;
	}

	/** Uses the first healing potion in the player's inventory; a splash or lingering one heals the target's neighbours too. */
	private void useItem(Unit u, Unit target) {
		ServerPlayer p = player();
		ItemStack s = dev.ahrivaea.creature.Healing.firstPotion(p);
		var heal = dev.ahrivaea.creature.Healing.potion(s);
		if (heal == null) return;
		boolean drinkable = s.is(Items.POTION);
		if (!p.getAbilities().instabuild) s.shrink(1);
		if (drinkable) dev.ahrivaea.creature.Healing.giveBack(p, new ItemStack(Items.GLASS_BOTTLE));
		StringBuilder msg = new StringBuilder(u.name + " uses " + heal.name() + " on " + (target == u ? "themself" : target.name));
		for (Unit t : units) {
			boolean hit = t == target || (heal.splash() && t.side == target.side && t.alive() && Pathing.distance(t.x, t.z, target.x, target.z) == 1);
			if (!hit) continue;
			int amount = Math.min(t.maxHp - t.hp, (int) Math.round(t.maxHp * heal.percent() / 100.0));
			t.hp += amount;
			msg.append(t == target ? ": " : ", ").append(t == target ? "" : t.name + " ").append("+").append(amount).append(" HP");
		}
		say(msg.toString());
	}

	/** Running away always works, at any point of the battle. No XP; your creatures keep the damage they took. */
	private String flee() {
		say("You got away!");
		finish(Outcome.FLED);
		return null;
	}

	// ---- combat ----

	private void attack(Unit attacker, Unit target, BattleMove move, boolean allowCounter) {
		strike(attacker, target, move);
		if (allowCounter && target.alive() && attacker.alive()) {
			BattleMove counter = bestMove(target, attacker, Pathing.distance(attacker.x, attacker.z, target.x, target.z));
			if (counter != null) {
				say(target.name + " counters!");
				strike(target, attacker, counter);
			}
		}
	}

	private BattleMove bestMove(Unit from, Unit to, int distance) {
		BattleMove best = null;
		double bestDamage = -1;
		for (BattleMove m : from.moves) {
			if (!m.reaches(distance)) continue;
			double d = preview(from, to, m).expected();
			if (d > bestDamage) {
				bestDamage = d;
				best = m;
			}
		}
		return best;
	}

	DamageCalc.Result preview(Unit a, Unit d, BattleMove m) {
		double highGround = grid.y(a.x, a.z) - grid.y(d.x, d.z) >= 2 ? 0.10 : 0;
		double allies = 0;
		for (Unit o : units) if (o != a && o.alive() && o.side == a.side && Pathing.distance(o.x, o.z, a.x, a.z) == 1) allies += o.charisma();
		return DamageCalc.calculate(a.fighter(inWater(a)), d.fighter(inWater(d)), m.move(), false, 1.0, allies,
				new DamageCalc.Extras(highGround, a.rally));
	}

	private void strike(Unit a, Unit d, BattleMove m) {
		DamageCalc.Result p = preview(a, d, m);
		double rally = a.rally;
		a.rally = 1;
		if (random.nextDouble() >= p.hitChance()) {
			say(a.name + " uses " + m.name() + " on " + d.name + " - missed!");
			return;
		}
		boolean crit = random.nextDouble() < p.critChance();
		double highGround = grid.y(a.x, a.z) - grid.y(d.x, d.z) >= 2 ? 0.10 : 0;
		double allies = 0;
		for (Unit o : units) if (o != a && o.alive() && o.side == a.side && Pathing.distance(o.x, o.z, a.x, a.z) == 1) allies += o.charisma();
		var r = DamageCalc.calculate(a.fighter(inWater(a)), d.fighter(inWater(d)), m.move(), crit, 0.9 + random.nextDouble() * 0.2, allies,
				new DamageCalc.Extras(highGround, rally));
		d.hp = Math.max(0, d.hp - r.damage());
		String eff = r.multiplier() > 1 ? " It's super effective!" : r.multiplier() < 1 ? " It's not very effective." : "";
		say(a.name + " uses " + m.name() + " on " + d.name + ": " + r.damage() + " damage" + (crit ? " (critical!)" : "") + "." + eff);
		if (!d.alive()) knockOut(d);
	}

	private void knockOut(Unit d) {
		say(d.name + (d.isPlayer ? " is knocked out!" : d.side == Unit.Side.PLAYER ? " fainted!" : " is defeated!"));
		if (d.side == Unit.Side.ENEMY) {
			Entity e = level.getEntity(d.entity);
			if (e instanceof CreatureEntity c) defeated.add(new Defeated(c.getSpeciesId(), c.getInfluence(), c.getCreatureLevel(), d.name, c.getVariant()));
		}
		Battles.removeEntity(this, d);
	}

	// ---- enemy phase ----

	private void startEnemyPhase() {
		phase = Phase.ENEMY;
		enemyQueue.clear();
		for (Unit u : units) if (u.alive() && u.side == Unit.Side.ENEMY) enemyQueue.add(u);
		enemyDelay = 10;
		say("Enemy phase");
	}

	/** Runs one enemy unit's turn. Returns false when the phase is over. */
	boolean enemyStep() {
		while (!enemyQueue.isEmpty() && !enemyQueue.getFirst().alive()) enemyQueue.removeFirst();
		if (enemyQueue.isEmpty() || phase != Phase.ENEMY) {
			if (phase == Phase.ENEMY) {
				phase = Phase.PLAYER;
				turn++;
				for (Unit u : units) u.acted = false;
				say("Turn " + turn + ": your phase");
			}
			return false;
		}
		Unit u = enemyQueue.removeFirst();
		Map<Integer, Integer> reach = reachable(u);
		int bestCell = -1;
		Unit bestTarget = null;
		BattleMove bestMove = null;
		double bestScore = -1;
		for (int cell : reach.keySet()) {
			int cx = cell % grid.size, cz = cell / grid.size;
			if (!canStop(u, cx, cz)) continue;
			int ox = u.x, oz = u.z;
			u.x = cx;
			u.z = cz;
			for (Unit t : units) {
				if (!t.alive() || t.side == u.side) continue;
				int dist = Pathing.distance(cx, cz, t.x, t.z);
				for (BattleMove m : u.moves) {
					if (!m.reaches(dist)) continue;
					var p = preview(u, t, m);
					double score = p.expected() + (p.damage() >= t.hp ? 1000 : 0) + (t.isPlayer ? 15 : 0) - reach.get(cell) * 0.01;
					if (score > bestScore) {
						bestScore = score;
						bestCell = cell;
						bestTarget = t;
						bestMove = m;
					}
				}
			}
			u.x = ox;
			u.z = oz;
		}
		if (bestTarget != null) {
			u.x = bestCell % grid.size;
			u.z = bestCell / grid.size;
			Battles.placeEntity(this, u);
			attack(u, bestTarget, bestMove, true);
		} else {
			// nobody in reach: close in on the nearest of the player's units
			int best = -1, bestDist = Integer.MAX_VALUE;
			for (int cell : reach.keySet()) {
				int cx = cell % grid.size, cz = cell / grid.size;
				if (!canStop(u, cx, cz)) continue;
				for (Unit t : units) {
					if (!t.alive() || t.side == u.side) continue;
					int d = Pathing.distance(cx, cz, t.x, t.z);
					if (d < bestDist) {
						bestDist = d;
						best = cell;
					}
				}
			}
			if (best >= 0) {
				u.x = best % grid.size;
				u.z = best / grid.size;
				Battles.placeEntity(this, u);
				say(u.name + " moves closer.");
			}
		}
		checkEnd();
		return phase == Phase.ENEMY;
	}

	// ---- ending ----

	boolean checkEnd() {
		if (phase == Phase.OVER) return true;
		Unit me = playerUnit();
		boolean enemiesLeft = units.stream().anyMatch(u -> u.alive() && u.side == Unit.Side.ENEMY);
		if (!enemiesLeft) {
			finish(Outcome.WON);
			return true;
		}
		// you lose only when you're knocked out yourself; your creatures fainting doesn't end it
		if (me == null || !me.alive()) {
			finish(Outcome.LOST);
			return true;
		}
		return false;
	}

	private void finish(Outcome o) {
		outcome = o;
		phase = Phase.OVER;
		ServerPlayer p = player();
		if (o == Outcome.WON) {
			// your own XP: 5 for the win, and about 5 per creature beaten, more for ones above your level, less below;
			// your creatures' XP pool gets the same, so they level at your pace
			int me = p == null ? 1 : PlayerCharacter.get(p).level();
			characterXp = 5;
			for (Defeated d : defeated) characterXp += (int) Math.max(1, Math.round(5 * Math.clamp(1 + 0.1 * (d.level() - me), 0.2, 2.0)));
			poolXp = characterXp;
			say("Victory! +" + poolXp + " XP for your creatures, +" + characterXp + " XP for you.");
			// what the defeated creatures drop; anything that doesn't fit in your inventory waits until you're home
			for (Defeated d : defeated) {
				var sp = dev.ahrivaea.creature.Species.get(d.species());
				if (sp.isPresent()) loot.addAll(dev.ahrivaea.creature.Drops.roll(sp.get(), d.influence(), d.level(), random, level.registryAccess()));
			}
			// the road to the End: a battle won at night may give an ender pearl, and Fire creatures beaten in the
			// Nether may give a blaze rod (together they make Eyes of Ender, which lead to the towers)
			net.minecraft.world.level.Level home = wild != null ? wild.level() : p != null ? p.level() : level;
			if (home.isDarkOutside() && random.nextDouble() < BattleSettings.NIGHT_PEARL_CHANCE) loot.add(new net.minecraft.world.item.ItemStack(Items.ENDER_PEARL));
			if (home.dimension() == net.minecraft.world.level.Level.NETHER)
				for (Defeated d : defeated)
					if (d.influence() == Influence.FIRE && random.nextDouble() < BattleSettings.NETHER_BLAZE_ROD_CHANCE) loot.add(new net.minecraft.world.item.ItemStack(Items.BLAZE_ROD));
			for (var stack : loot) lootNames.add(dev.ahrivaea.creature.Drops.describe(stack));
			if (p != null) {
				for (var it = loot.iterator(); it.hasNext(); ) {
					var stack = it.next();
					if (p.getInventory().add(stack) && stack.isEmpty()) it.remove();
				}
				Party.addXp(p, poolXp);
				PlayerCharacter.addXp(p, characterXp);
			}
		} else if (o == Outcome.LOST) {
			say("You were defeated and retreat.");
			defeated.clear();
		} else {
			defeated.clear();
		}
		Ahrivaea.LOGGER.info("Battle for {} ended: {} after {} turns", p == null ? playerId : p.getPlainTextName(), o, turn);
		Battles.cleanUp(this);
	}

	void say(String s) {
		log.add(s);
		if (log.size() > 40) log.removeFirst();
	}

	public ServerPlayer player() {
		return level.getServer().getPlayerList().getPlayer(playerId);
	}

	// ---- units ----

	Unit addPlayer(ServerPlayer p, int x, int z) {
		PlayerCharacter c = PlayerCharacter.get(p);
		ItemStack hand = p.getMainHandItem();
		boolean ranged = hand.is(Items.BOW) || hand.is(Items.CROSSBOW);
		double dmg = hand.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).compute(Attributes.ATTACK_DAMAGE, 1.0, EquipmentSlot.MAINHAND);
		String weapon = hand.isEmpty() ? "Punch" : hand.getHoverName().getString();
		Unit u = new Unit(newId(), Unit.Side.PLAYER, true, p.getPlainTextName(), c.battleStats(p), c.level(), Influence.NONE, Species.Movement.WALK,
				List.of(BattleMove.forWeapon(weapon, dmg, ranged)), c.maxHealth(), c.maxHealth(), "", -1);
		u.x = x;
		u.z = z;
		u.entity = p.getUUID();
		units.add(u);
		return u;
	}

	Unit addCreature(Unit.Side side, Species species, Influence influence, int lvl, float health, int slot, int x, int z) {
		return addCreature(side, species, influence, dev.ahrivaea.creature.Variant.NONE, lvl, health, slot, x, z);
	}

	Unit addCreature(Unit.Side side, Species species, Influence influence, dev.ahrivaea.creature.Variant variant, int lvl, float health, int slot, int x, int z) {
		var stats = species.statsFor(influence);
		int max = stats.maxHealth(lvl);
		int hp = health <= 0 ? max : Math.max(1, Math.min(max, Math.round(health)));
		Unit u = new Unit(newId(), side, false, variant.prefix() + species.nameFor(influence), stats, lvl, influence, species.movement(),
				BattleMove.forCreature(influence), max, hp, species.id(), slot);
		u.x = x;
		u.z = z;
		units.add(u);
		return u;
	}

	/** Writes the player's creatures' HP back to their party (fainted ones come back at 1 HP). */
	void saveParty(ServerPlayer p) {
		List<CaughtCreature> party = Party.get(p);
		for (Unit u : units) {
			if (u.side != Unit.Side.PLAYER || u.isPlayer) continue;
			int out = -2 - u.partySlot;
			if (out >= 0 && out < outCreatures.size()) outCreatures.set(out, outCreatures.get(out).withHealth(Math.max(1, u.hp)));
			if (u.partySlot < 0 || u.partySlot >= party.size()) continue;
			CaughtCreature c = party.get(u.partySlot);
			Party.set(p, u.partySlot, c.withHealth(Math.max(1, u.hp)));
		}
	}

	// ---- what the client sees ----

	public JsonObject view(UUID viewer) {
		JsonObject o = new JsonObject();
		JsonObject g = new JsonObject();
		g.addProperty("size", grid.size);
		g.addProperty("ox", grid.originX);
		g.addProperty("oz", grid.originZ);
		JsonArray h = new JsonArray();
		StringBuilder t = new StringBuilder();
		for (int i = 0; i < grid.heights.length; i++) {
			h.add(grid.heights[i]);
			t.append(grid.terrain[i].code());
		}
		g.add("h", h);
		g.addProperty("t", t.toString());
		o.add("grid", g);
		JsonArray us = new JsonArray();
		for (Unit u : units) {
			JsonObject j = new JsonObject();
			j.addProperty("id", u.id);
			j.addProperty("side", u.side.name());
			j.addProperty("player", u.isPlayer);
			j.addProperty("name", u.name);
			j.addProperty("hp", u.hp);
			j.addProperty("max", u.maxHp);
			j.addProperty("x", u.x);
			j.addProperty("z", u.z);
			j.addProperty("acted", u.acted);
			j.addProperty("flies", u.flies());
			j.addProperty("lava", u.influence == Influence.FIRE);
			j.addProperty("squares", u.squares());
			j.addProperty("level", u.level);
			j.addProperty("influence", u.influence.displayName());
			j.addProperty("color", u.influence.color());
			JsonArray ms = new JsonArray();
			for (BattleMove m : u.moves) {
				JsonObject mj = new JsonObject();
				mj.addProperty("name", m.name());
				mj.addProperty("min", m.minRange());
				mj.addProperty("max", m.maxRange());
				mj.addProperty("power", m.move().power());
				mj.addProperty("kind", m.move().category().name());
				ms.add(mj);
			}
			j.add("moves", ms);
			us.add(j);
		}
		o.add("units", us);
		o.addProperty("phase", phase.name());
		o.addProperty("turn", turn);
		JsonArray lg = new JsonArray();
		for (int i = Math.max(0, log.size() - 8); i < log.size(); i++) lg.add(log.get(i));
		o.add("log", lg);
		if (outcome != null) {
			JsonObject r = new JsonObject();
			r.addProperty("outcome", outcome.name());
			r.addProperty("xp", poolXp);
			r.addProperty("charXp", characterXp);
			JsonArray lootJson = new JsonArray();
			for (String l : lootNames) lootJson.add(l);
			r.add("loot", lootJson);
			JsonArray rec = new JsonArray();
			for (Defeated d : defeated) {
				JsonObject dj = new JsonObject();
				dj.addProperty("name", d.name());
				dj.addProperty("level", d.level());
				dj.addProperty("cost", d.cost());
				dj.addProperty("rarity", d.rarity());
				String why = whyNotRecruit(d);
				if (why != null) dj.addProperty("why", why);
				rec.add(dj);
			}
			r.add("recruits", rec);
			o.add("result", r);
		}
		return o;
	}

	/** Where to start: the nearest cell to (x, z) a unit could stand on and that's free. */
	int[] freeCellNear(int x, int z, Unit.Side side) {
		for (int r = 0; r < grid.size; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
					int cx = x + dx, cz = z + dz;
					if (!grid.inside(cx, cz) || unitAt(cx, cz) != null) continue;
					BattleGrid.Terrain t = grid.at(cx, cz);
					if (t == BattleGrid.Terrain.GROUND || t == BattleGrid.Terrain.WATER) return new int[]{cx, cz};
				}
			}
		}
		return new int[]{x, z};
	}

	public BlockPos cellPos(Unit u) {
		return grid.worldPos(u.x, u.z);
	}
}
