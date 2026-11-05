package com.dima16263690d.bleckshlng.registry;

import com.dima16263690d.bleckshlng.registry.ModBlocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.registry.GameRegistry;

/**
 * Furnace input recipes for the stripped wood family.
 */
public final class SmeltingRegistry {
    private static final float CHARCOAL_EXPERIENCE = 0.15F;

    private SmeltingRegistry() {
    }

    public static void register() {
        registerCharcoal(ModBlocks.STRIPPED_OAK_LOG);
        registerCharcoal(ModBlocks.STRIPPED_OAK_WOOD);
        registerCharcoal(ModBlocks.STRIPPED_SPRUCE_LOG);
        registerCharcoal(ModBlocks.STRIPPED_SPRUCE_WOOD);
        registerCharcoal(ModBlocks.STRIPPED_BIRCH_LOG);
        registerCharcoal(ModBlocks.STRIPPED_BIRCH_WOOD);
        registerCharcoal(ModBlocks.STRIPPED_JUNGLE_LOG);
        registerCharcoal(ModBlocks.STRIPPED_JUNGLE_WOOD);
        registerCharcoal(ModBlocks.STRIPPED_ACACIA_LOG);
        registerCharcoal(ModBlocks.STRIPPED_ACACIA_WOOD);
        registerCharcoal(ModBlocks.STRIPPED_DARK_OAK_LOG);
        registerCharcoal(ModBlocks.STRIPPED_DARK_OAK_WOOD);
    }

    private static void registerCharcoal(net.minecraft.block.Block block) {
        GameRegistry.addSmelting(
                new ItemStack(block, 1, 0),
                new ItemStack(Items.COAL, 1, 1),
                CHARCOAL_EXPERIENCE);
    }
}
