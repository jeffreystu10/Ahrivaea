package dev.ahrivaea.battle;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.ahrivaea.Ahrivaea;
import dev.ahrivaea.creature.CreatureEntity;
import dev.ahrivaea.creature.Species;
import dev.ahrivaea.party.CaughtCreature;
import dev.ahrivaea.party.Party;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Starts, runs and ends battles, keeps the world out of them, and talks to the battle screen on each
 * player's client (the whole battle state goes over as JSON).
 */
public final class Battles {
	private Battles() {}

	public static final int RADIUS = 10;

	/** Server to client: the battle as it is now (empty JSON object = no battle). */
	public record StatePayload(String json) implements CustomPacketPayload {
		public static final Type<StatePayload> TYPE = new Type<>(Ahrivaea.id("battle_state"));
		public static final StreamCodec<RegistryFriendlyByteBuf, StatePayload> CODEC = StreamCodec.composite(ByteBufCodecs.stringUtf8(1 << 20), StatePayload::json, StatePayload::new);

		@Override
		public Type<StatePayload> type() {
			return TYPE;
		}
	}

	/** Client to server: an order (see {@link Battle.Action}), or "recruit" / "leave" after the battle. */
	public record ActionPayload(String json) implements CustomPacketPayload {
		public static final Type<ActionPayload> TYPE = new Type<>(Ahrivaea.id("battle_action"));
		public static final StreamCodec<RegistryFriendlyByteBuf, ActionPayload> CODEC = StreamCodec.composite(ByteBufCodecs.stringUtf8(4096), ActionPayload::json, ActionPayload::new);

		@Override
		public Type<ActionPayload> type() {
			return TYPE;
		}
	}

	private static final Map<UUID, Battle> ACTIVE = new HashMap<>();
	/** Operators looking at an arena with /battle arena (the only other people allowed in the arena dimension). */
	private static final Set<UUID> PREVIEWING = new HashSet<>();
	/** Finished battles whose results screen is still open (for recruiting). */
	private static final Map<UUID, Battle> FINISHED = new HashMap<>();

	public static Battle of(Player p) {
		return ACTIVE.get(p.getUUID());
	}

	public static boolean inBattle(Entity e) {
		for (Battle b : ACTIVE.values()) for (Unit u : b.units) if (e.getUUID().equals(u.entity)) return true;
		return false;
	}

