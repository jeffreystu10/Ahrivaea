package dev.pokecraft;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import dev.pokecraft.creature.CreatureEntity;
import dev.pokecraft.creature.Species;
import dev.pokecraft.item.CaptureBallItem;
import dev.pokecraft.party.CaughtCreature;
import dev.pokecraft.party.Party;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
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

public class PokeCraft implements ModInitializer {
	public static final String MOD_ID = "pokecraft";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static final ResourceKey<EntityType<?>> CREATURE_KEY = ResourceKey.create(Registries.ENTITY_TYPE, id("creature"));
	public static final EntityType<CreatureEntity> CREATURE = Registry.register(BuiltInRegistries.ENTITY_TYPE, CREATURE_KEY,
			EntityType.Builder.<CreatureEntity>of(CreatureEntity::new, MobCategory.CREATURE).sized(0.7f, 0.8f).clientTrackingRange(10).build(CREATURE_KEY));

	public static final ResourceKey<Item> CAPTURE_BALL_KEY = ResourceKey.create(Registries.ITEM, id("capture_ball"));
	public static final Item CAPTURE_BALL = Registry.register(BuiltInRegistries.ITEM, CAPTURE_BALL_KEY,
			new CaptureBallItem(new Item.Properties().setId(CAPTURE_BALL_KEY).stacksTo(64)));

	@Override
	public void onInitialize() {
		Party.init();
		FabricDefaultAttributeRegistry.register(CREATURE, Animal.createAnimalAttributes()
				.add(Attributes.MAX_HEALTH, 20.0)
				.add(Attributes.MOVEMENT_SPEED, 0.25));

		// wild creatures appear in every overworld biome for now; per-species spawn rules come later
		BiomeModifications.addSpawn(BiomeSelectors.foundInOverworld(), MobCategory.CREATURE, CREATURE, 12, 1, 2);

		ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(entries -> entries.accept(CAPTURE_BALL));

		registerCommands();
		LOGGER.info("PokeCraft loaded with {} species", Species.all().size());
	}

	private void registerCommands() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
				Commands.literal("party")
						.executes(ctx -> {
							ServerPlayer player = ctx.getSource().getPlayerOrException();
							List<CaughtCreature> party = Party.get(player);
							if (party.isEmpty()) {
								ctx.getSource().sendSuccess(() -> Component.literal("Your party is empty. Catch a creature with a Capture Ball."), false);
								return 0;
							}
							StringBuilder sb = new StringBuilder("Your party:");
							for (int i = 0; i < party.size(); i++) {
								CaughtCreature c = party.get(i);
								sb.append("\n ").append(i + 1).append(". ").append(Species.get(c.species()).displayName())
										.append(" Lv.").append(c.level()).append("  ").append(Math.round(c.health())).append(" HP");
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
							e.setup(Species.get(c.species()), c.level(), c.health());
							e.tame(player);
							level.addFreshEntity(e);
							ctx.getSource().sendSuccess(() -> Component.literal("Go, " + Species.get(c.species()).displayName() + "! (sneak + right-click it to call it back)"), false);
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
							ctx.getSource().sendSuccess(() -> Component.literal("Released " + Species.get(c.species()).displayName() + " into the wild for good."), false);
							return 1;
						})))
		));
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
