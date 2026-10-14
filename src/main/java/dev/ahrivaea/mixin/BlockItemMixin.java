package dev.ahrivaea.mixin;

import dev.ahrivaea.stellar.PlacedLogs;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Notes logs placed by players, so the Stellar Axe leaves them alone. */
@Mixin(BlockItem.class)
public class BlockItemMixin {
	@Inject(method = "place", at = @At("RETURN"))
	private void ahrivaea$rememberPlacedLog(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> cir) {
		var level = context.getLevel();
		if (level.isClientSide() || context.getPlayer() == null || !cir.getReturnValue().consumesAction()) return;
		var pos = context.getClickedPos();
		if (level.getBlockState(pos).is(BlockTags.LOGS)) PlacedLogs.mark(level, pos);
	}
}
