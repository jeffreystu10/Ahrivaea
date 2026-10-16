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
		// workers can only be hurt by raiders (and are knocked out, not killed: see Raids)
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !entity.hasAttached(WORKER_OF)
				|| source.getEntity() != null && Raids.isRaider(source.getEntity()));
		Raids.init();
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
		});
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
		e.setAttached(WORKER_OF, be.getBlockPos());
		return e;
	}

	public static List<BlockPos> cores(ServerLevel level) {
		return new ArrayList<>(CORES.getOrDefault(level.dimension(), Set.of()));
	}
}
