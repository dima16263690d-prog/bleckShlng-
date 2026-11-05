package com.dima16263690d.bleckshlng.registry;

import net.minecraft.block.Block;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

/**
 * Item form of stripped logs/wood. Matches the vanilla 1.12 log fuel value.
 */
public final class ItemBlockStrippedWood extends ItemBlock {
    public ItemBlockStrippedWood(Block block) {
        super(block);
    }

    @Override
    public int getItemBurnTime(ItemStack itemStack) {
        return 300;
    }
}
