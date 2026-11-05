package com.dima16263690d.bleckshlng.registry;

import com.dima16263690d.bleckshlng.BleckShlng;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.oredict.OreDictionary;

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
    }

    private static void register(RegistryEvent.Register<Item> event, Block block) {
        ItemBlockStrippedWood itemBlock = new ItemBlockStrippedWood(block);
        itemBlock.setRegistryName(block.getRegistryName());
        event.getRegistry().register(itemBlock);

        // Forge 1.12.2 equivalent of the vanilla 1.13+ "logs" item tag.
        // The wildcard metadata keeps this compatible with any pillar axis metadata.
        OreDictionary.registerOre(
                "logWood",
                new ItemStack(itemBlock, 1, OreDictionary.WILDCARD_VALUE));
    }
}
