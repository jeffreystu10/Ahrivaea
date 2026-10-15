package dev.ahrivaea.base.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.CrafterBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Lets Crafting workers set off a Crafter, as a redstone pulse would. */
@Mixin(CrafterBlock.class)
public interface CrafterAccess {
	@Invoker("dispenseFrom")
	void ahrivaea$craft(BlockState state, ServerLevel level, BlockPos pos);
}
