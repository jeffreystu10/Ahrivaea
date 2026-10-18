package dev.ahrivaea.guilds;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.TeamColor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Guilds. Each guild is a scoreboard team ("guild_" + its name in lower case) with friendly fire off, so members
 * can't hurt each other (vanilla handles players; this also covers creatures and pets they own), their names carry
 * a [Guild] tag, and /teammsg talks to the guild. Other mods see guilds as ordinary teams: the tower challenges let
 * a tower owner's guild build at their towers. Who leads each guild is saved with the overworld.
 */
public final class Guilds {
	private Guilds() {}

	public static final String PREFIX = "guild_";
	public static final int MAX_NAME = 16;
	/** How long an invitation lasts (ticks): five minutes. */
	public static final int INVITE_TICKS = 5 * 60 * 20;

	/** A guild: its name as typed and its leader's player name. */
	public record Guild(String name, String leader) {
		static final Codec<Guild> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.fieldOf("name").forGetter(Guild::name),
				Codec.STRING.fieldOf("leader").forGetter(Guild::leader)
		).apply(i, Guild::new));
	}

	/** Team name to guild. */
	public static final AttachmentType<Map<String, Guild>> GUILDS = AttachmentRegistry.create(
			Identifier.fromNamespaceAndPath("ahrivaea", "guilds"),
			b -> b.persistent(Codec.unboundedMap(Codec.STRING, Guild.CODEC)).initializer(Map::of));

	/** Player to (team name to the tick the invitation runs out). Not saved. */
	private static final Map<UUID, Map<String, Long>> INVITES = new HashMap<>();

	public static void init() {
		// guild mates' creatures and pets can't hurt them or each other (players themselves: friendly fire is off)
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((victim, source, amount) -> {
			Entity attacker = source.getEntity();
			if (attacker == null || attacker == victim || victim.level().getServer() == null) return true;
			String a = controller(attacker), v = controller(victim);
			if (a == null || v == null || a.equals(v)) return true;
			Scoreboard sb = victim.level().getServer().getScoreboard();
			PlayerTeam ta = sb.getPlayersTeam(a), tv = sb.getPlayersTeam(v);
			return ta == null || tv == null || !isGuild(ta) || !ta.getName().equals(tv.getName());
		});
	}

	/** The player behind an entity: the player itself, or the owner of a tame creature or pet (if loaded). */
	private static String controller(Entity e) {
		if (e instanceof Player p) return p.getScoreboardName();
		if (e instanceof OwnableEntity o) {
			LivingEntity owner = o.getOwner();
			if (owner instanceof Player p) return p.getScoreboardName();
		}
		return null;
	}

	// ---- state ----

	public static boolean isGuild(PlayerTeam team) {
		return team != null && team.getName().startsWith(PREFIX);
	}

	static Map<String, Guild> guilds(MinecraftServer server) {
		return server.overworld().getAttachedOrCreate(GUILDS);
	}

	private static void save(MinecraftServer server, String team, Guild guild) {
		Map<String, Guild> copy = new HashMap<>(guilds(server));
		if (guild == null) copy.remove(team);
		else copy.put(team, guild);
		server.overworld().setAttached(GUILDS, copy);
	}

	/** The guild team a player is in, or null. */
	public static PlayerTeam teamOf(MinecraftServer server, String player) {
		PlayerTeam t = server.getScoreboard().getPlayersTeam(player);
		return isGuild(t) ? t : null;
	}

	/** A guild's saved info; a team made without /guild create (or whose save was lost) is led by its first member. */
	static Guild info(MinecraftServer server, PlayerTeam team) {
		Guild g = guilds(server).get(team.getName());
		if (g != null && team.getPlayers().contains(g.leader())) return g;
		String name = g != null ? g.name() : team.getDisplayName().getString();
		List<String> members = new ArrayList<>(team.getPlayers());
		members.sort(String.CASE_INSENSITIVE_ORDER);
		Guild fixed = new Guild(name, members.isEmpty() ? "" : members.getFirst());
		save(server, team.getName(), fixed);
		return fixed;
	}

	private static final TeamColor[] COLORS = {TeamColor.AQUA, TeamColor.GOLD, TeamColor.GREEN, TeamColor.LIGHT_PURPLE, TeamColor.YELLOW,
			TeamColor.RED, TeamColor.BLUE, TeamColor.DARK_AQUA, TeamColor.DARK_GREEN, TeamColor.DARK_PURPLE};

	private static void style(PlayerTeam team, String name, TeamColor color) {
		team.setDisplayName(Component.literal(name));
		team.setColor(Optional.of(color));
		team.setPlayerPrefix(Component.literal("[" + name + "] ").withStyle(s -> s.withColor(color.textColor())));
		team.setAllowFriendlyFire(false);
		team.setSeeFriendlyInvisibles(true);
	}

	// ---- actions (also used by the tests) ----

	/** Creates a guild led by the player. Returns an error message, or null when done. */
	public static String create(ServerPlayer p, String name) {
		MinecraftServer server = p.level().getServer();
		if (!name.matches("[A-Za-z0-9_]{2," + MAX_NAME + "}")) return "A guild name is 2 to " + MAX_NAME + " letters, digits or _.";
		PlayerTeam current = p.getTeam();
		if (current != null) return "You're already in " + current.getDisplayName().getString() + ". Leave it first (/guild leave).";
		String id = PREFIX + name.toLowerCase(Locale.ROOT);
		if (server.getScoreboard().getPlayerTeam(id) != null) return "There's already a guild called " + name + ".";
		PlayerTeam team = server.getScoreboard().addPlayerTeam(id);
		style(team, name, COLORS[Math.floorMod(name.toLowerCase(Locale.ROOT).hashCode(), COLORS.length)]);
		server.getScoreboard().addPlayerToTeam(p.getScoreboardName(), team);
		save(server, id, new Guild(name, p.getScoreboardName()));
		GuildsMod.LOGGER.info("{} founded the guild {}", p.getScoreboardName(), name);
		server.getPlayerList().broadcastSystemMessage(Component.literal(p.getScoreboardName() + " founded the guild " + name + ".").withStyle(ChatFormatting.AQUA), false);
		return null;
	}

	public static String invite(ServerPlayer leader, ServerPlayer target) {
		MinecraftServer server = leader.level().getServer();
		PlayerTeam team = teamOf(server, leader.getScoreboardName());
		if (team == null) return "You're not in a guild. Make one with /guild create <name>.";
		Guild g = info(server, team);
		if (!g.leader().equals(leader.getScoreboardName())) return "Only " + g.leader() + ", the guild's leader, can invite players.";
		if (target == leader) return "You're already in your guild.";
		if (team.getPlayers().contains(target.getScoreboardName())) return target.getScoreboardName() + " is already in " + g.name() + ".";
		INVITES.computeIfAbsent(target.getUUID(), k -> new HashMap<>()).put(team.getName(), (long) server.getTickCount() + INVITE_TICKS);
		String cmd = "/guild join " + g.name();
		target.sendSystemMessage(Component.literal(leader.getScoreboardName() + " invited you to the guild " + g.name() + ". ").withStyle(ChatFormatting.AQUA)
				.append(Component.literal("[Join]").withStyle(s -> s.withColor(ChatFormatting.GREEN).withBold(true)
						.withClickEvent(new ClickEvent.RunCommand(cmd)).withHoverEvent(new HoverEvent.ShowText(Component.literal(cmd)))))
				.append(Component.literal(" (or type " + cmd + "; the invitation lasts 5 minutes)").withStyle(ChatFormatting.GRAY)));
		leader.sendSystemMessage(Component.literal("Invited " + target.getScoreboardName() + " to " + g.name() + ".").withStyle(ChatFormatting.AQUA));
		return null;
	}

	public static String join(ServerPlayer p, String name) {
		MinecraftServer server = p.level().getServer();
		String id = PREFIX + name.toLowerCase(Locale.ROOT);
		PlayerTeam team = server.getScoreboard().getPlayerTeam(id);
		if (team == null) return "There's no guild called " + name + ".";
		Long until = INVITES.getOrDefault(p.getUUID(), Map.of()).get(id);
		if (until == null || until < server.getTickCount()) return "You need an invitation from " + info(server, team).leader() + " to join " + info(server, team).name() + ".";
		PlayerTeam current = p.getTeam();
		if (current != null) return "You're already in " + current.getDisplayName().getString() + ". Leave it first (/guild leave).";
		INVITES.get(p.getUUID()).remove(id);
		server.getScoreboard().addPlayerToTeam(p.getScoreboardName(), team);
		tellGuild(server, team, Component.literal(p.getScoreboardName() + " joined " + info(server, team).name() + "!").withStyle(ChatFormatting.AQUA));
		return null;
	}

	/** Takes a player out of their guild; the guild gets a new leader if it was theirs, or ends if it's now empty. */
	public static String remove(MinecraftServer server, String player, String why) {
		PlayerTeam team = teamOf(server, player);
		if (team == null) return player + " isn't in a guild.";
		Guild g = info(server, team);
		server.getScoreboard().removePlayerFromTeam(player, team);
		ServerPlayer online = server.getPlayerList().getPlayer(player);
		if (online != null) online.sendSystemMessage(Component.literal(why.isEmpty() ? "You left " + g.name() + "." : why).withStyle(ChatFormatting.YELLOW));
		if (team.getPlayers().isEmpty()) {
			server.getScoreboard().removePlayerTeam(team);
			save(server, team.getName(), null);
			GuildsMod.LOGGER.info("The guild {} ended", g.name());
			return null;
		}
		tellGuild(server, team, Component.literal(player + " left " + g.name() + ".").withStyle(ChatFormatting.YELLOW));
		if (g.leader().equals(player)) {
			List<String> members = new ArrayList<>(team.getPlayers());
			members.sort(String.CASE_INSENSITIVE_ORDER);
			save(server, team.getName(), new Guild(g.name(), members.getFirst()));
			tellGuild(server, team, Component.literal(members.getFirst() + " now leads " + g.name() + ".").withStyle(ChatFormatting.GOLD));
		}
		return null;
	}

	private static void tellGuild(MinecraftServer server, PlayerTeam team, Component msg) {
		for (String name : team.getPlayers()) {
			ServerPlayer p = server.getPlayerList().getPlayer(name);
			if (p != null) p.sendSystemMessage(msg);
		}
	}

	// ---- commands ----

	private static ServerPlayer self(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		return ctx.getSource().getPlayerOrException();
	}

	private static int result(CommandContext<CommandSourceStack> ctx, String error) {
		if (error != null) {
			ctx.getSource().sendFailure(Component.literal(error));
			return 0;
		}
		return 1;
	}

	/** The leader's guild, or null after telling them why not. */
	private static PlayerTeam leading(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer p = self(ctx);
		MinecraftServer server = ctx.getSource().getServer();
		PlayerTeam team = teamOf(server, p.getScoreboardName());
		if (team == null) {
			ctx.getSource().sendFailure(Component.literal("You're not in a guild."));
			return null;
		}
		if (!info(server, team).leader().equals(p.getScoreboardName())) {
			ctx.getSource().sendFailure(Component.literal("Only " + info(server, team).leader() + ", the guild's leader, can do that."));
			return null;
		}
		return team;
	}

	public static void registerCommands(CommandDispatcher<CommandSourceStack> d) {
		com.mojang.brigadier.suggestion.SuggestionProvider<CommandSourceStack> members = (ctx, b) -> {
			PlayerTeam t = ctx.getSource().getPlayer() == null ? null : teamOf(ctx.getSource().getServer(), ctx.getSource().getPlayer().getScoreboardName());
			return SharedSuggestionProvider.suggest(t == null ? List.of() : t.getPlayers(), b);
		};
		com.mojang.brigadier.suggestion.SuggestionProvider<CommandSourceStack> invitedTo = (ctx, b) -> {
			List<String> names = new ArrayList<>();
			if (ctx.getSource().getPlayer() != null) for (String id : INVITES.getOrDefault(ctx.getSource().getPlayer().getUUID(), Map.of()).keySet()) {
				PlayerTeam t = ctx.getSource().getServer().getScoreboard().getPlayerTeam(id);
				if (t != null) names.add(info(ctx.getSource().getServer(), t).name());
			}
			return SharedSuggestionProvider.suggest(names, b);
		};
		d.register(Commands.literal("guild")
				.executes(ctx -> {
					ServerPlayer p = self(ctx);
					MinecraftServer server = ctx.getSource().getServer();
					PlayerTeam team = teamOf(server, p.getScoreboardName());
					if (team == null) {
						ctx.getSource().sendSuccess(() -> Component.literal("You're not in a guild. Guild members can't hurt each other and can build at the towers their members own.\n"
								+ "/guild create <name> to found one, or ask a guild's leader to /guild invite you. /guild list shows every guild.").withStyle(ChatFormatting.AQUA), false);
						return 0;
					}
					Guild g = info(server, team);
					List<String> names = new ArrayList<>(team.getPlayers());
					names.sort(String.CASE_INSENSITIVE_ORDER);
					MutableComponent text = Component.literal(g.name()).withStyle(s -> s.withColor(team.getColor().map(TeamColor::textColor).orElse(null)).withBold(true))
							.append(Component.literal("  " + names.size() + (names.size() == 1 ? " member" : " members")).withStyle(ChatFormatting.GRAY));
					for (String n : names) {
						boolean online = server.getPlayerList().getPlayer(n) != null;
						text.append(Component.literal("\n " + (online ? "● " : "○ ") + n + (n.equals(g.leader()) ? " (leader)" : "")).withStyle(online ? ChatFormatting.WHITE : ChatFormatting.GRAY));
					}
					text.append(Component.literal("\n/teammsg <message> talks to your guild.").withStyle(ChatFormatting.DARK_GRAY));
					ctx.getSource().sendSuccess(() -> text, false);
					return names.size();
				})
				.then(Commands.literal("create").then(Commands.argument("name", StringArgumentType.word())
						.executes(ctx -> result(ctx, create(self(ctx), StringArgumentType.getString(ctx, "name"))))))
				.then(Commands.literal("invite").then(Commands.argument("player", EntityArgument.player())
						.executes(ctx -> result(ctx, invite(self(ctx), EntityArgument.getPlayer(ctx, "player"))))))
				.then(Commands.literal("join").then(Commands.argument("name", StringArgumentType.word()).suggests(invitedTo)
						.executes(ctx -> result(ctx, join(self(ctx), StringArgumentType.getString(ctx, "name"))))))
				.then(Commands.literal("leave").executes(ctx -> result(ctx, remove(ctx.getSource().getServer(), self(ctx).getScoreboardName(), ""))))
				.then(Commands.literal("kick").then(Commands.argument("member", StringArgumentType.word()).suggests(members).executes(ctx -> {
					PlayerTeam team = leading(ctx);
					if (team == null) return 0;
					String who = StringArgumentType.getString(ctx, "member");
					if (who.equals(self(ctx).getScoreboardName())) return result(ctx, "Use /guild leave to leave your own guild.");
					if (!team.getPlayers().contains(who)) return result(ctx, who + " isn't in your guild.");
					return result(ctx, remove(ctx.getSource().getServer(), who, "You were removed from " + info(ctx.getSource().getServer(), team).name() + "."));
				})))
				.then(Commands.literal("leader").then(Commands.argument("member", StringArgumentType.word()).suggests(members).executes(ctx -> {
					PlayerTeam team = leading(ctx);
					if (team == null) return 0;
					String who = StringArgumentType.getString(ctx, "member");
					if (!team.getPlayers().contains(who)) return result(ctx, who + " isn't in your guild.");
					MinecraftServer server = ctx.getSource().getServer();
					Guild g = info(server, team);
					save(server, team.getName(), new Guild(g.name(), who));
					tellGuild(server, team, Component.literal(who + " now leads " + g.name() + ".").withStyle(ChatFormatting.GOLD));
					return 1;
				})))
				.then(Commands.literal("color").then(Commands.argument("color", StringArgumentType.word())
						.suggests((ctx, b) -> SharedSuggestionProvider.suggest(TeamColor.VALUES.stream().map(TeamColor::getSerializedName), b)).executes(ctx -> {
							PlayerTeam team = leading(ctx);
							if (team == null) return 0;
							TeamColor c = TeamColor.byName(StringArgumentType.getString(ctx, "color"));
							if (c == null) return result(ctx, "That's not a color. Try aqua, gold, green, red, blue, yellow, light_purple ...");
							style(team, info(ctx.getSource().getServer(), team).name(), c);
							ctx.getSource().sendSuccess(() -> Component.literal("Guild color changed.").withStyle(s -> s.withColor(c.textColor())), false);
							return 1;
						})))
				.then(Commands.literal("disband").executes(ctx -> {
					PlayerTeam team = leading(ctx);
					if (team == null) return 0;
					MinecraftServer server = ctx.getSource().getServer();
					Guild g = info(server, team);
					tellGuild(server, team, Component.literal(g.name() + " has been disbanded.").withStyle(ChatFormatting.YELLOW));
					server.getScoreboard().removePlayerTeam(team);
					save(server, team.getName(), null);
					GuildsMod.LOGGER.info("{} disbanded the guild {}", self(ctx).getScoreboardName(), g.name());
					return 1;
				}))
				.then(Commands.literal("list").executes(ctx -> {
					MinecraftServer server = ctx.getSource().getServer();
					MutableComponent text = Component.literal("Guilds:").withStyle(ChatFormatting.GOLD);
					int n = 0;
					for (PlayerTeam t : server.getScoreboard().getPlayerTeams()) {
						if (!isGuild(t)) continue;
						Guild g = info(server, t);
						n++;
						text.append(Component.literal("\n " + g.name()).withStyle(s -> s.withColor(t.getColor().map(TeamColor::textColor).orElse(null))))
								.append(Component.literal(" - " + t.getPlayers().size() + " members, led by " + g.leader()).withStyle(ChatFormatting.GRAY));
					}
					if (n == 0) text.append(Component.literal("\n none yet: /guild create <name>").withStyle(ChatFormatting.GRAY));
					ctx.getSource().sendSuccess(() -> text, false);
					return n;
				})));
	}
}
