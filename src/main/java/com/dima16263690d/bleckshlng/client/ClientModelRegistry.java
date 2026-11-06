package com.dima16263690d.bleckshlng.client;

import com.dima16263690d.bleckshlng.BleckShlng;
import com.dima16263690d.bleckshlng.registry.ModBlocks;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

@Mod.EventBusSubscriber(modid = BleckShlng.MODID, value = Side.CLIENT)
public final class ClientModelRegistry {
    private ClientModelRegistry() {
    }

    @SubscribeEvent
    public static void registerModels(ModelRegistryEvent event) {
        register(ModBlocks.STRIPPED_OAK_LOG);
        register(ModBlocks.STRIPPED_OAK_WOOD);
        register(ModBlocks.STRIPPED_SPRUCE_LOG);
        register(ModBlocks.STRIPPED_SPRUCE_WOOD);
        register(ModBlocks.STRIPPED_BIRCH_LOG);
        register(ModBlocks.STRIPPED_BIRCH_WOOD);
        register(ModBlocks.STRIPPED_JUNGLE_LOG);
        register(ModBlocks.STRIPPED_JUNGLE_WOOD);
        register(ModBlocks.STRIPPED_ACACIA_LOG);
        register(ModBlocks.STRIPPED_ACACIA_WOOD);
        register(ModBlocks.STRIPPED_DARK_OAK_LOG);
        register(ModBlocks.STRIPPED_DARK_OAK_WOOD);
        register(ModBlocks.CHAIN);
    }

    private static void register(Block block) {
        Item item = Item.getItemFromBlock(block);
        ModelLoader.setCustomModelResourceLocation(
                item, 0, new ModelResourceLocation(block.getRegistryName(), "inventory"));
    }
}
