package dev.ahrivaea.base.mixin;

import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets Smelting workers keep a furnace burning without fuel and speed it up. */
@Mixin(AbstractFurnaceBlockEntity.class)
public interface FurnaceAccess {
	@Accessor("litTimeRemaining")
	int ahrivaea$litTime();

	@Accessor("litTimeRemaining")
	void ahrivaea$setLitTime(int t);

	@Accessor("litTotalTime")
	void ahrivaea$setLitTotal(int t);

	@Accessor("cookingTimer")
	int ahrivaea$cookTime();

	@Accessor("cookingTimer")
	void ahrivaea$setCookTime(int t);

	@Accessor("cookingTotalTime")
	int ahrivaea$cookTotal();
}
