package dev.ahrivaea.base;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.ahrivaea.Ahrivaea;
import dev.ahrivaea.battle.ArenaReturn;
import dev.ahrivaea.creature.CreatureEntity;
import dev.ahrivaea.creature.Job;
import dev.ahrivaea.creature.Species;
import dev.ahrivaea.creature.WildSpawner;
import dev.ahrivaea.party.CaughtCreature;
import dev.ahrivaea.party.Party;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Bases: the Base Core block, the creatures that work there, and the screen for assigning them (Palworld-style).
 * See docs/bases.md.
 */
public final class Bases {
	private Bases() {}

	public static final Block BASE_CORE = block("base_core", BaseCoreBlock::new,
			BlockBehaviour.Properties.of().strength(4f, 1200f).sound(SoundType.AMETHYST).lightLevel(s -> 10));
	public static final Block MINING_POST = block("mining_post", Block::new,
			BlockBehaviour.Properties.of().strength(2f, 6f).sound(SoundType.WOOD));
	public static final Block CREATURE_DYNAMO = block("creature_dynamo", CreatureDynamoBlock::new,
			BlockBehaviour.Properties.of().strength(3f, 6f).sound(SoundType.COPPER).lightLevel(s -> s.getValue(CreatureDynamoBlock.POWERED) ? 9 : 0));
	public static final BlockEntityType<BaseCoreBlockEntity> BASE_CORE_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
			Ahrivaea.id("base_core"), new BlockEntityType<>(BaseCoreBlockEntity::new, Set.of(BASE_CORE)));

	/** On a worker: the base core it works for. */
	public static final AttachmentType<BlockPos> WORKER_OF = AttachmentRegistry.create(Ahrivaea.id("worker_of"), b -> b.persistent(BlockPos.CODEC));

	private static Block block(String name, Function<BlockBehaviour.Properties, Block> make, BlockBehaviour.Properties props) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, Ahrivaea.id(name));
		Block b = Registry.register(BuiltInRegistries.BLOCK, key, make.apply(props.setId(key)));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, Ahrivaea.id(name));
		Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(b, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
		return b;
	}

	// ---- the screen ----

	public record StatePayload(String json) implements CustomPacketPayload {
		public static final Type<StatePayload> TYPE = new Type<>(Ahrivaea.id("base_state"));
		public static final StreamCodec<RegistryFriendlyByteBuf, StatePayload> CODEC = StreamCodec.composite(ByteBufCodecs.stringUtf8(1 << 20), StatePayload::json, StatePayload::new);

		@Override
		public Type<StatePayload> type() {
			return TYPE;
		}
	}

	public record ActionPayload(String json) implements CustomPacketPayload {
		public static final Type<ActionPayload> TYPE = new Type<>(Ahrivaea.id("base_action"));
		public static final StreamCodec<RegistryFriendlyByteBuf, ActionPayload> CODEC = StreamCodec.composite(ByteBufCodecs.stringUtf8(4096), ActionPayload::json, ActionPayload::new);

		@Override
		public Type<ActionPayload> type() {
			return TYPE;
		}
	}

	// ---- keeping bases loaded ----

	/**
	 * Keeps a base's chunks loaded and running (crops grow, workers work) while its owner is online. Not saved: the
	 * tickets are put back from {@link #LOADERS} after a restart.
	 */
	public static final net.minecraft.server.level.TicketType BASE_TICKET = Registry.register(BuiltInRegistries.TICKET_TYPE, Ahrivaea.id("base"),
			new net.minecraft.server.level.TicketType(net.minecraft.server.level.TicketType.NO_TIMEOUT,
					net.minecraft.server.level.TicketType.FLAG_LOADING | net.minecraft.server.level.TicketType.FLAG_SIMULATION | net.minecraft.server.level.TicketType.FLAG_KEEP_DIMENSION_ACTIVE));

	/** A base that may keep itself loaded: where it is, who owns it, how many chunks out it reaches, and whether it's on. */
	public record Loader(String dimension, BlockPos pos, java.util.UUID owner, int chunks, boolean on) {
		static final com.mojang.serialization.Codec<Loader> CODEC = com.mojang.serialization.codecs.RecordCodecBuilder.create(i -> i.group(
				com.mojang.serialization.Codec.STRING.fieldOf("dimension").forGetter(Loader::dimension),
				BlockPos.CODEC.fieldOf("pos").forGetter(Loader::pos),
				net.minecraft.core.UUIDUtil.CODEC.fieldOf("owner").forGetter(Loader::owner),
				com.mojang.serialization.Codec.INT.fieldOf("chunks").forGetter(Loader::chunks),
				com.mojang.serialization.Codec.BOOL.fieldOf("on").forGetter(Loader::on)
		).apply(i, Loader::new));

		String key() {
			return dimension + "|" + pos.asLong();
		}

		/** The ticket's radius: two more than the base, so every chunk of the base runs entities, not just loads. */
		int ticketRadius() {
			return chunks + 2;
		}
	}

	/** Every owned base, so they can be loaded while their chunks aren't (saved with the overworld). */
	public static final AttachmentType<Map<String, Loader>> LOADERS = AttachmentRegistry.create(Ahrivaea.id("base_loaders"),
			b -> b.persistent(com.mojang.serialization.Codec.unboundedMap(com.mojang.serialization.Codec.STRING, Loader.CODEC)).initializer(Map::of));
	/** Tickets this server has handed out, by loader key. */
	private static final Map<String, Loader> TICKETS = new HashMap<>();

	private static Map<String, Loader> loaders(net.minecraft.server.MinecraftServer server) {
		return server.overworld().getAttachedOrCreate(LOADERS);
	}

	/** Records (or updates) a base in the list of bases that may keep themselves loaded. */
	static void updateLoader(ServerLevel level, BaseCoreBlockEntity be) {
		if (be.owner() == null) return;
		Loader l = new Loader(level.dimension().identifier().toString(), be.getBlockPos().immutable(), be.owner(), be.chunks(), be.chunkLoading());
		Map<String, Loader> all = loaders(level.getServer());
		if (l.equals(all.get(l.key()))) return;
		Map<String, Loader> copy = new HashMap<>(all);
		copy.put(l.key(), l);
		level.getServer().overworld().setAttached(LOADERS, copy);
		refreshTickets(level.getServer());
	}

	static void forgetLoader(ServerLevel level, BlockPos pos) {
		String key = level.dimension().identifier().toString() + "|" + pos.asLong();
		Map<String, Loader> all = loaders(level.getServer());
		if (!all.containsKey(key)) return;
		Map<String, Loader> copy = new HashMap<>(all);
		copy.remove(key);
		level.getServer().overworld().setAttached(LOADERS, copy);
		refreshTickets(level.getServer());
	}

	/** Loads the bases that are switched on and whose owner is online; lets go of the rest. */
	static void refreshTickets(net.minecraft.server.MinecraftServer server) {
		Map<String, Loader> want = new HashMap<>();
		for (Loader l : loaders(server).values())
			if (l.on() && server.getPlayerList().getPlayer(l.owner()) != null) want.put(l.key(), l);
		for (var e : new ArrayList<>(TICKETS.entrySet())) {
			if (e.getValue().equals(want.get(e.getKey()))) continue;
			ServerLevel lv = levelOf(server, e.getValue());
			if (lv != null) lv.getChunkSource().removeTicketWithRadius(BASE_TICKET, net.minecraft.world.level.ChunkPos.containing(e.getValue().pos()), e.getValue().ticketRadius());
			TICKETS.remove(e.getKey());
		}
		for (Loader l : want.values()) {
			if (TICKETS.containsKey(l.key())) continue;
			ServerLevel lv = levelOf(server, l);
			if (lv == null) continue;
			lv.getChunkSource().addTicketWithRadius(BASE_TICKET, net.minecraft.world.level.ChunkPos.containing(l.pos()), l.ticketRadius());
			TICKETS.put(l.key(), l);
		}
	}

	private static ServerLevel levelOf(net.minecraft.server.MinecraftServer server, Loader l) {
		return server.getLevel(ResourceKey.create(Registries.DIMENSION, net.minecraft.resources.Identifier.parse(l.dimension())));
	}

	/** How many of a player's bases are kept loaded right now. */
	public static int loadedBases(ServerPlayer p) {
		String me = p.getUUID().toString();
		return (int) TICKETS.values().stream().filter(l -> l.owner().toString().equals(me)).count();
	}

	// ---- where the bases are ----

	private static final Map<ResourceKey<Level>, Set<BlockPos>> CORES = new HashMap<>();

	static void index(ServerLevel level, BlockPos pos) {
		CORES.computeIfAbsent(level.dimension(), k -> new HashSet<>()).add(pos.immutable());
	}

	static void unindex(ServerLevel level, BlockPos pos) {
		var set = CORES.get(level.dimension());
		if (set != null) set.remove(pos);
	}

	public static BaseCoreBlockEntity coreAt(ServerLevel level, BlockPos pos) {
		return level.isLoaded(pos) && level.getBlockEntity(pos) instanceof BaseCoreBlockEntity be ? be : null;
	}

	/** Whether a spot is in any (loaded) base's chunks, at any height. */
	public static boolean inAnyBaseColumn(ServerLevel level, BlockPos pos) {
		for (BlockPos c : CORES.getOrDefault(level.dimension(), Set.of())) {
			BaseCoreBlockEntity be = coreAt(level, c);
			if (be != null && pos.getX() >= be.minX() && pos.getX() <= be.maxX() && pos.getZ() >= be.minZ() && pos.getZ() <= be.maxZ()) return true;
		}
		return false;
	}

	/** The (loaded) base whose chunks a spot is in, or null. */
	public static BaseCoreBlockEntity baseAt(ServerLevel level, BlockPos pos) {
		for (BlockPos c : CORES.getOrDefault(level.dimension(), Set.of())) {
			BaseCoreBlockEntity be = coreAt(level, c);
			if (be != null && pos.getX() >= be.minX() && pos.getX() <= be.maxX() && pos.getZ() >= be.minZ() && pos.getZ() <= be.maxZ()) return be;
		}
		return null;
	}

	/** Whether a spot is inside any (loaded) base. */
	public static boolean inAnyBase(ServerLevel level, BlockPos pos) {
		for (BlockPos c : CORES.getOrDefault(level.dimension(), Set.of())) {
			BaseCoreBlockEntity be = coreAt(level, c);
			if (be != null && be.contains(pos)) return true;
		}
		return false;
	}

	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(StatePayload.TYPE, StatePayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ActionPayload.TYPE, ActionPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(ActionPayload.TYPE, (payload, ctx) -> handle(ctx.player(), payload.json()));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(out -> {
			out.accept(BASE_CORE);
			out.accept(MINING_POST);
			out.accept(CREATURE_DYNAMO);
		});
		// no wild creatures turn up inside a base
		WildSpawner.BLOCKED.add(Bases::inAnyBase);
		WildSpawner.BLOCKED.add(Bases::inAnyBaseColumn);
		// a broken block stops counting as player-placed
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, be) -> {
			if (level instanceof ServerLevel sl) PlacedBlocks.removed(sl, pos);
		});
		// right-click a Mining Post: its quarry's state, with buttons to pause it and to dig structures
		net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (!(level instanceof ServerLevel sl) || !(player instanceof ServerPlayer sp) || hand != net.minecraft.world.InteractionHand.MAIN_HAND
					|| !sl.getBlockState(hit.getBlockPos()).is(MINING_POST)) return net.minecraft.world.InteractionResult.PASS;
			quarryInfo(sp, hit.getBlockPos());
			return net.minecraft.world.InteractionResult.SUCCESS;
		});
		net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((d, reg, env) -> d.register(
				net.minecraft.commands.Commands.literal("quarry")
						.then(net.minecraft.commands.Commands.argument("post", net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos())
								.then(net.minecraft.commands.Commands.literal("pause").executes(ctx -> quarryCommand(ctx.getSource(), net.minecraft.commands.arguments.coordinates.BlockPosArgument.getBlockPos(ctx, "post"), "pause")))
								.then(net.minecraft.commands.Commands.literal("structures").executes(ctx -> quarryCommand(ctx.getSource(), net.minecraft.commands.arguments.coordinates.BlockPosArgument.getBlockPos(ctx, "post"), "structures")))
								.then(net.minecraft.commands.Commands.literal("restart").executes(ctx -> quarryCommand(ctx.getSource(), net.minecraft.commands.arguments.coordinates.BlockPosArgument.getBlockPos(ctx, "post"), "restart")))
								.executes(ctx -> quarryCommand(ctx.getSource(), net.minecraft.commands.arguments.coordinates.BlockPosArgument.getBlockPos(ctx, "post"), "info")))));
		// workers can only be hurt by raiders (and are knocked out, not killed: see Raids)
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !entity.hasAttached(WORKER_OF)
				|| source.getEntity() != null && Raids.isRaider(source.getEntity()));
		Raids.init();
		// bases follow their owners: loaded while they're online (and the base is switched on)
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 20 == 3) refreshTickets(server);
		});
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server -> TICKETS.clear());
		net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((d, reg, env) -> d.register(
				net.minecraft.commands.Commands.literal("base").requires(net.minecraft.commands.Commands.hasPermission(net.minecraft.commands.Commands.LEVEL_GAMEMASTERS))
						.then(net.minecraft.commands.Commands.literal("raid").executes(ctx -> {
							ServerPlayer p = ctx.getSource().getPlayerOrException();
							for (BlockPos c : cores(p.level())) {
								BaseCoreBlockEntity be = coreAt(p.level(), c);
								if (be != null && be.contains(p.blockPosition())) {
									int n = Raids.start(p.level(), be);
									ctx.getSource().sendSuccess(() -> Component.literal(n > 0 ? "Raid started: " + n + " raiders." : "Couldn't start a raid here."), false);
									return n;
								}
							}
							ctx.getSource().sendFailure(Component.literal("Stand in a base to raid it."));
							return 0;
						}))));
		// a core with workers can't be broken until they're sent away
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, be) -> {
			if (be instanceof BaseCoreBlockEntity core && !core.workers.isEmpty()) {
				player.sendOverlayMessage(Component.literal("Send this base's " + core.workers.size() + " workers away first (right-click the core)."));
				return false;
			}
			return true;
		});
		// a worker body whose base no longer lists it (it was replaced while its chunk was unloaded) is removed
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (!(entity instanceof CreatureEntity c) || !c.hasAttached(WORKER_OF)) return;
			BlockPos core = c.getAttached(WORKER_OF);
			if (!level.isLoaded(core)) return;
			BaseCoreBlockEntity be = coreAt(level, core);
			if (be == null || !be.isWorker(c.getUUID())) c.discard();
			else c.capSize(CreatureEntity.BASE_HITBOX); // workers saved before the cap existed
		});
	}

	// ---- quarries ----

	/** Tells the player about the quarry under a Mining Post, with buttons to change it. */
	public static void quarryInfo(ServerPlayer p, BlockPos post) {
		ServerLevel level = p.level();
		BaseCoreBlockEntity be = baseAt(level, post);
		if (be == null) {
			p.sendSystemMessage(Component.literal("This Mining Post isn't in a base. Put it in your base and give it a chest: your miners will dig out the chunk below it."));
			return;
		}
		if (be.owner() != null && !be.owner().equals(p.getUUID())) {
			p.sendOverlayMessage(Component.literal("This is " + be.ownerName() + "'s quarry."));
			return;
		}
		Quarry q = be.quarry(post);
		String at = post.getX() + " " + post.getY() + " " + post.getZ();
		int chests = Quarry.chests(level, post).size();
		var text = Component.literal("Quarry: ").withStyle(net.minecraft.ChatFormatting.GOLD)
				.append(Component.literal(q.describe(level, post)).withStyle(net.minecraft.ChatFormatting.WHITE))
				.append(Component.literal("\n" + q.mined + " blocks dug; " + chests + (chests == 1 ? " chest" : " chests") + " by the post"
						+ (chests == 0 ? " (miners need one: put a chest or barrel next to the post)" : "")).withStyle(net.minecraft.ChatFormatting.GRAY))
				.append(Component.literal("\nDigs out this chunk below the post, down to bedrock, leaving a wall around it and a staircase. Never strongholds, spawners or blocks players placed.").withStyle(net.minecraft.ChatFormatting.DARK_GRAY))
				.append(Component.literal("\n"))
				.append(button(q.paused ? "[Resume]" : "[Pause]", "/quarry " + at + " pause"))
				.append(Component.literal(" "))
				.append(button(q.structures ? "[Structures: dug]" : "[Structures: kept]", "/quarry " + at + " structures"))
				.append(Component.literal(" "))
				.append(button("[Restart]", "/quarry " + at + " restart"));
		p.sendSystemMessage(text);
	}

	private static net.minecraft.network.chat.MutableComponent button(String label, String cmd) {
		return Component.literal(label).withStyle(s -> s.withColor(net.minecraft.ChatFormatting.AQUA)
				.withClickEvent(new net.minecraft.network.chat.ClickEvent.RunCommand(cmd))
				.withHoverEvent(new net.minecraft.network.chat.HoverEvent.ShowText(Component.literal(cmd))));
	}

	private static int quarryCommand(net.minecraft.commands.CommandSourceStack src, BlockPos post, String what) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer p = src.getPlayerOrException();
		ServerLevel level = p.level();
		BaseCoreBlockEntity be = baseAt(level, post);
		if (be == null || !level.getBlockState(post).is(MINING_POST) || be.owner() != null && !be.owner().equals(p.getUUID()) && !p.isCreative()) {
			src.sendFailure(Component.literal("That isn't a Mining Post in your base."));
			return 0;
		}
		Quarry q = be.quarry(post);
		switch (what) {
			case "pause" -> q.paused = !q.paused;
			case "structures" -> q.structures = !q.structures;
			case "restart" -> {
				q.phase = Quarry.STAIRS;
				q.step = 0;
				q.layer = Integer.MIN_VALUE;
			}
			default -> {}
		}
		be.setChanged();
		quarryInfo(p, post);
		return 1;
	}

	/** Tells a base's owner (now and then) that a quarry has stopped for want of a chest. */
	static void alertNoChest(ServerLevel level, BaseCoreBlockEntity be, BlockPos post, Quarry q) {
		if (level.getGameTime() - q.lastAlert < 2400) return;
		q.lastAlert = level.getGameTime();
		ServerPlayer owner = be.owner() == null ? null : level.getServer().getPlayerList().getPlayer(be.owner());
		if (owner != null) owner.sendSystemMessage(Component.literal("Your quarry at " + post.getX() + ", " + post.getZ()
				+ " has stopped: the chests by its Mining Post are full (or there are none). Put another chest next to the post.")
				.withStyle(net.minecraft.ChatFormatting.YELLOW));
	}

	// ---- assigning workers ----

	static void open(ServerPlayer p, BaseCoreBlockEntity be) {
		if (be.owner() != null && !be.owner().equals(p.getUUID())) {
			p.sendOverlayMessage(Component.literal("This is " + be.ownerName() + "'s base."));
			return;
		}
		send(p, be);
	}

	public static void send(ServerPlayer p, BaseCoreBlockEntity be) {
		ServerPlayNetworking.send(p, new StatePayload(view(p, be).toString()));
	}

	/** The base as the screen shows it. */
	public static JsonObject view(ServerPlayer p, BaseCoreBlockEntity be) {
		JsonObject o = new JsonObject();
		BlockPos c = be.getBlockPos();
		JsonArray pos = new JsonArray();
		pos.add(c.getX());
		pos.add(c.getY());
		pos.add(c.getZ());
		o.add("pos", pos);
		o.addProperty("level", be.baseLevel());
		o.addProperty("radius", be.radius());
		o.addProperty("chunks", 2 * be.chunks() + 1);
		o.addProperty("loading", be.chunkLoading());
		o.addProperty("cap", be.cap());
		var up = BaseCoreBlockEntity.upgradeFrom(be.baseLevel());
		o.addProperty("upgrade", up == null ? "Fully upgraded" : "Next level: right-click the core with " + up.count() + " " + up.item().getDefaultInstance().getHoverName().getString());
		JsonArray workers = new JsonArray();
		ServerLevel level = (ServerLevel) be.getLevel();
		for (Worker w : be.workers) {
			JsonObject j = describe(w.creature());
			String status = "Coming back to the base";
			if (level.getEntity(w.entity()) instanceof CreatureEntity e && e.brain() != null) status = e.brain().status();
			j.addProperty("status", status);
			// its jobs in the order it does them, and which are switched off
			JsonArray jobList = new JsonArray();
			for (Job job : w.ordered(jobsOf(w.creature()))) {
				JsonObject jj = new JsonObject();
				jj.addProperty("id", job.getSerializedName());
				jj.addProperty("name", job.displayName());
				jj.addProperty("desc", job.description());
				jj.addProperty("on", !w.off().contains(job.getSerializedName()));
				jj.addProperty("works", job.working());
				jobList.add(jj);
			}
			j.add("jobList", jobList);
			j.addProperty("speed", (int) Math.round(WorkerBrain.speedAt(w.creature().level()) * 100));
			if (jobsOf(w.creature()).contains(Job.MINING)) {
				j.addProperty("silkLevel", w.creature().level() >= WorkerBrain.SILK_TOUCH_LEVEL);
				j.addProperty("silk", !w.off().contains(Worker.SILK_TOUCH));
			}
			workers.add(j);
		}
		o.add("workers", workers);
		JsonArray party = new JsonArray();
		for (CaughtCreature cc : Party.get(p)) party.add(describe(cc));
		o.add("party", party);
		JsonArray box = new JsonArray();
		for (CaughtCreature cc : Party.box(p)) box.add(describe(cc));
		o.add("box", box);
		return o;
	}

	public static List<Job> jobsOf(CaughtCreature c) {
		return Species.get(c.species()).map(s -> s.jobsFor(c.influence())).orElse(List.of());
	}

	/** Switches one of a worker's jobs on or off, or moves it up or down its list. */
	public static String changeJob(BaseCoreBlockEntity be, int index, String jobId, String kind) {
		if (index < 0 || index >= be.workers.size()) return "No such worker.";
		Worker w = be.workers.get(index);
		List<Job> jobs = jobsOf(w.creature());
		Job job = jobs.stream().filter(j -> j.getSerializedName().equals(jobId)).findFirst().orElse(null);
		if (job == null) return "It doesn't have that job.";
		be.setWorker(index, switch (kind) {
			case "job_toggle" -> w.toggled(job);
			case "job_up" -> w.moved(jobs, job, -1);
			default -> w.moved(jobs, job, 1);
		});
		return null;
	}

	private static JsonObject describe(CaughtCreature c) {
		JsonObject j = new JsonObject();
		var sp = Species.get(c.species());
		j.addProperty("name", sp.map(s -> s.nameFor(c.influence())).orElse(c.species()));
		j.addProperty("level", c.level());
		StringBuilder jobs = new StringBuilder();
		boolean works = false;
		for (Job job : sp.map(s -> s.jobsFor(c.influence())).orElse(List.of())) {
			jobs.append(jobs.isEmpty() ? "" : ", ").append(job.displayName()).append(job.working() ? "" : " (soon)");
			works |= job.working();
		}
		j.addProperty("jobs", jobs.isEmpty() ? "no jobs" : jobs.toString());
		j.addProperty("works", works);
		return j;
	}

	static void handle(ServerPlayer p, String json) {
		JsonObject j = JsonParser.parseString(json).getAsJsonObject();
		JsonArray at = j.getAsJsonArray("pos");
		BlockPos pos = new BlockPos(at.get(0).getAsInt(), at.get(1).getAsInt(), at.get(2).getAsInt());
		ServerLevel level = p.level();
		BaseCoreBlockEntity be = coreAt(level, pos);
		if (be == null || p.distanceToSqr(Vec3.atCenterOf(pos)) > 64 * 64) return;
		if (be.owner() != null && !be.owner().equals(p.getUUID())) return;
		String kind = j.get("kind").getAsString();
		int index = j.has("index") ? j.get("index").getAsInt() : -1;
		String problem = switch (kind) {
			case "assign_party" -> index >= 0 && index < Party.get(p).size() ? assign(p, be, Party.get(p).get(index), () -> Party.remove(p, index)) : "No such creature.";
			case "assign_box" -> index >= 0 && index < Party.box(p).size() ? assign(p, be, Party.box(p).get(index), () -> Party.removeFromBox(p, index)) : "No such creature.";
			case "unassign" -> unassign(p, be, index);
			case "job_toggle", "job_up", "job_down" -> changeJob(be, index, j.has("job") ? j.get("job").getAsString() : "", kind);
			case "silk_toggle" -> {
				if (index < 0 || index >= be.workers.size()) yield "No such worker.";
				be.setWorker(index, be.workers.get(index).toggled(Worker.SILK_TOUCH));
				yield null;
			}
			case "toggle_loading" -> {
				be.setChunkLoading(!be.chunkLoading());
				p.sendSystemMessage(Component.literal(be.chunkLoading()
						? "This base stays loaded while you're online (" + loadedBases(p) + " of your bases are now)."
						: "This base is only loaded when someone is nearby now."));
				yield null;
			}
			default -> null;
		};
		if (problem != null) p.sendOverlayMessage(Component.literal(problem));
		send(p, be);
	}

	/** Puts a creature to work at the base; returns why it can't, or null. */
	public static String assign(ServerPlayer p, BaseCoreBlockEntity be, CaughtCreature c, Runnable takeFromPlayer) {
		if (be.workers.size() >= be.cap()) return "This base is full (" + be.cap() + " workers). Upgrade the core for more.";
		var species = Species.get(c.species());
		if (species.isEmpty()) return "That creature's species no longer exists.";
		CreatureEntity e = spawnWorker((ServerLevel) be.getLevel(), be, c);
		if (e == null) return "There's no room by the core for it.";
		takeFromPlayer.run();
		be.workers.add(new Worker(c, e.getUUID()));
		be.setChanged();
		boolean works = species.get().jobsFor(c.influence()).stream().anyMatch(Job::working);
		p.sendSystemMessage(Component.literal(e.displayName() + " is working at your base" + (works ? "." : ", but its only job (riding) isn't a base job yet.")));
		return null;
	}

	static String unassign(ServerPlayer p, BaseCoreBlockEntity be, int index) {
		if (index < 0 || index >= be.workers.size()) return "No such worker.";
		Worker w = be.workers.remove(index);
		CaughtCreature c = w.creature();
		if (((ServerLevel) be.getLevel()).getEntity(w.entity()) instanceof CreatureEntity e) {
			c = e.toCaught();
			e.discard();
		}
		be.release(w.entity());
		be.setChanged();
		boolean party = Party.addAnywhere(p, c);
		String name = Species.get(c.species()).map(s -> s.nameFor(w.creature().influence())).orElse(c.species());
		p.sendSystemMessage(Component.literal(name + (party ? " rejoined your party." : " went to your box.")));
		return null;
	}

	/** Makes a worker's body next to the core. */
	static CreatureEntity spawnWorker(ServerLevel level, BaseCoreBlockEntity be, CaughtCreature c) {
		var species = Species.get(c.species());
		if (species.isEmpty()) return null;
		Vec3 at = ArenaReturn.safeSpot(level, Vec3.atBottomCenterOf(be.getBlockPos().above()).add(1.5, 0, 0));
		if (at == null) return null;
		CreatureEntity e = Ahrivaea.spawn(level, species.get(), c.influence(), c.level(), c.health(), at.x, at.y, at.z);
		if (e == null) return null;
		if (be.owner() != null) {
			e.setTame(true, false);
			e.setOwnerReference(EntityReference.of(be.owner()));
		}
		e.setPersistenceRequired();
		e.applyCaught(c);
		e.setAttached(WORKER_OF, be.getBlockPos());
		e.capSize(CreatureEntity.BASE_HITBOX);
		return e;
	}

	public static List<BlockPos> cores(ServerLevel level) {
		return new ArrayList<>(CORES.getOrDefault(level.dimension(), Set.of()));
	}
}
