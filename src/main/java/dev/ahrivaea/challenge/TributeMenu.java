package dev.ahrivaea.challenge;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The offering chest's screen: an ordinary-looking chest. When the player closes it, accepted items are
 * counted toward the challenge and everything else goes back to them.
 */
public class TributeMenu extends ChestMenu {
	private final String challengeId;
	private final SimpleContainer offering;

	public TributeMenu(int id, Inventory inventory, SimpleContainer offering, String challengeId) {
		super(MenuType.GENERIC_9x3, id, inventory, offering, 3);
		this.offering = offering;
		this.challengeId = challengeId;
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		if (!(player instanceof ServerPlayer sp)) return;
		List<ItemStack> stacks = new ArrayList<>();
		for (int i = 0; i < offering.getContainerSize(); i++) {
			ItemStack s = offering.removeItemNoUpdate(i);
			if (!s.isEmpty()) stacks.add(s);
		}
		if (!stacks.isEmpty()) Challenges.offer(sp, challengeId, stacks);
	}
}
