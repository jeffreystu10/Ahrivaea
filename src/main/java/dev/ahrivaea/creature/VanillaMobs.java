package dev.ahrivaea.creature;

import dev.ahrivaea.Ahrivaea;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.biome.v1.ModificationPhase;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * No vanilla mobs in Ahrivaea: creatures take their place. Natural mob spawning is switched off in every biome, and
 * any vanilla mob that turns up anyway (spawners, structures, villages, eggs, mobs saved in old chunks ...) is
 * replaced by a creature: the matching one where the creature list has it (a cow becomes a Cow), else one that lives
 * in that biome, unless enough creatures are there already. The Ender Dragon counts as defeated the moment it
 * appears, so the End's exit portal and gateways open. Game rule {@code ahrivaea:vanilla_mobs true} turns this off.
 */
public final class VanillaMobs {
	private VanillaMobs() {}

	public static final GameRule<Boolean> ALLOWED = GameRuleBuilder.forBoolean(false).category(GameRuleCategory.SPAWNING)
			.buildAndRegister(Ahrivaea.id("vanilla_mobs"));

	/** Vanilla mobs with a creature of their own in the creature list. */
	public static final Map<String, String> MATCHES = Map.ofEntries(
			Map.entry("cow", "cow"), Map.entry("mooshroom", "cow"), Map.entry("pig", "pig"), Map.entry("sheep", "sheep"), Map.entry("chicken", "chicken"),
			Map.entry("horse", "horse"), Map.entry("donkey", "horse"), Map.entry("mule", "horse"), Map.entry("skeleton_horse", "horse"),
			Map.entry("zombie_horse", "horse"), Map.entry("camel", "camel"), Map.entry("cat", "cat"), Map.entry("ocelot", "cat"), Map.entry("fox", "fox"),
			Map.entry("goat", "goat"), Map.entry("llama", "llama"), Map.entry("trader_llama", "llama"), Map.entry("rabbit", "rabbit"),
			Map.entry("squid", "squid"), Map.entry("glow_squid", "squid"), Map.entry("dolphin", "dolphin"), Map.entry("wolf", "wolf"),
			Map.entry("polar_bear", "bear"), Map.entry("iron_golem", "golem"), Map.entry("snow_golem", "golem"),
			Map.entry("zombie", "zombie"), Map.entry("husk", "zombie"), Map.entry("drowned", "zombie"), Map.entry("zombie_villager", "zombie"),
			Map.entry("skeleton", "skeleton"), Map.entry("stray", "skeleton"), Map.entry("bogged", "skeleton"), Map.entry("wither_skeleton", "skeleton"));

	/** At most this many wild creatures within 8 blocks before a replaced mob is just removed. */
	public static final int CROWD = 3;

	private record Replace(ServerLevel level, BlockPos pos, String mob) {}

	private static final List<Replace> QUEUE = new ArrayList<>();

	public static void init() {
		// no natural spawning of vanilla mobs anywhere (creatures have their own spawner)
		BiomeModifications.create(Ahrivaea.id("no_vanilla_mobs")).add(ModificationPhase.REMOVALS, BiomeSelectors.all(),
				ctx -> ctx.getSpawnSettings().clearSpawns());
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (!(entity instanceof Mob mob) || entity instanceof CreatureEntity || level.getGameRules().get(ALLOWED)) return;
			if (WildSpawner.SKIP.contains(level.dimension())) return; // the battle arenas look after themselves
			if (mob instanceof EnderDragon dragon) {
				var fight = level.getDragonFight();
				if (fight != null) fight.setDragonKilled(dragon);
				Ahrivaea.LOGGER.info("The Ender Dragon appeared and was defeated straight away (no vanilla mobs here)");
			} else {
				String id = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).getPath();
				synchronized (QUEUE) {
					QUEUE.add(new Replace(level, mob.blockPosition(), id));
				}
			}
			mob.discard();
		});
		// replacements are made at the end of the tick, not while the world is loading entities
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			List<Replace> todo;
			synchronized (QUEUE) {
				if (QUEUE.isEmpty()) return;
				todo = new ArrayList<>(QUEUE);
				QUEUE.clear();
			}
			for (Replace r : todo) replace(r);
		});
	}

	private static void replace(Replace r) {
		ServerLevel level = r.level();
		if (!level.isLoaded(r.pos()) || WildSpawner.wildAround(level, r.pos(), 8) >= CROWD) return;
		var biome = level.getBiome(r.pos());
		boolean water = level.getFluidState(r.pos()).is(FluidTags.WATER);
		Species.Pick pick = null;
		String match = MATCHES.get(r.mob());
		if (match != null) {
			var sp = Species.get(match);
			if (sp.isPresent()) {
				var here = Species.pickFor(biome, level, level.getRandom(), (s, inf) -> s == sp.get());
				pick = here.orElse(new Species.Pick(sp.get(), sp.get().forms().keySet().iterator().next()));
			}
		}
		if (pick == null) pick = Species.pickFor(biome, level, level.getRandom(), (s, inf) -> water == (s.movement() == Species.Movement.SWIM)).orElse(null);
		if (pick == null) return;
		int[] range = pick.species().levelRange(pick.influence());
		Ahrivaea.spawn(level, pick.species(), pick.influence(), range[0] + level.getRandom().nextInt(Math.max(1, range[1] - range[0] + 1)), -1,
				r.pos().getX() + 0.5, r.pos().getY(), r.pos().getZ() + 0.5);
	}
}
