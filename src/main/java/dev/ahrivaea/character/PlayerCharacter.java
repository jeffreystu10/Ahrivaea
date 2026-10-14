package dev.ahrivaea.character;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.ahrivaea.Ahrivaea;
import dev.ahrivaea.creature.BattleSettings;
import dev.ahrivaea.creature.Stats;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.List;

/**
 * The player's own battle character: MMO-style levels 1–50 with points to spend on the seven stats.
 * Starts at 30 in each stat; each level gives 6 points (max 100 per stat). XP to the next level is
 * 100 + 25 × level.
 */
public record PlayerCharacter(int level, int xp, int points, Stats stats) {
	public static final int MAX_LEVEL = 50, START_STAT = 30, POINTS_PER_LEVEL = 6;
	public static final List<String> STAT_NAMES = List.of("hp", "attack", "defense", "speed", "magic", "luck", "charisma");

	public static final Codec<PlayerCharacter> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("level").forGetter(PlayerCharacter::level),
			Codec.INT.fieldOf("xp").forGetter(PlayerCharacter::xp),
			Codec.INT.fieldOf("points").forGetter(PlayerCharacter::points),
			Stats.CODEC.fieldOf("stats").forGetter(PlayerCharacter::stats)
	).apply(i, PlayerCharacter::new));

	public static final PlayerCharacter NEW = new PlayerCharacter(1, 0, 0, new Stats(START_STAT, START_STAT, START_STAT, START_STAT, START_STAT, START_STAT, START_STAT));

	public static final AttachmentType<PlayerCharacter> ATTACHMENT = AttachmentRegistry.create(Ahrivaea.id("character"),
			b -> b.persistent(CODEC).copyOnDeath().initializer(() -> NEW));

	public static void init() {
		// touching the class registers the attachment
	}

	public static PlayerCharacter get(Player p) {
		return p.getAttachedOrCreate(ATTACHMENT);
	}

	public static int xpToNext(int level) {
		return 100 + 25 * level;
	}

	/** Battle stats: spent points, plus armor (1.5 Defense per armor point), each capped at 100. */
	public Stats battleStats(Player p) {
		int def = (int) Math.min(100, stats.defense() + Math.round(p.getArmorValue() * 1.5));
		return new Stats(stats.hp(), stats.attack(), def, stats.speed(), stats.magic(), stats.luck(), stats.charisma());
	}

	public int maxHealth() {
		return (int) Math.round(BattleSettings.HP_FLAT + BattleSettings.HP_MULTIPLIER * stats.hp());
	}

	/** Adds XP, levelling up as many times as it pays for. Returns the levels gained. */
	public static int addXp(Player p, int amount) {
		PlayerCharacter c = get(p);
		int level = c.level, xp = c.xp + amount, points = c.points, gained = 0;
		while (level < MAX_LEVEL && xp >= xpToNext(level)) {
			xp -= xpToNext(level);
			level++;
			points += POINTS_PER_LEVEL;
			gained++;
		}
		if (level >= MAX_LEVEL) xp = 0;
		p.setAttached(ATTACHMENT, new PlayerCharacter(level, xp, points, c.stats));
		if (gained > 0 && p instanceof ServerPlayer sp) {
			sp.sendSystemMessage(Component.literal("You reached level " + level + "! You have " + points + " stat points to spend: /character spend <stat> <points>")
					.withStyle(ChatFormatting.GOLD));
		}
		return gained;
	}

	private static int stat(Stats s, String name) {
		return switch (name) {
			case "hp" -> s.hp();
			case "attack" -> s.attack();
			case "defense" -> s.defense();
			case "speed" -> s.speed();
			case "magic" -> s.magic();
			case "luck" -> s.luck();
			default -> s.charisma();
		};
	}

	private static Stats with(Stats s, String name, int v) {
		return new Stats(name.equals("hp") ? v : s.hp(), name.equals("attack") ? v : s.attack(), name.equals("defense") ? v : s.defense(),
				name.equals("speed") ? v : s.speed(), name.equals("magic") ? v : s.magic(), name.equals("luck") ? v : s.luck(),
				name.equals("charisma") ? v : s.charisma());
	}

	public static void registerCommands(CommandDispatcher<CommandSourceStack> d) {
		d.register(Commands.literal("character")
				.executes(ctx -> {
					ServerPlayer p = ctx.getSource().getPlayerOrException();
					PlayerCharacter c = get(p);
					Stats b = c.battleStats(p);
					String text = "Your character: level " + c.level + (c.level < MAX_LEVEL ? " (" + c.xp + "/" + xpToNext(c.level) + " XP)" : "")
							+ ", " + c.points + " points to spend"
							+ "\n HP " + b.hp() + "  Attack " + b.attack() + "  Defense " + b.defense() + (b.defense() != c.stats.defense() ? " (with armor)" : "")
							+ "  Speed " + b.speed() + "  Magic " + b.magic() + "  Luck " + b.luck() + "  Charisma " + b.charisma()
							+ "\n Moves " + b.squaresPerTurn() + " squares per turn in battle, " + c.maxHealth() + " battle HP";
					ctx.getSource().sendSuccess(() -> Component.literal(text), false);
					return c.level;
				})
				.then(Commands.literal("spend").then(Commands.argument("stat", StringArgumentType.word())
						.suggests((ctx, b) -> SharedSuggestionProvider.suggest(STAT_NAMES, b))
						.then(Commands.argument("points", IntegerArgumentType.integer(1)).executes(ctx -> {
							ServerPlayer p = ctx.getSource().getPlayerOrException();
							PlayerCharacter c = get(p);
							String stat = StringArgumentType.getString(ctx, "stat").toLowerCase(java.util.Locale.ROOT);
							if (!STAT_NAMES.contains(stat)) {
								ctx.getSource().sendFailure(Component.literal("Stats: " + String.join(", ", STAT_NAMES)));
								return 0;
							}
							int want = IntegerArgumentType.getInteger(ctx, "points");
							int now = stat(c.stats, stat);
							int spend = Math.min(Math.min(want, c.points), 100 - now);
							if (spend <= 0) {
								ctx.getSource().sendFailure(Component.literal(c.points == 0 ? "You have no points to spend." : stat + " is already 100."));
								return 0;
							}
							p.setAttached(ATTACHMENT, new PlayerCharacter(c.level, c.xp, c.points - spend, with(c.stats, stat, now + spend)));
							ctx.getSource().sendSuccess(() -> Component.literal(stat + " is now " + (now + spend) + " (" + (c.points - spend) + " points left)."), false);
							return spend;
						}))))
				.then(Commands.literal("givexp").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(Commands.argument("amount", IntegerArgumentType.integer(1)).executes(ctx -> {
							addXp(ctx.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(ctx, "amount"));
							return 1;
						}))));
	}
}
