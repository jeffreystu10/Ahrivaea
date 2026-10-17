package dev.ahrivaea;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.ahrivaea.creature.BattleSettings;
import dev.ahrivaea.creature.Influence;
import dev.ahrivaea.creature.InfluenceRules;
import dev.ahrivaea.creature.Stats;
import dev.ahrivaea.creature.CreatureEntity;
import dev.ahrivaea.creature.Species;
import dev.ahrivaea.item.VesselItem;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import dev.ahrivaea.party.CaughtCreature;
import dev.ahrivaea.party.Party;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.commands.Commands;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class Ahrivaea implements ModInitializer {
	public static final String MOD_ID = "ahrivaea";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static final ResourceKey<EntityType<?>> CREATURE_KEY = ResourceKey.create(Registries.ENTITY_TYPE, id("creature"));
	public static final EntityType<CreatureEntity> CREATURE = Registry.register(BuiltInRegistries.ENTITY_TYPE, CREATURE_KEY,
			EntityType.Builder.<CreatureEntity>of(CreatureEntity::new, MobCategory.CREATURE).sized(0.7f, 0.8f).clientTrackingRange(10).build(CREATURE_KEY));

	public static final ResourceKey<Item> VESSEL_KEY = ResourceKey.create(Registries.ITEM, id("vessel"));
	public static final Item VESSEL = Registry.register(BuiltInRegistries.ITEM, VESSEL_KEY,
			new VesselItem(new Item.Properties().setId(VESSEL_KEY).stacksTo(64)));

	@Override
	public void onInitialize() {
		Present.register(); // so players' copies of this mod switch on only on servers that have it
		Party.init();
		dev.ahrivaea.creature.Mounts.init();
		dev.ahrivaea.party.PartySync.init();
		dev.ahrivaea.party.Starters.init();
		dev.ahrivaea.item.DropItems.init();
		dev.ahrivaea.base.Bases.init();
		dev.ahrivaea.creature.VanillaMobs.init();
		dev.ahrivaea.character.PlayerCharacter.init();
		dev.ahrivaea.battle.Battles.init();
		ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(id("creatures"), (ResourceManagerReloadListener) Species::reload);
		ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(id("influence_rules"), (ResourceManagerReloadListener) InfluenceRules::reload);
		FabricDefaultAttributeRegistry.register(CREATURE, Animal.createAnimalAttributes()
				.add(Attributes.MAX_HEALTH, 20.0)
				.add(Attributes.MOVEMENT_SPEED, 0.25));

		// wild creatures are put around players by our own spawner (land, water, caves, Nether, End), not Minecraft's
		dev.ahrivaea.creature.WildSpawner.SKIP.add(dev.ahrivaea.battle.Arena.DIMENSION);
		dev.ahrivaea.creature.WildSpawner.init();

		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> output.accept(VESSEL));

		registerCommands();
		LOGGER.info("Ahrivaea loaded");
	}

	private void registerCommands() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dev.ahrivaea.character.PlayerCharacter.registerCommands(dispatcher);
			dev.ahrivaea.battle.Battles.registerCommands(dispatcher);
			dev.ahrivaea.party.Starters.registerCommands(dispatcher);
			dispatcher.register(Commands.literal("party")
					.executes(ctx -> listParty(ctx.getSource().getPlayerOrException(), ctx.getSource()))
					.then(Commands.literal("send").then(Commands.argument("slot", IntegerArgumentType.integer(1, Party.MAX)).executes(ctx -> {
						ServerPlayer player = ctx.getSource().getPlayerOrException();
						int slot = IntegerArgumentType.getInteger(ctx, "slot") - 1;
						if (slot >= Party.get(player).size()) {
							ctx.getSource().sendFailure(Component.literal("There's no creature in that slot."));
							return 0;
						}
						CaughtCreature c = Party.get(player).get(slot);
						Species species = Species.get(c.species()).orElse(null);
						if (species == null) {
							ctx.getSource().sendFailure(Component.literal("That creature's species no longer exists."));
							return 0;
						}
						CreatureEntity e = spawn((ServerLevel) player.level(), species, c.influence(), c.level(), c.health(), player.getX(), player.getY(), player.getZ());
						if (e == null) return 0;
						Party.remove(player, slot);
						e.tame(player);
						e.applyCaught(c);
						ctx.getSource().sendSuccess(() -> Component.literal("Go, " + e.displayName() + "! (sneak + right-click it to call it back"
								+ (e.isRideable() ? (e.hasSaddle() ? "; right-click to ride)" : "; put a saddle on it to ride it)") : ")")), false);
						return 1;
					})))
					.then(Commands.literal("feed").then(Commands.literal("out").then(Commands.argument("entity", IntegerArgumentType.integer(0)).executes(ctx -> {
						// one of your creatures out in the world (the party screen's Feed button on the Out tab)
						ServerPlayer player = ctx.getSource().getPlayerOrException();
						var found = player.level().getEntity(IntegerArgumentType.getInteger(ctx, "entity"));
						if (!(found instanceof CreatureEntity e) || !e.isTame() || !e.isOwnedBy(player) || e.distanceTo(player) > dev.ahrivaea.party.PartySync.OUT_RANGE + 8) {
							ctx.getSource().sendFailure(Component.literal("That creature isn't out with you."));
							return 0;
						}
						if (dev.ahrivaea.creature.Healing.foodPercent(player.getMainHandItem()) <= 0) {
							ctx.getSource().sendFailure(Component.literal("Hold some food in your main hand to feed it."));
							return 0;
						}
						e.eat(player, player.getMainHandItem());
						return 1;
					})))
							.then(Commands.argument("slot", IntegerArgumentType.integer(1, Party.MAX)).executes(ctx -> {
						// the food in your main hand heals a party creature (outside battle)
						ServerPlayer player = ctx.getSource().getPlayerOrException();
						String msg = dev.ahrivaea.creature.Healing.feedParty(player, IntegerArgumentType.getInteger(ctx, "slot") - 1);
						player.sendSystemMessage(Component.literal(msg));
						return 1;
					})))
					.then(Commands.literal("recall")
							.executes(ctx -> {
								// every creature you sent out that's nearby
								ServerPlayer player = ctx.getSource().getPlayerOrException();
								int n = 0;
								for (CreatureEntity e : player.level().getEntitiesOfClass(CreatureEntity.class, player.getBoundingBox().inflate(dev.ahrivaea.party.PartySync.OUT_RANGE),
										e -> e.isTame() && e.isOwnedBy(player) && !e.hasAttached(dev.ahrivaea.base.Bases.WORKER_OF) && !dev.ahrivaea.battle.Battles.inBattle(e))) {
									e.recallTo(player);
									n++;
								}
								if (n == 0) ctx.getSource().sendFailure(Component.literal("None of your creatures are out nearby."));
								return n;
							})
							.then(Commands.argument("entity", IntegerArgumentType.integer(0)).executes(ctx -> {
								// one of them, by its entity number (the party screen's Call back button)
								ServerPlayer player = ctx.getSource().getPlayerOrException();
								var found = player.level().getEntity(IntegerArgumentType.getInteger(ctx, "entity"));
								if (!(found instanceof CreatureEntity e) || !e.isTame() || !e.isOwnedBy(player) || e.hasAttached(dev.ahrivaea.base.Bases.WORKER_OF)
										|| dev.ahrivaea.battle.Battles.inBattle(e) || e.distanceTo(player) > dev.ahrivaea.party.PartySync.OUT_RANGE + 8) {
									ctx.getSource().sendFailure(Component.literal("That creature isn't out with you."));
									return 0;
								}
								e.recallTo(player);
								return 1;
							})))
					.then(Commands.literal("release").then(Commands.argument("slot", IntegerArgumentType.integer(1, Party.MAX)).executes(ctx -> {
						ServerPlayer player = ctx.getSource().getPlayerOrException();
						int slot = IntegerArgumentType.getInteger(ctx, "slot") - 1;
						if (slot >= Party.get(player).size()) {
							ctx.getSource().sendFailure(Component.literal("There's no creature in that slot."));
							return 0;
						}
						CaughtCreature c = Party.remove(player, slot);
						ctx.getSource().sendSuccess(() -> Component.literal("Released " + formName(c) + " into the wild for good."), false);
						return 1;
					})))
					.then(Commands.literal("box")
							.executes(ctx -> {
								ServerPlayer player = ctx.getSource().getPlayerOrException();
								var box = Party.box(player);
								StringBuilder sb = new StringBuilder("Box (" + box.size() + "):");
								for (int i = 0; i < box.size(); i++) sb.append("\n ").append(i + 1).append(". ").append(formName(box.get(i))).append(" Lv.").append(box.get(i).level());
								if (box.isEmpty()) sb.append(" empty. Recruits go here when your party is full.");
								ctx.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
								return box.size();
							})
							.then(Commands.literal("take").then(Commands.argument("number", IntegerArgumentType.integer(1)).executes(ctx -> {
								ServerPlayer player = ctx.getSource().getPlayerOrException();
								int i = IntegerArgumentType.getInteger(ctx, "number") - 1;
								if (i >= Party.box(player).size()) {
									ctx.getSource().sendFailure(Component.literal("There's no creature with that number in your box."));
									return 0;
								}
								if (Party.get(player).size() >= Party.MAX) {
									ctx.getSource().sendFailure(Component.literal("Your party is full; /party box store <slot> first."));
									return 0;
								}
								CaughtCreature c = Party.removeFromBox(player, i);
								Party.add(player, c);
								ctx.getSource().sendSuccess(() -> Component.literal(formName(c) + " joined your party."), false);
								return 1;
							})))
							.then(Commands.literal("store").then(Commands.argument("slot", IntegerArgumentType.integer(1, Party.MAX)).executes(ctx -> {
								ServerPlayer player = ctx.getSource().getPlayerOrException();
								int slot = IntegerArgumentType.getInteger(ctx, "slot") - 1;
								if (slot >= Party.get(player).size()) {
									ctx.getSource().sendFailure(Component.literal("There's no creature in that slot."));
									return 0;
								}
								CaughtCreature c = Party.remove(player, slot);
								Party.addToBox(player, c);
								ctx.getSource().sendSuccess(() -> Component.literal(formName(c) + " went to your box."), false);
								return 1;
							}))))
					.then(Commands.literal("xp")
							.executes(ctx -> {
								int xp = Party.xp(ctx.getSource().getPlayerOrException());
								ctx.getSource().sendSuccess(() -> Component.literal("You have " + xp + " XP to give out. Use /party xp <slot> <amount>."), false);
								return xp;
							})
							.then(Commands.argument("slot", IntegerArgumentType.integer(1, Party.MAX)).then(Commands.argument("amount", IntegerArgumentType.integer(1)).executes(ctx -> {
								ServerPlayer player = ctx.getSource().getPlayerOrException();
								int slot = IntegerArgumentType.getInteger(ctx, "slot") - 1;
								if (slot >= Party.get(player).size()) {
									ctx.getSource().sendFailure(Component.literal("There's no creature in that slot."));
									return 0;
								}
								int before = Party.get(player).get(slot).level();
								int spent = Party.feed(player, slot, IntegerArgumentType.getInteger(ctx, "amount"));
								CaughtCreature c = Party.get(player).get(slot);
								String grew = c.level() > before ? " It grew to level " + c.level() + "!" : "";
								ctx.getSource().sendSuccess(() -> Component.literal("Gave " + spent + " XP to " + formName(c) + " (" + c.xp() + "/" + BattleSettings.xpToNext(c.level()) + ")." + grew), false);
								return spent;
							}))))
			);

			// tools for building and testing (operators only)
			dispatcher.register(Commands.literal("ahrivaea").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
					.then(Commands.literal("spawn").then(Commands.argument("species", StringArgumentType.word())
							.suggests((ctx, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(Species.all().stream().map(Species::id), b))
							.then(Commands.argument("influence", StringArgumentType.word())
									.suggests((ctx, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(java.util.Arrays.stream(Influence.values()).map(Influence::getSerializedName), b))
									.then(Commands.argument("level", IntegerArgumentType.integer(0, Stats.MAX_LEVEL)).executes(ctx -> {
										var species = Species.get(StringArgumentType.getString(ctx, "species"));
										if (species.isEmpty()) {
											ctx.getSource().sendFailure(Component.literal("No such creature."));
											return 0;
										}
										var pos = ctx.getSource().getPosition();
										CreatureEntity e = spawn(ctx.getSource().getLevel(), species.get(), Influence.byName(StringArgumentType.getString(ctx, "influence")),
												IntegerArgumentType.getInteger(ctx, "level"), -1, pos.x, pos.y, pos.z);
										if (e == null) return 0;
										ctx.getSource().sendSuccess(() -> Component.literal("Spawned " + e.displayName() + " Lv." + e.getCreatureLevel()), true);
										return 1;
									})))))
					.then(Commands.literal("givexp").then(Commands.argument("amount", IntegerArgumentType.integer(1)).executes(ctx -> {
						ServerPlayer player = ctx.getSource().getPlayerOrException();
						Party.addXp(player, IntegerArgumentType.getInteger(ctx, "amount"));
						ctx.getSource().sendSuccess(() -> Component.literal("You now have " + Party.xp(player) + " XP."), false);
						return 1;
					})))
					.then(Commands.literal("selftest").executes(ctx -> {
						SelfTest.run(msg -> ctx.getSource().sendSuccess(() -> Component.literal(msg), false));
						return 1;
					})));
		});
	}

	private static String formName(CaughtCreature c) {
		return Species.get(c.species()).map(s -> s.nameFor(c.influence())).orElse(c.species());
	}

	private static int listParty(ServerPlayer player, net.minecraft.commands.CommandSourceStack source) {
		List<CaughtCreature> party = Party.get(player);
		if (party.isEmpty()) {
			source.sendSuccess(() -> Component.literal("Your party is empty. Bind a creature with a Vessel."), false);
			return 0;
		}
		StringBuilder sb = new StringBuilder("Your party (" + Party.xp(player) + " XP to give out):");
		for (int i = 0; i < party.size(); i++) {
			CaughtCreature c = party.get(i);
			var sp = Species.get(c.species());
			sb.append("\n ").append(i + 1).append(". ").append(formName(c)).append(" Lv.").append(c.level())
					.append(" (").append(c.influence().displayName()).append(")  ").append(Math.round(c.health())).append(" HP  ")
					.append(c.xp()).append("/").append(BattleSettings.xpToNext(c.level())).append(" XP");
			sp.ifPresent(s -> {
				Stats st = s.statsFor(c.influence());
				sb.append("\n    HP ").append(st.hp()).append(" Atk ").append(st.attack()).append(" Def ").append(st.defense())
						.append(" Spd ").append(st.speed()).append(" Mag ").append(st.magic()).append(" Lck ").append(st.luck())
						.append(" Cha ").append(st.charisma()).append("  (moves ").append(st.squaresPerTurn()).append(" squares)");
			});
		}
		String text = sb.toString();
		source.sendSuccess(() -> Component.literal(text), false);
		return party.size();
	}

	/** Puts a creature of a given species, influence and level into the world; health below 0 means full. */
	public static CreatureEntity spawn(ServerLevel level, Species species, Influence influence, int lvl, float health, double x, double y, double z) {
		CreatureEntity e = CREATURE.create(level, EntitySpawnReason.MOB_SUMMONED);
		if (e == null) return null;
		e.snapTo(x, y, z);
		e.setup(species, influence, lvl, health);
		level.addFreshEntity(e);
		return e;
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
