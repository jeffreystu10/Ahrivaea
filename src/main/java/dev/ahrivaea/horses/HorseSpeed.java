package dev.ahrivaea.horses;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.equine.Donkey;
import net.minecraft.world.entity.animal.equine.Horse;
import net.minecraft.world.entity.animal.equine.Mule;
import net.minecraft.world.entity.animal.equine.SkeletonHorse;
import net.minecraft.world.entity.animal.equine.ZombieHorse;
import net.minecraft.world.item.Item;
import net.minecraft.util.RandomSource;

/**
 * A horse's speed tier (0-5) and how far it is through its feeding towards the next one. Saved with the horse.
 * Every tier multiplies the horse's own (inherited) speed, so good bloodlines still matter.
 */
public record HorseSpeed(int tier, int fed) {
	public static final Codec<HorseSpeed> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("tier").forGetter(HorseSpeed::tier),
			Codec.INT.fieldOf("fed").forGetter(HorseSpeed::fed)
	).apply(i, HorseSpeed::new));

	public static final AttachmentType<HorseSpeed> ATTACHMENT = AttachmentRegistry.create(HorsesMod.id("horse_speed"),
			b -> b.persistent(CODEC).initializer(() -> new HorseSpeed(0, 0)));

	public static final HorseSpeed START = new HorseSpeed(0, 0);

	/** Speed multiplier for each tier, applied to the horse's own speed. Tier 0 is every horse, untrained. */
	public static final double[] MULTIPLIER = {1.5, 1.76, 2.02, 2.28, 2.54, 2.8};
	public static final int MAX_TIER = MULTIPLIER.length - 1;
	/** Feeding raises a horse up to this tier; the tiers above come only from breeding. */
	public static final int MAX_FED_TIER = 3;
	/** Chance a foal of two parents on the same tier is born one tier higher. */
	public static final double BREED_BONUS_CHANCE = 0.3;
	/** Movement speed attribute to blocks per second for a ridden horse. */
	public static final double BLOCKS_PER_SECOND = 42.16;

	public record Step(Item feed, int amount) {}

	/** What it takes to go from tier n to n + 1 by feeding. */
	public static Step stepFrom(int tier) {
		return switch (tier) {
			case 0 -> new Step(Feeds.SWIFT, 8);
			case 1 -> new Step(Feeds.GOLDEN, 8);
			case 2 -> new Step(Feeds.RACING, 4);
			default -> null;
		};
	}

	/** Horses, donkeys, mules and skeleton and zombie horses (not llamas or camels). */
	public static boolean covers(Entity e) {
		return e instanceof Horse || e instanceof Donkey || e instanceof Mule || e instanceof SkeletonHorse || e instanceof ZombieHorse;
	}

	public static HorseSpeed of(AbstractHorse h) {
		return h.getAttachedOrElse(ATTACHMENT, START);
	}

	public static void set(AbstractHorse h, HorseSpeed s) {
		h.setAttached(ATTACHMENT, s);
		apply(h);
	}

	/** Puts the tier's speed bonus on the horse (done whenever it loads or changes tier). */
	public static void apply(AbstractHorse h) {
		AttributeInstance speed = h.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed == null) return;
		int tier = Math.clamp(of(h).tier(), 0, MAX_TIER);
		speed.addOrUpdateTransientModifier(new AttributeModifier(HorsesMod.id("speed_tier"), MULTIPLIER[tier] - 1, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
	}

	/** Top speed in blocks per second, as ridden. */
	public static double blocksPerSecond(AbstractHorse h) {
		return h.getAttributeValue(Attributes.MOVEMENT_SPEED) * BLOCKS_PER_SECOND;
	}

	/** A foal takes the better parent's tier; if both parents are on the same tier it may be born one higher. */
	public static int foalTier(int a, int b, RandomSource random) {
		int t = Math.max(a, b);
		if (a == b && t < MAX_TIER && random.nextDouble() < BREED_BONUS_CHANCE) t++;
		return t;
	}
}
