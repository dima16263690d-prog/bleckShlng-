package com.dima16263690d.bleckshlng.registry;

import com.dima16263690d.bleckshlng.BleckShlng;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;

import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Mod.EventBusSubscriber(modid = BleckShlng.MODID)
public final class ModItems {
    private ModItems() {
    }

    @SubscribeEvent
    public static void registerItems(RegistryEvent.Register<Item> event) {
        register(event, ModBlocks.STRIPPED_OAK_LOG);
        register(event, ModBlocks.STRIPPED_OAK_WOOD);
        register(event, ModBlocks.STRIPPED_SPRUCE_LOG);
        register(event, ModBlocks.STRIPPED_SPRUCE_WOOD);
        register(event, ModBlocks.STRIPPED_BIRCH_LOG);
        register(event, ModBlocks.STRIPPED_BIRCH_WOOD);
        register(event, ModBlocks.STRIPPED_JUNGLE_LOG);
        register(event, ModBlocks.STRIPPED_JUNGLE_WOOD);
        register(event, ModBlocks.STRIPPED_ACACIA_LOG);
        register(event, ModBlocks.STRIPPED_ACACIA_WOOD);
        register(event, ModBlocks.STRIPPED_DARK_OAK_LOG);
        register(event, ModBlocks.STRIPPED_DARK_OAK_WOOD);
        ItemBlock chainItem = new ItemBlock(ModBlocks.CHAIN);
        chainItem.setRegistryName(ModBlocks.CHAIN.getRegistryName());
        event.getRegistry().register(chainItem);

        ItemBlock candleItem = new ItemBlock(ModBlocks.CANDLE);
        candleItem.setRegistryName(ModBlocks.CANDLE.getRegistryName());
        event.getRegistry().register(candleItem);

        ItemBlock lanternItem = new ItemBlock(ModBlocks.LANTERN);
        lanternItem.setRegistryName(ModBlocks.LANTERN.getRegistryName());
        event.getRegistry().register(lanternItem);

        ItemBlock campfireItem = new ItemBlock(ModBlocks.CAMPFIRE);
        campfireItem.setRegistryName(ModBlocks.CAMPFIRE.getRegistryName());
        event.getRegistry().register(campfireItem);
    }

    private static void register(RegistryEvent.Register<Item> event, Block block) {
        ItemBlockStrippedWood itemBlock = new ItemBlockStrippedWood(block);
        itemBlock.setRegistryName(block.getRegistryName());
        event.getRegistry().register(itemBlock);
    }
}
