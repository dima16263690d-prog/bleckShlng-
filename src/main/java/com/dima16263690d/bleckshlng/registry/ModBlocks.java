package com.dima16263690d.bleckshlng.registry;

import com.dima16263690d.bleckshlng.BleckShlng;
import com.dima16263690d.bleckshlng.block.BlockStrippedWood;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Mod.EventBusSubscriber(modid = BleckShlng.MODID)
public final class ModBlocks {
    public static BlockStrippedWood STRIPPED_OAK_LOG;
    public static BlockStrippedWood STRIPPED_OAK_WOOD;
    public static BlockStrippedWood STRIPPED_SPRUCE_LOG;
    public static BlockStrippedWood STRIPPED_SPRUCE_WOOD;
    public static BlockStrippedWood STRIPPED_BIRCH_LOG;
    public static BlockStrippedWood STRIPPED_BIRCH_WOOD;
    public static BlockStrippedWood STRIPPED_JUNGLE_LOG;
    public static BlockStrippedWood STRIPPED_JUNGLE_WOOD;
    public static BlockStrippedWood STRIPPED_ACACIA_LOG;
    public static BlockStrippedWood STRIPPED_ACACIA_WOOD;
    public static BlockStrippedWood STRIPPED_DARK_OAK_LOG;
    public static BlockStrippedWood STRIPPED_DARK_OAK_WOOD;

    private ModBlocks() {
    }

    @SubscribeEvent
    public static void registerBlocks(RegistryEvent.Register<Block> event) {
        STRIPPED_OAK_LOG = register(event, "stripped_oak_log");
        STRIPPED_OAK_WOOD = register(event, "stripped_oak_wood");
        STRIPPED_SPRUCE_LOG = register(event, "stripped_spruce_log");
        STRIPPED_SPRUCE_WOOD = register(event, "stripped_spruce_wood");
        STRIPPED_BIRCH_LOG = register(event, "stripped_birch_log");
        STRIPPED_BIRCH_WOOD = register(event, "stripped_birch_wood");
        STRIPPED_JUNGLE_LOG = register(event, "stripped_jungle_log");
        STRIPPED_JUNGLE_WOOD = register(event, "stripped_jungle_wood");
        STRIPPED_ACACIA_LOG = register(event, "stripped_acacia_log");
        STRIPPED_ACACIA_WOOD = register(event, "stripped_acacia_wood");
        STRIPPED_DARK_OAK_LOG = register(event, "stripped_dark_oak_log");
        STRIPPED_DARK_OAK_WOOD = register(event, "stripped_dark_oak_wood");
    }

    private static BlockStrippedWood register(
            RegistryEvent.Register<Block> event, String name) {
        BlockStrippedWood block = new BlockStrippedWood(name);
        event.getRegistry().register(block);

        // Match vanilla log flammability (fire spread encouragement, flammability).
        Blocks.FIRE.setFireInfo(block, 5, 5);
        return block;
    }
}
