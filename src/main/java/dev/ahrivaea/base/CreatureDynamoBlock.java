package dev.ahrivaea.base;

import dev.ahrivaea.creature.CreatureEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.AABB;

/**
 * A redstone power source that a Redstone worker keeps running: full power (15) on every side while one is working
 * it, off a few seconds after it leaves.
 */
public class CreatureDynamoBlock extends Block {
	public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

	public CreatureDynamoBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(POWERED, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(POWERED);
	}

	@Override
	protected boolean isSignalSource(BlockState state) {
		return true;
	}

	@Override
	protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
		return state.getValue(POWERED) ? 15 : 0;
	}

	/** Called by the worker every so often while it works the dynamo. */
	public static void power(ServerLevel level, BlockPos pos) {
		BlockState s = level.getBlockState(pos);
		if (!(s.getBlock() instanceof CreatureDynamoBlock)) return;
		if (!s.getValue(POWERED)) level.setBlock(pos, s.setValue(POWERED, true), Block.UPDATE_ALL);
		level.scheduleTick(pos, s.getBlock(), 40);
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (!state.getValue(POWERED)) return;
		boolean worked = !level.getEntitiesOfClass(CreatureEntity.class, new AABB(pos).inflate(3),
				c -> c.brain() != null && pos.equals(c.brain().powering())).isEmpty();
		if (worked) level.scheduleTick(pos, this, 40);
		else level.setBlock(pos, state.setValue(POWERED, false), Block.UPDATE_ALL);
	}
}
