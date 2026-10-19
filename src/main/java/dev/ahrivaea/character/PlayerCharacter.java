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
 * The player's own character: levels 1–50 with points to spend on the same seven stats creatures have. You level
 * yourself up like a party creature, by spending XP from the pool battles fill (the same XP to the next level as a
 * creature, 100 + 25 × level). Starts at 30 in each stat; each level gives 6 points (max 100 per stat). The stats
 * are yours in battle; outside battles Speed makes you faster (2% per point above 30) and HP gives you more hearts
 * (one per 5 points above 30).
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

	/** How much faster you walk (and run, swim ...) per point of Speed above the starting 30. */
	public static final double SPEED_PER_POINT = 0.02;
	private static final net.minecraft.resources.Identifier SPEED_ID = Ahrivaea.id("character_speed");

	public static void init() {
		// Speed outside battles: kept up to date on every player once a second
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 20 != 11) return;
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				applySpeed(p);
				applyHealth(p);
			}
		});
	}

	/** The bonus to movement speed from the Speed stat (0.02 per point above 30). */
	public double speedBonus() {
		return SPEED_PER_POINT * Math.max(0, stats.speed() - START_STAT);
	}

	/** Extra health outside battles: a heart (2 health) per 5 points of HP above 30, up to 14 more hearts at 100. */
	public int extraHealth() {
		return 2 * (Math.max(0, stats.hp() - START_STAT) / 5);
	}

	private static final net.minecraft.resources.Identifier HEALTH_ID = Ahrivaea.id("character_health");

	public static void applyHealth(ServerPlayer p) {
		var attr = p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
		if (attr == null) return;
		double extra = get(p).extraHealth();
		var now = attr.getModifier(HEALTH_ID);
		if (now != null && now.amount() == extra) return;
		attr.removeModifier(HEALTH_ID);
		if (extra > 0) attr.addPermanentModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(HEALTH_ID, extra,
				net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
		if (p.getHealth() > p.getMaxHealth()) p.setHealth(p.getMaxHealth());
	}

	public static void applySpeed(ServerPlayer p) {
		var attr = p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
		if (attr == null) return;
		double bonus = get(p).speedBonus();
		var now = attr.getModifier(SPEED_ID);
		if (now != null && now.amount() == bonus) return;
		attr.removeModifier(SPEED_ID);
		if (bonus > 0) attr.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(SPEED_ID, bonus,
				net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
	}

	/** XP still needed for the next level. */
	public int toNext() {
		return level >= MAX_LEVEL ? 0 : xpToNext(level) - xp;
	}

	/** Spends XP from the pool to go up a level, like levelling a party creature. Returns why not, or null. */
	public static String levelUp(ServerPlayer p) {
		PlayerCharacter c = get(p);
		if (c.level >= MAX_LEVEL) return "You're already level " + MAX_LEVEL + ".";
		int need = c.toNext();
		if (dev.ahrivaea.party.Party.xp(p) < need) return "Level " + (c.level + 1) + " takes " + need + " XP; you have " + dev.ahrivaea.party.Party.xp(p) + ".";
		dev.ahrivaea.party.Party.addXp(p, -need);
		p.setAttached(ATTACHMENT, new PlayerCharacter(c.level + 1, 0, c.points + POINTS_PER_LEVEL, c.stats));
		p.sendSystemMessage(Component.literal("You reached level " + (c.level + 1) + "! " + (c.points + POINTS_PER_LEVEL) + " stat points to spend (party screen, You).")
				.withStyle(ChatFormatting.GOLD));
		return null;
	}

	/** Puts points into a stat (up to 100). Returns why not, or null. */
	public static String spend(ServerPlayer p, String stat, int want) {
		PlayerCharacter c = get(p);
		if (!STAT_NAMES.contains(stat)) return "Stats: " + String.join(", ", STAT_NAMES);
		int now = stat(c.stats, stat);
		int spend = Math.min(Math.min(want, c.points), 100 - now);
		if (spend <= 0) return c.points == 0 ? "You have no points to spend." : stat + " is already 100.";
		p.setAttached(ATTACHMENT, new PlayerCharacter(c.level, c.xp, c.points - spend, with(c.stats, stat, now + spend)));
		if (stat.equals("speed")) applySpeed(p);
		if (stat.equals("hp")) applyHealth(p);
		return null;
	}

	public static PlayerCharacter get(Player p) {
		return p.getAttachedOrCreate(ATTACHMENT);
	}

	public static int xpToNext(int level) {
		return dev.ahrivaea.creature.BattleSettings.xpToNext(level);
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
							+ "\n Moves " + b.squaresPerTurn() + " squares per turn in battle, " + c.maxHealth() + " battle HP, "
							+ Math.round(c.speedBonus() * 100) + "% faster and " + c.extraHealth() / 2 + " extra hearts outside battles"
							+ "\n Level up with XP from your pool: /character levelup (" + c.toNext() + " XP), or on the party screen";
					ctx.getSource().sendSuccess(() -> Component.literal(text), false);
					return c.level;
				})
				.then(Commands.literal("levelup").executes(ctx -> {
					String why = levelUp(ctx.getSource().getPlayerOrException());
					if (why != null) ctx.getSource().sendFailure(Component.literal(why));
					return why == null ? 1 : 0;
				}))
				.then(Commands.literal("spend").then(Commands.argument("stat", StringArgumentType.word())
						.suggests((ctx, b) -> SharedSuggestionProvider.suggest(STAT_NAMES, b))
						.then(Commands.argument("points", IntegerArgumentType.integer(1)).executes(ctx -> {
							ServerPlayer p = ctx.getSource().getPlayerOrException();
							String stat = StringArgumentType.getString(ctx, "stat").toLowerCase(java.util.Locale.ROOT);
							String why = spend(p, stat, IntegerArgumentType.getInteger(ctx, "points"));
							if (why != null) {
								ctx.getSource().sendFailure(Component.literal(why));
								return 0;
							}
							PlayerCharacter c = get(p);
							ctx.getSource().sendSuccess(() -> Component.literal(stat + " is now " + stat(c.stats, stat) + " (" + c.points + " points left)."), false);
							return 1;
						}))))
				.then(Commands.literal("givexp").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(Commands.argument("amount", IntegerArgumentType.integer(1)).executes(ctx -> {
							addXp(ctx.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(ctx, "amount"));
							return 1;
						}))));
	}
}
