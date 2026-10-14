package dev.ahrivaea.stellar;

import com.mojang.serialization.Codec;
import dev.ahrivaea.Ahrivaea;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * What Stellar gear does. Sneaking turns off the pickaxe, shovel, axe and hoe area powers.
 * <ul>
 * <li>Pickaxe, shovel: mine 3×3 (1 deep) across the face you're looking at.</li>
 * <li>Axe: fells a whole natural tree. Never touches player-placed logs, logs in villages or other structures,
 *     or wood without natural leaves.</li>
 * <li>Hoe: tills 3×3; on fully grown crops, harvests 3×3 and replants.</li>
 * <li>Sword: heals you for 20% of the damage it deals.</li>
 * <li>Full armor set: creative-style flight.</li>
 * </ul>
 */
public final class StellarAbilities {
	private StellarAbilities() {}

	public static final int MAX_TREE = 256;

	/** Whether flight currently comes from Stellar armor (so we know to take it away again). */
	public static final AttachmentType<Boolean> FLIGHT = AttachmentRegistry.create(Ahrivaea.id("stellar_flight"),
			b -> b.persistent(Codec.BOOL).copyOnDeath().initializer(() -> false));

	private static boolean busy;

	static void init() {
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer sp)) return;
			boolean placedLog = state.is(BlockTags.LOGS) && PlacedLogs.isPlaced(level, pos);
			if (placedLog) PlacedLogs.unmark(level, pos);
			if (busy || sp.isShiftKeyDown()) return;
			ItemStack tool = sp.getMainHandItem();
			busy = true;
			try {
				if (tool.is(Stellar.PICKAXE) || tool.is(Stellar.SHOVEL)) areaMine(server, sp, pos, state, tool);
				else if (tool.is(Stellar.AXE) && state.is(BlockTags.LOGS) && !placedLog) fellTree(server, sp, pos);
			} finally {
				busy = false;
			}
		});

		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (hand != InteractionHand.MAIN_HAND || !(level instanceof ServerLevel server) || !(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
			if (!sp.getMainHandItem().is(Stellar.HOE) || sp.isShiftKeyDown()) return InteractionResult.PASS;
			return hoe(server, sp, hit.getBlockPos(), hit.getDirection());
		});

		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (taken > 0 && source.getEntity() instanceof Player p && p != entity && p.getMainHandItem().is(Stellar.SWORD)) {
				p.heal(taken * 0.2f);
			}
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 5 != 0) return;
			for (ServerPlayer p : server.getPlayerList().getPlayers()) updateFlight(p);
		});
	}

	// ---- pickaxe and shovel ----

	private static void areaMine(ServerLevel level, ServerPlayer player, BlockPos center, BlockState centerState, ItemStack tool) {
		float centerHardness = centerState.getDestroySpeed(level, center);
		// the plane across the face being mined: looking steeply up or down mines a flat 3x3, otherwise a wall
		Direction.Axis axis = Math.abs(player.getXRot()) > 50 ? Direction.Axis.Y : player.getDirection().getAxis();
		for (int a = -1; a <= 1; a++) {
			for (int b = -1; b <= 1; b++) {
				if (a == 0 && b == 0) continue;
				BlockPos p = switch (axis) {
					case Y -> center.offset(a, 0, b);
					case X -> center.offset(0, a, b);
					case Z -> center.offset(a, b, 0);
				};
				BlockState s = level.getBlockState(p);
				float hardness = s.getDestroySpeed(level, p);
				if (s.isAir() || hardness < 0 || hardness > Math.max(centerHardness * 2.5f, 5f)) continue;
				if (tool.getDestroySpeed(s) <= 1f) continue; // the wrong kind of block for this tool
				if (tool.isEmpty() || !player.getMainHandItem().is(tool.getItem())) return; // tool broke
				player.gameMode.destroyBlock(p);
			}
		}
	}

	// ---- axe ----

	private static void fellTree(ServerLevel level, ServerPlayer player, BlockPos start) {
		if (inStructure(level, start)) return;
		Set<BlockPos> logs = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		queue.add(start);
		Set<BlockPos> seen = new HashSet<>();
		seen.add(start);
		int naturalLeaves = 0;
		while (!queue.isEmpty() && logs.size() < MAX_TREE) {
			BlockPos p = queue.poll();
			for (BlockPos n : BlockPos.betweenClosed(p.offset(-1, -1, -1), p.offset(1, 1, 1))) {
				BlockPos q = n.immutable();
				if (!seen.add(q)) continue;
				BlockState s = level.getBlockState(q);
				if (s.getBlock() instanceof LeavesBlock && s.hasProperty(LeavesBlock.PERSISTENT) && !s.getValue(LeavesBlock.PERSISTENT)) naturalLeaves++;
				if (s.is(BlockTags.LOGS) && q.getY() >= start.getY() - 1 && !PlacedLogs.isPlaced(level, q) && !inStructure(level, q)) {
					logs.add(q);
					queue.add(q);
				}
			}
		}
		// only a real tree: natural leaves must hang off it
		if (naturalLeaves < 3 || logs.isEmpty()) return;
		List<BlockPos> order = new ArrayList<>(logs);
		order.sort(Comparator.comparingInt(BlockPos::getY));
		for (BlockPos p : order) {
			if (!player.getMainHandItem().is(Stellar.AXE)) return; // axe broke
			player.gameMode.destroyBlock(p);
		}
	}

	private static boolean inStructure(ServerLevel level, BlockPos pos) {
		return level.structureManager().getStructureWithPieceAt(pos, holder -> true).isValid();
	}

	// ---- hoe ----

	private static InteractionResult hoe(ServerLevel level, ServerPlayer player, BlockPos center, Direction face) {
		BlockState clicked = level.getBlockState(center);
		ItemStack hoe = player.getMainHandItem();
		if (clicked.getBlock() instanceof CropBlock) {
			int harvested = 0;
			for (BlockPos p : BlockPos.betweenClosed(center.offset(-1, 0, -1), center.offset(1, 0, 1))) {
				BlockState s = level.getBlockState(p);
				if (!(s.getBlock() instanceof CropBlock crop) || !crop.isMaxAge(s)) continue;
				List<ItemStack> drops = new ArrayList<>(Block.getDrops(s, level, p, null, player, hoe));
				// keep one seed back to replant
				var seed = s.getBlock().asItem();
				for (ItemStack d : drops) {
					if (d.is(seed)) {
						d.shrink(1);
						break;
					}
				}
				for (ItemStack d : drops) if (!d.isEmpty()) Block.popResource(level, p, d);
				level.setBlock(p.immutable(), crop.getStateForAge(0), Block.UPDATE_ALL);
				harvested++;
			}
			if (harvested == 0) return InteractionResult.PASS;
			level.playSound(null, center, SoundEvents.CROP_BREAK, SoundSource.BLOCKS, 1f, 1f);
			hoe.hurtAndBreak(1, player, InteractionHand.MAIN_HAND);
			return InteractionResult.SUCCESS;
		}
		if (face == Direction.DOWN) return InteractionResult.PASS;
		int tilled = 0;
		for (BlockPos p : BlockPos.betweenClosed(center.offset(-1, 0, -1), center.offset(1, 0, 1))) {
			BlockState s = level.getBlockState(p);
			if (!level.getBlockState(p.above()).isAir()) continue;
			BlockState to = null;
			if (s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.DIRT) || s.is(Blocks.DIRT_PATH)) to = Blocks.FARMLAND.defaultBlockState();
			else if (s.is(Blocks.COARSE_DIRT)) to = Blocks.DIRT.defaultBlockState();
			if (to == null) continue;
			level.setBlock(p.immutable(), to, Block.UPDATE_ALL);
			tilled++;
		}
		if (tilled == 0) return InteractionResult.PASS;
		level.playSound(null, center, SoundEvents.HOE_TILL.value(), SoundSource.BLOCKS, 1f, 1f);
		hoe.hurtAndBreak(1, player, InteractionHand.MAIN_HAND);
		return InteractionResult.SUCCESS;
	}

	// ---- armor ----

	private static void updateFlight(ServerPlayer p) {
		boolean full = Stellar.wearingFullSet(p);
		boolean ours = p.getAttachedOrCreate(FLIGHT);
		var abilities = p.getAbilities();
		if (full && !abilities.mayfly) {
			abilities.mayfly = true;
			p.setAttached(FLIGHT, true);
			p.onUpdateAbilities();
		} else if (!full && ours) {
			p.setAttached(FLIGHT, false);
			if (!p.isCreative() && !p.isSpectator()) {
				if (abilities.flying) p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 200, 0));
				abilities.mayfly = false;
				abilities.flying = false;
				p.onUpdateAbilities();
			}
		}
	}
}
