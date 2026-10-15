package dev.ahrivaea.horses;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ahrivaea Swift Horses: every horse, donkey, mule and skeleton or zombie horse is 1.5x as fast as vanilla, and can be
 * trained up to 2.8x by feeding (tiers 1-3) and breeding (tiers 4-5).
 */
public class HorsesMod implements ModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger("ahrivaea_horses");

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath("ahrivaea", path);
	}

	@Override
	public void onInitialize() {
		Feeds.init();
		LOGGER.debug("Horse speed data: {}", HorseSpeed.ATTACHMENT.identifier());
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (HorseSpeed.covers(entity)) HorseSpeed.apply((AbstractHorse) entity);
		});
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			ItemStack stack = player.getItemInHand(hand);
			if (!isFeed(stack) || !HorseSpeed.covers(entity)) return InteractionResult.PASS;
			if (level.isClientSide()) return InteractionResult.SUCCESS;
			return feed((ServerPlayer) player, (AbstractHorse) entity, stack);
		});
		CommandRegistrationCallback.EVENT.register((d, registry, env) -> d.register(Commands.literal("horse")
				.executes(ctx -> info(ctx.getSource()))
				.then(Commands.literal("settier").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(Commands.argument("tier", IntegerArgumentType.integer(0, HorseSpeed.MAX_TIER)).executes(ctx -> {
							AbstractHorse h = mount(ctx.getSource().getPlayerOrException());
							if (h == null) {
								ctx.getSource().sendFailure(Component.literal("Ride the horse first."));
								return 0;
							}
							HorseSpeed.set(h, new HorseSpeed(IntegerArgumentType.getInteger(ctx, "tier"), 0));
							return info(ctx.getSource());
						})))));
		LOGGER.info("Ahrivaea Swift Horses loaded");
	}

	static boolean isFeed(ItemStack s) {
		return s.is(Feeds.SWIFT) || s.is(Feeds.GOLDEN) || s.is(Feeds.RACING);
	}

	private static AbstractHorse mount(Player p) {
		return p.getVehicle() instanceof AbstractHorse h && HorseSpeed.covers(h) ? h : null;
	}

	/** Feeding a tamed horse the feed for its next tier; enough of it and the horse goes up a tier. */
	public static InteractionResult feed(ServerPlayer p, AbstractHorse h, ItemStack stack) {
		HorseSpeed s = HorseSpeed.of(h);
		if (!h.isTamed()) {
			p.sendOverlayMessage(Component.literal("Tame it first."));
			return InteractionResult.FAIL;
		}
		if (h instanceof net.minecraft.world.entity.animal.equine.SkeletonHorse) {
			p.sendOverlayMessage(Component.literal("Skeleton horses are always at top speed."));
			return InteractionResult.FAIL;
		}
		if (s.tier() >= HorseSpeed.MAX_FED_TIER) {
			p.sendOverlayMessage(Component.literal(s.tier() >= HorseSpeed.MAX_TIER ? "This horse is as fast as a horse gets."
					: "Feed can't take it past tier " + HorseSpeed.MAX_FED_TIER + ". Breed two tier " + s.tier() + " horses for a chance at tier " + (s.tier() + 1) + "."));
			return InteractionResult.FAIL;
		}
		HorseSpeed.Step step = HorseSpeed.stepFrom(s.tier());
		if (!stack.is(step.feed())) {
			p.sendOverlayMessage(Component.literal("This horse is tier " + s.tier() + "; it needs ").append(step.feed().getDefaultInstance().getHoverName()).append(" next."));
			return InteractionResult.FAIL;
		}
		stack.consume(1, p);
		ServerLevel level = (ServerLevel) h.level();
		int fed = s.fed() + 1;
		if (fed >= step.amount()) {
			HorseSpeed.set(h, new HorseSpeed(s.tier() + 1, 0));
			level.playSound(null, h.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.NEUTRAL, 1f, 1.2f);
			level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, h.getX(), h.getY() + 1.2, h.getZ(), 30, 0.5, 0.6, 0.5, 0.3);
			p.sendOverlayMessage(Component.literal("Speed tier " + (s.tier() + 1) + "! Top speed " + Math.round(HorseSpeed.blocksPerSecond(h)) + " blocks/sec")
					.withStyle(ChatFormatting.GOLD));
		} else {
			HorseSpeed.set(h, new HorseSpeed(s.tier(), fed));
			level.playSound(null, h.blockPosition(), SoundEvents.HORSE_EAT, SoundSource.NEUTRAL, 1f, 1f);
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, h.getX(), h.getY() + 1.2, h.getZ(), 6, 0.4, 0.4, 0.4, 0);
			p.sendOverlayMessage(Component.literal("Tier " + s.tier() + ": " + fed + "/" + step.amount() + " ").append(step.feed().getDefaultInstance().getHoverName()).append(" to tier " + (s.tier() + 1)));
		}
		return InteractionResult.SUCCESS;
	}

	private static int info(CommandSourceStack src) {
		ServerPlayer p = src.getPlayer();
		AbstractHorse h = p == null ? null : mount(p);
		if (h == null) {
			src.sendFailure(Component.literal("Ride a horse, donkey or mule to see its speed."));
			return 0;
		}
		HorseSpeed s = HorseSpeed.of(h);
		HorseSpeed.Step step = HorseSpeed.stepFrom(s.tier());
		Component next = s.tier() >= HorseSpeed.MAX_TIER ? Component.literal("Top tier.")
				: step == null ? Component.literal("Next tier: breed two tier " + s.tier() + " horses (" + Math.round(HorseSpeed.BREED_BONUS_CHANCE * 100) + "% chance per foal).")
				: Component.literal("Next tier: " + s.fed() + "/" + step.amount() + " ").append(step.feed().getDefaultInstance().getHoverName());
		src.sendSuccess(() -> Component.literal(h.getName().getString() + ": speed tier " + s.tier() + " (x" + HorseSpeed.MULTIPLIER[s.tier()] + "), top speed "
				+ String.format("%.1f", HorseSpeed.blocksPerSecond(h)) + " blocks/sec. ").append(next), false);
		return 1;
	}
}
