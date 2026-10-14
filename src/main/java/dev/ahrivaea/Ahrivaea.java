package dev.ahrivaea;

import com.mojang.brigadier.arguments.IntegerArgumentType;
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
		Party.init();
		ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(id("creatures"), (ResourceManagerReloadListener) Species::reload);
		FabricDefaultAttributeRegistry.register(CREATURE, Animal.createAnimalAttributes()
				.add(Attributes.MAX_HEALTH, 20.0)
				.add(Attributes.MOVEMENT_SPEED, 0.25));

		// one spawn entry for all creatures; which species appears (if any) is decided by each species' biome list
		BiomeModifications.addSpawn(BiomeSelectors.foundInOverworld(), MobCategory.CREATURE, CREATURE, 12, 1, 2);

		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> output.accept(VESSEL));

		registerCommands();
		LOGGER.info("Ahrivaea loaded");
	}

	private void registerCommands() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
				Commands.literal("party")
						.executes(ctx -> {
							ServerPlayer player = ctx.getSource().getPlayerOrException();
							List<CaughtCreature> party = Party.get(player);
							if (party.isEmpty()) {
								ctx.getSource().sendSuccess(() -> Component.literal("Your party is empty. Bind a creature with a Vessel."), false);
								return 0;
							}
							StringBuilder sb = new StringBuilder("Your party:");
							for (int i = 0; i < party.size(); i++) {
								CaughtCreature c = party.get(i);
								var sp = Species.get(c.species());
								sb.append("\n ").append(i + 1).append(". ").append(sp.map(Species::name).orElse(c.species()))
										.append(" Lv.").append(c.level()).append(" (").append(sp.map(Species::typeLabel).orElse("?")).append(")  ")
										.append(Math.round(c.health())).append(" HP");
							}
							String text = sb.toString();
							ctx.getSource().sendSuccess(() -> Component.literal(text), false);
							return party.size();
						})
						.then(Commands.literal("send").then(Commands.argument("slot", IntegerArgumentType.integer(1, Party.MAX)).executes(ctx -> {
							ServerPlayer player = ctx.getSource().getPlayerOrException();
							int slot = IntegerArgumentType.getInteger(ctx, "slot") - 1;
							if (slot >= Party.get(player).size()) {
								ctx.getSource().sendFailure(Component.literal("There's no creature in that slot."));
								return 0;
							}
							CaughtCreature c = Party.remove(player, slot);
							ServerLevel level = (ServerLevel) player.level();
							CreatureEntity e = CREATURE.create(level, EntitySpawnReason.MOB_SUMMONED);
							if (e == null) return 0;
							e.setPos(player.getX(), player.getY(), player.getZ());
							Species species = Species.get(c.species()).orElse(null);
							if (species == null) { Party.add(player, c); ctx.getSource().sendFailure(Component.literal("That creature's species no longer exists.")); return 0; }
							e.setup(species, c.level(), c.health());
							e.tame(player);
							level.addFreshEntity(e);
							ctx.getSource().sendSuccess(() -> Component.literal("Go, " + species.name() + "! (sneak + right-click it to call it back)"), false);
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
							ctx.getSource().sendSuccess(() -> Component.literal("Released " + Species.get(c.species()).map(Species::name).orElse(c.species()) + " into the wild for good."), false);
							return 1;
						})))
		));
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