	public static void init() {
		Ahrivaea.LOGGER.debug("Arena return point: {}", ArenaReturn.ATTACHMENT.identifier());
		PayloadTypeRegistry.clientboundPlay().register(StatePayload.TYPE, StatePayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ActionPayload.TYPE, ActionPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(ActionPayload.TYPE, (payload, ctx) -> handle(ctx.player(), payload.json()));

		// hitting a wild creature starts a battle instead of hurting it
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (!(player instanceof ServerPlayer sp) || !(entity instanceof CreatureEntity c) || c.isTame() || player.isSpectator()) return InteractionResult.PASS;
			if (of(player) != null || inBattle(c)) return InteractionResult.FAIL;
			// for now a sword is the only way to start a battle; anything else does nothing
			if (!player.getItemInHand(hand).is(ItemTags.SWORDS)) {
				sp.sendOverlayMessage(Component.literal("Hit it with a sword to battle it."));
				return InteractionResult.FAIL;
			}
			start(sp, c);
			return InteractionResult.FAIL;
		});
		// nothing outside the battle can hurt its units, and nobody can dig up the battlefield
		// and players can't hurt wild creatures outside a battle (arrows, tridents, explosions they set off ...)
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
				!inBattle(entity) && !(entity instanceof CreatureEntity && source.getEntity() instanceof Player));
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, be) -> {
			if (level.dimension().equals(Arena.DIMENSION)) return false;
			for (Battle b : ACTIVE.values()) {
				if (b.level == level && pos.getX() >= b.grid.originX && pos.getZ() >= b.grid.originZ
						&& pos.getX() < b.grid.originX + b.grid.size && pos.getZ() < b.grid.originZ + b.grid.size) return false;
			}
			return true;
		});
		// and nothing in an arena can be used, placed or broken
		net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, level, hand, hit) ->
				level.dimension().equals(Arena.DIMENSION) ? InteractionResult.FAIL : InteractionResult.PASS);
		net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.register((player, level, hand) ->
				level.dimension().equals(Arena.DIMENSION) && !player.isCreative() ? InteractionResult.FAIL : InteractionResult.PASS);
		net.fabricmc.fabric.api.event.player.AttackBlockCallback.EVENT.register((player, level, hand, pos, dir) ->
				level.dimension().equals(Arena.DIMENSION) ? InteractionResult.FAIL : InteractionResult.PASS);
		ServerTickEvents.END_SERVER_TICK.register(Battles::tick);
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			if (server.getLevel(Arena.DIMENSION) != null) Ahrivaea.LOGGER.info("Battle arena dimension ready");
			else Ahrivaea.LOGGER.error("Battle arena dimension missing: battles will be fought on the spot");
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			Battle b = ACTIVE.get(handler.getPlayer().getUUID());
			if (b != null) {
				b.say(handler.getPlayer().getPlainTextName() + " left.");
				b.outcome = Battle.Outcome.FLED;
				b.phase = Battle.Phase.OVER;
				cleanUp(b);
			}
			PREVIEWING.remove(handler.getPlayer().getUUID());
			Battle done = FINISHED.remove(handler.getPlayer().getUUID());
			if (done != null) goHome(null, done);
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayer p = handler.getPlayer();
			if (!ACTIVE.containsKey(p.getUUID()) && (p.hasAttached(ArenaReturn.ATTACHMENT) || p.level().dimension().equals(Arena.DIMENSION))) ArenaReturn.sendBack(p);
		});
		// nothing lives in the arena dimension except the creatures of battles going on there
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (SPAWNING || !level.dimension().equals(Arena.DIMENSION) || entity instanceof Player) return;
			if (!inBattle(entity)) entity.discard();
		});
	}

	// ---- starting ----

	public static Battle start(ServerPlayer player, CreatureEntity wild) {
		if (ACTIVE.containsKey(player.getUUID())) return null;
		var wildSpecies = wild.getSpecies();
		if (wildSpecies.isEmpty()) return null;
		ServerLevel home = (ServerLevel) player.level();
		ServerLevel arena = home.getServer().getLevel(Arena.DIMENSION);
		List<CaughtCreature> party = Party.get(player);
		var biome = home.getBiome(wild.blockPosition());
		// reinforcements: fewer, and less often, for low-level creatures
		int extra = reinforcements(wild.getCreatureLevel(), home.getRandom());
		// your creatures out in the world with you fight alongside you (then party creatures fill the rest, up to 4)
		List<CreatureEntity> out = home.getEntitiesOfClass(CreatureEntity.class, player.getBoundingBox().inflate(dev.ahrivaea.party.PartySync.OUT_RANGE),
				e -> e != wild && e.isTame() && e.isOwnedBy(player) && !e.hasAttached(dev.ahrivaea.base.Bases.WORKER_OF) && !inBattle(e) && e.getSpecies().isPresent());
		out.sort(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(player)));
		if (out.size() > Party.BATTLE_SIZE) out = out.subList(0, Party.BATTLE_SIZE);
		int fromParty = Math.min(Party.BATTLE_SIZE - out.size(), party.size());

		ServerLevel level;
		BattleGrid grid;
		int slot = -1;
		int[] playerCell, enemyCell;
		if (arena != null && !home.dimension().equals(Arena.DIMENSION)) {
			// off to an arena built for this battle, themed after where it happened
			slot = freeSlot();
			Arena.Theme theme = Arena.Theme.of(home, wild.blockPosition());
			int field = arenaField(theme, 1 + extra, out.size() + fromParty + 1);
			BlockPos c = Arena.build(arena, slot, theme, biome, home.getRandom().nextLong(), field);
			grid = BattleGrid.read(arena, c, field);
			level = arena;
			// you line up on the south edge, the wild creatures on the north edge
			playerCell = new int[] {field, grid.size - 4};
			enemyCell = new int[] {field, 4};
			Ahrivaea.LOGGER.info("Arena {} ({}, {}x{}) for {}", slot, theme, grid.size, grid.size, player.getPlainTextName());
		} else {
			// no arena dimension (or already in one): fight on the spot
			BlockPos center = BlockPos.containing(player.position().add(wild.position()).scale(0.5));
			grid = BattleGrid.read(home, center, RADIUS);
			level = home;
			playerCell = new int[] {Math.clamp(player.getBlockX() - grid.originX, 0, grid.size - 1), Math.clamp(player.getBlockZ() - grid.originZ, 0, grid.size - 1)};
			enemyCell = new int[] {Math.clamp(wild.getBlockX() - grid.originX, 0, grid.size - 1), Math.clamp(wild.getBlockZ() - grid.originZ, 0, grid.size - 1)};
		}
		Battle b = new Battle(player, level, grid, !party.isEmpty() || !out.isEmpty());
		b.arenaSlot = slot;

		int[] pc = b.freeCellNear(playerCell[0], playerCell[1], Unit.Side.PLAYER);
		b.addPlayer(player, pc[0], pc[1]);
		if (slot >= 0) {
			player.stopRiding(); // off any mount first; it waits where it was
			if (!player.hasAttached(ArenaReturn.ATTACHMENT)) player.setAttached(ArenaReturn.ATTACHMENT, ArenaReturn.of(player));
			BlockPos at = b.cellPos(b.playerUnit());
			player.teleportTo(level, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, Set.of(), 180, 30, true);
		}

		SPAWNING = true;
		try {
			// the ones that were out come along (they leave the world and come back out beside you afterwards)
			List<CaughtCreature> allies = new ArrayList<>();
			List<Integer> slots = new ArrayList<>();
			for (CreatureEntity e : out) {
				CaughtCreature c = e.toCaught();
				e.ejectPassengers();
				e.discard();
				slots.add(Battle.outSlot(b.outCreatures.size()));
				b.outCreatures.add(c);
				allies.add(c);
			}
			for (int i = 0; i < fromParty; i++) {
				allies.add(party.get(i));
				slots.add(i);
			}
			for (int i = 0; i < allies.size(); i++) {
				CaughtCreature c = allies.get(i);
				var species = Species.get(c.species());
				if (species.isEmpty()) continue;
				int[] cell = b.freeCellNear(pc[0], pc[1], Unit.Side.PLAYER);
				Unit u = b.addCreature(Unit.Side.PLAYER, species.get(), c.influence(), c.variant(), c.level(), c.health(), slots.get(i), cell[0], cell[1]);
				BlockPos at = b.cellPos(u);
				CreatureEntity e = Ahrivaea.spawn(level, species.get(), c.influence(), c.level(), u.hp, at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
				if (e != null) {
					e.setVariant(c.variant());
					e.tame(player);
					e.setNoAi(true);
					e.capSize(CreatureEntity.BATTLE_HITBOX);
					u.entity = e.getUUID();
				}
			}

			int[] wc = b.freeCellNear(enemyCell[0], enemyCell[1], Unit.Side.ENEMY);
			Unit lead = b.addCreature(Unit.Side.ENEMY, wildSpecies.get(), wild.getInfluence(), wild.getVariant(), wild.getCreatureLevel(), wild.getHealth(), -1, wc[0], wc[1]);
			b.lead = lead;
			b.wild = new Battle.Wild(home, wild.getSpeciesId(), wild.getInfluence(), wild.getCreatureLevel(), wild.getX(), wild.getY(), wild.getZ(), wild.getVariant());
			if (level == home) {
				lead.entity = wild.getUUID();
				wild.setNoAi(true);
			} else {
				// the wild creature comes along: it leaves the world, and comes back afterwards unless you won
				BlockPos at = b.cellPos(lead);
				CreatureEntity e = Ahrivaea.spawn(level, wildSpecies.get(), wild.getInfluence(), wild.getCreatureLevel(), lead.hp, at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
				if (e != null) {
					e.setVariant(wild.getVariant());
					e.setNoAi(true);
					e.capSize(CreatureEntity.BATTLE_HITBOX);
					lead.entity = e.getUUID();
				}
				wild.discard();
			}
			for (int i = 0; i < extra; i++) {
				// others of the area, no rarer and no higher level than the one you challenged and at most 2 levels
				// below it (it, again, if nothing else fits)
				var pick = dev.ahrivaea.party.RecruitRules.reinforcement(biome, home, b.random, wildSpecies.get(), lead.influence, lead.level);
				Species s = pick.map(Species.LevelledPick::species).orElse(wildSpecies.get());
				var inf = pick.map(Species.LevelledPick::influence).orElse(lead.influence);
				int lvl = pick.map(Species.LevelledPick::level).orElse(Math.max(0, lead.level - b.random.nextInt(dev.ahrivaea.party.RecruitRules.REINFORCEMENT_LEVELS_BELOW + 1)));
				int[] cell = b.freeCellNear(wc[0], wc[1], Unit.Side.ENEMY);
				var variant = dev.ahrivaea.creature.Variant.roll(home, b.random);
				Unit u = b.addCreature(Unit.Side.ENEMY, s, inf, variant, lvl, -1, -1, cell[0], cell[1]);
				BlockPos at = b.cellPos(u);
				CreatureEntity e = Ahrivaea.spawn(level, s, inf, lvl, -1, at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
				if (e != null) {
					e.setVariant(variant);
					e.setNoAi(true);
					e.capSize(CreatureEntity.BATTLE_HITBOX);
					u.entity = e.getUUID();
				}
			}
			b.say("A wild " + lead.name + " appears" + (extra == 0 ? "!" : " with " + extra + " more!")
					+ (out.isEmpty() ? "" : " " + out.size() + " of your creatures that were out join you.") + " Turn 1: your phase");
		} finally {
			SPAWNING = false;
		}
		for (Unit u : b.units) placeEntity(b, u);
		ACTIVE.put(player.getUUID(), b);
		FINISHED.remove(player.getUUID());
		Ahrivaea.LOGGER.info("Battle started for {}: {} units on a {}x{} field in {}", player.getPlainTextName(), b.units.size(), grid.size, grid.size, level.dimension().identifier());
		send(b);
		return b;
	}

	/** Arena size (half-width): the biome's usual size, a bit bigger for crowded battles and smaller for small ones. */
	static int arenaField(Arena.Theme theme, int enemies, int allies) {
		int units = enemies + allies;
		int f = theme.baseField() + (units >= 9 ? 1 : units <= 4 ? -1 : 0);
		return Math.clamp(f, Arena.MIN_FIELD, Arena.MAX_FIELD);
	}

	/** True while a battle is putting its creatures into the arena (anything else turning up there is removed). */
	private static boolean SPAWNING;

	/** The lowest arena slot no running or just-finished battle is using. */
	private static int freeSlot() {
		Set<Integer> used = new HashSet<>();
		for (Battle b : ACTIVE.values()) used.add(b.arenaSlot);
		for (Battle b : FINISHED.values()) used.add(b.arenaSlot);
		int s = 0;
		while (used.contains(s)) s++;
		return s;
	}

	/**
	 * How many wild creatures join the one you challenged: up to 1 for levels 1-10, 2 for 11-20, 3 for 21-30, 4 for
	 * 31-40 and 5 for 41-50, each one turning up 50%, 60%, 70%, 80% or 90% of the time.
	 */
	public static int reinforcements(int level, net.minecraft.util.RandomSource random) {
		int tier = Math.clamp((level + 9) / 10, 1, 5);
		double chance = 0.4 + 0.1 * tier;
		int n = 0;
		for (int i = 0; i < tier; i++) if (random.nextDouble() < chance) n++;
		return n;
	}

	/**
	 * After the results screen: back to where the battle started, with your creatures that were out beside you, and
	 * the wild creature back where it was unless you won (at full health if it was knocked out).
	 */
	static void goHome(ServerPlayer p, Battle b) {
		if (b != null && b.wild != null && b.lead != null && b.outcome != Battle.Outcome.WON) {
			// in a battle fought on the spot, a wild creature still standing never left
			boolean stillThere = b.level == b.wild.level() && b.lead.alive() && b.level.getEntity(b.lead.entity) != null;
			var species = Species.get(b.wild.species());
			var at = ArenaReturn.safeSpot(b.wild.level(), new net.minecraft.world.phys.Vec3(b.wild.x(), b.wild.y(), b.wild.z()));
			if (!stillThere && species.isPresent() && at != null) {
				CreatureEntity back = Ahrivaea.spawn(b.wild.level(), species.get(), b.wild.influence(), b.wild.creatureLevel(), b.lead.alive() ? b.lead.hp : -1, at.x, at.y, at.z);
				if (back != null) back.setVariant(b.wild.variant());
			}
		}
		if (b != null) b.wild = null;
		ServerPlayer owner = p != null ? p : b == null ? null : b.player();
		if (b != null && !b.outCreatures.isEmpty() && owner != null && p == null) {
			// leaving the game mid-battle: they go back into the party
			for (CaughtCreature c : b.outCreatures) Party.addAnywhere(owner, c);
			b.outCreatures.clear();
		}
		if (p != null) {
			Ahrivaea.LOGGER.info("Battle over for {}: back from {} (return point {})", p.getPlainTextName(), p.level().dimension().identifier(), p.getAttached(ArenaReturn.ATTACHMENT));
			try {
				ArenaReturn.sendBack(p);
			} catch (RuntimeException e) {
				Ahrivaea.LOGGER.error("Couldn't send {} back from the arena", p.getPlainTextName(), e);
			}
			if (b != null) {
				for (var stack : b.loot) if (!p.getInventory().add(stack) || !stack.isEmpty()) p.spawnAtLocation(p.level(), stack);
				b.loot.clear();
				// your creatures that were out come back out beside you
				for (CaughtCreature c : b.outCreatures) {
					var species = Species.get(c.species());
					CreatureEntity e = species.isEmpty() ? null : Ahrivaea.spawn((ServerLevel) p.level(), species.get(), c.influence(), c.level(), c.health(), p.getX(), p.getY(), p.getZ());
					if (e == null) {
						Party.addAnywhere(p, c);
						continue;
					}
					e.tame(p);
					e.applyCaught(c);
				}
				b.outCreatures.clear();
			}
		}
	}

	// ---- running ----

	private static void tick(MinecraftServer server) {
		// the arenas are only for battles: anyone else who turns up there (e.g. by /tp) is sent back
		ServerLevel arenaLevel = server.getLevel(Arena.DIMENSION);
		if (arenaLevel != null && server.getTickCount() % 20 == 0) {
			for (ServerPlayer p : List.copyOf(arenaLevel.players())) {
				UUID id = p.getUUID();
				if (ACTIVE.containsKey(id) || FINISHED.containsKey(id) || PREVIEWING.contains(id)) continue;
				Ahrivaea.LOGGER.info("{} is in the battle arenas without a battle; sending them back", p.getPlainTextName());
				ArenaReturn.sendBack(p);
			}
		}
		for (Battle b : List.copyOf(ACTIVE.values())) {
			ServerPlayer p = b.player();
			if (p == null) continue;
			// the player's body stays on its square
			Unit me = b.playerUnit();
			if (me != null && me.alive()) {
				BlockPos c = b.cellPos(me);
				if (p.position().distanceToSqr(c.getX() + 0.5, c.getY(), c.getZ() + 0.5) > 0.04) p.connection.teleport(c.getX() + 0.5, c.getY(), c.getZ() + 0.5, p.getYRot(), p.getXRot());
				p.setDeltaMovement(0, 0, 0);
			}
			if (b.phase == Battle.Phase.ENEMY && --b.enemyDelay <= 0) {
				b.enemyStep();
				b.enemyDelay = 15;
				send(b);
			}
		}
	}

	static void placeEntity(Battle b, Unit u) {
		if (u.isPlayer) return; // handled every tick
		Entity e = b.level.getEntity(u.entity);
		if (e == null) return;
		BlockPos c = b.cellPos(u);
		e.snapTo(c.getX() + 0.5, c.getY(), c.getZ() + 0.5);
		e.setDeltaMovement(0, 0, 0);
	}

	static void removeEntity(Battle b, Unit u) {
		if (u.isPlayer) return;
		Entity e = b.level.getEntity(u.entity);
		if (e != null) e.discard();
	}

	/** Ends a battle: party HP saved, the player's creatures go back to the party, survivors go back to the wild. */
	static void cleanUp(Battle b) {
		ACTIVE.remove(b.playerId);
		ServerPlayer p = b.player();
		if (p != null) b.saveParty(p);
		for (Unit u : b.units) {
			if (u.isPlayer || !u.alive()) continue;
			Entity e = b.level.getEntity(u.entity);
			if (e == null) continue;
			if (u.side == Unit.Side.PLAYER || b.arenaSlot >= 0) e.discard();
			else if (e instanceof CreatureEntity c) c.setNoAi(false);
		}
		if (p != null) {
			FINISHED.put(b.playerId, b);
			send(b);
		}
	}

	public static void send(Battle b) {
		ServerPlayer p = b.player();
		if (p != null) ServerPlayNetworking.send(p, new StatePayload(b.view(p.getUUID()).toString()));
	}

	/** An order from the battle screen. */
	public static String handle(ServerPlayer p, String json) {
		JsonObject j = JsonParser.parseString(json).getAsJsonObject();
		String kind = j.get("kind").getAsString();
		if (kind.equals("recruit") || kind.equals("leave")) {
			Battle done = FINISHED.get(p.getUUID());
			if (done == null) return "No battle to leave.";
			String msg = null;
			if (kind.equals("recruit")) msg = recruit(p, done, j.get("index").getAsInt());
			else {
				FINISHED.remove(p.getUUID());
				ServerPlayNetworking.send(p, new StatePayload("{}"));
				goHome(p, done);
			}
			if (msg != null) p.sendSystemMessage(Component.literal(msg).withStyle(ChatFormatting.RED));
			else if (FINISHED.containsKey(p.getUUID())) send(done);
			return msg;
		}
		Battle b = ACTIVE.get(p.getUUID());
		if (b == null) return "You're not in a battle.";
		Battle.Action a = new Battle.Action(kind, opt(j, "unit"), opt(j, "x"), opt(j, "z"), opt(j, "move"), opt(j, "target"));
		String problem = b.act(a);
		if (problem != null) p.sendOverlayMessage(Component.literal(problem));
		send(b);
		return problem;
	}

	private static int opt(JsonObject j, String k) {
		return j.has(k) ? j.get(k).getAsInt() : -1;
	}

	private static String recruit(ServerPlayer p, Battle b, int index) {
		if (index < 0 || index >= b.defeated.size()) return "Pick a creature to recruit.";
		Battle.Defeated d = b.defeated.get(index);
		String why = b.whyNotRecruit(d);
		if (why != null) return "You can't recruit " + d.name() + ": " + why + ".";
		if (Party.xp(p) < d.cost()) return "Recruiting " + d.name() + " costs " + d.cost() + " XP; you have " + Party.xp(p) + ".";
		var species = Species.get(d.species());
		if (species.isEmpty()) return "That creature no longer exists.";
		boolean party = Party.addAnywhere(p, new CaughtCreature(d.species(), d.influence(), d.level(), 0, species.get().statsFor(d.influence()).maxHealth(d.level()), 0, 0, false, d.variant()));
		Party.addXp(p, -d.cost());
		dev.ahrivaea.villager.Villagers.award(p, dev.ahrivaea.villager.Villagers.CREATURES_RECRUITED);
		b.defeated.remove(index);
		if (!party) p.sendSystemMessage(Component.literal("Your party is full, so " + d.name() + " went to your box (/party box).").withStyle(ChatFormatting.YELLOW));
		p.sendSystemMessage(Component.literal(d.name() + " joined your party! (-" + d.cost() + " XP)").withStyle(ChatFormatting.GREEN));
		return null;
	}

	// ---- commands (for testing and admins) ----

	/** Builds an arena of the given theme (no battle) and takes the player above it to look. Returns a problem, or null. */
	public static String previewArena(ServerPlayer p, String themeName) {
		Arena.Theme theme;
		try {
			theme = Arena.Theme.valueOf(themeName.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			return "Unknown arena theme: " + themeName;
		}
		ServerLevel arena = p.level().getServer().getLevel(Arena.DIMENSION);
		if (arena == null) return "The battle arena dimension is missing.";
		if (of(p) != null) return "Not during a battle.";
		var biome = arena.registryAccess().lookupOrThrow(Registries.BIOME)
				.get(ResourceKey.create(Registries.BIOME, Identifier.withDefaultNamespace(theme.sampleBiome())));
		BlockPos c = Arena.build(arena, 1000 + theme.ordinal(), theme, biome.<Holder<Biome>>map(h -> h).orElse(null), theme.ordinal() * 31L + 7, theme.baseField());
		PREVIEWING.add(p.getUUID());
		if (!p.hasAttached(ArenaReturn.ATTACHMENT) && !p.level().dimension().equals(Arena.DIMENSION)) p.setAttached(ArenaReturn.ATTACHMENT, ArenaReturn.of(p));
		p.teleportTo(arena, c.getX() + 0.5, c.getY() + 18, c.getZ() + theme.baseField() + 8.5, Set.of(), 180, 45, true);
		if (p.getAbilities().mayfly) {
			p.getAbilities().flying = true;
			p.onUpdateAbilities();
		}
		Ahrivaea.LOGGER.info("Arena preview {} at {} for {}", theme, c.toShortString(), p.getPlainTextName());
		return null;
	}

	public static void registerCommands(CommandDispatcher<CommandSourceStack> d) {
		d.register(Commands.literal("battle").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("start").executes(ctx -> {
					ServerPlayer p = ctx.getSource().getPlayerOrException();
					List<CreatureEntity> near = p.level().getEntitiesOfClass(CreatureEntity.class, new AABB(p.blockPosition()).inflate(16), c -> !c.isTame());
					if (near.isEmpty()) {
						ctx.getSource().sendFailure(Component.literal("No wild creature within 16 blocks."));
						return 0;
					}
					near.sort((x, y) -> Double.compare(x.distanceToSqr(p), y.distanceToSqr(p)));
					return start(p, near.getFirst()) != null ? 1 : 0;
				}))
				.then(Commands.literal("end").executes(ctx -> {
					Battle b = of(ctx.getSource().getPlayerOrException());
					if (b == null) return 0;
					b.outcome = Battle.Outcome.FLED;
					b.phase = Battle.Phase.OVER;
					cleanUp(b);
					return 1;
				}))
				// look at an arena without a battle: /battle arena <theme>, then /battle home
				.then(Commands.literal("arena").then(Commands.argument("theme", StringArgumentType.word())
						.suggests((ctx, sb) -> SharedSuggestionProvider.suggest(Arrays.stream(Arena.Theme.values()).map(t -> t.name().toLowerCase(Locale.ROOT)), sb))
						.executes(ctx -> {
							String msg = previewArena(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "theme"));
							if (msg != null) ctx.getSource().sendFailure(Component.literal(msg));
							return msg == null ? 1 : 0;
						})))
				.then(Commands.literal("home").executes(ctx -> {
					ServerPlayer p = ctx.getSource().getPlayerOrException();
					if (of(p) != null) return 0;
					PREVIEWING.remove(p.getUUID());
					goHome(p, FINISHED.remove(p.getUUID()));
					return 1;
				}))
				.then(Commands.literal("recruit").then(Commands.argument("index", IntegerArgumentType.integer(0)).executes(ctx -> {
					ServerPlayer p = ctx.getSource().getPlayerOrException();
					Battle b = FINISHED.get(p.getUUID());
					String msg = b == null ? "No finished battle." : recruit(p, b, IntegerArgumentType.getInteger(ctx, "index"));
					if (msg != null) ctx.getSource().sendFailure(Component.literal(msg));
					return msg == null ? 1 : 0;
				}))));
	}
}
