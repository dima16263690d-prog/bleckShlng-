package com.dima16263690d.bleckshlng.interaction;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemAxe;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.block.state.IBlockState;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Right-click stripping handler. Vanilla axes and modded items using
 * Forge's "axe" tool class are supported without a mod/material whitelist.
 */
@Mod.EventBusSubscriber(modid = "bleckshlng")
public final class AxeStripHandler {
    private AxeStripHandler() {
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) {
            return;
        }

        World world = event.getWorld();
        BlockPos pos = event.getPos();
        IBlockState sourceState = world.getBlockState(pos);
        EntityPlayer player = event.getEntityPlayer();

        if (!isAxe(stack, player, sourceState)) {
            return;
        }

        IBlockState strippedState = StrippingRegistry.getStrippedState(sourceState);
        if (strippedState == null) {
            return;
        }

        // Stop the normal block/item right-click action on both sides.
        event.setCanceled(true);
        event.setCancellationResult(EnumActionResult.SUCCESS);

        if (world.isRemote) {
            return;
        }

        if (world.setBlockState(pos, strippedState, 11)) {
            // 1.12.2 has no dedicated AXE_STRIP sound event; use the
            // closest vanilla wood interaction sound.
            world.playSound(
                    null, pos, SoundEvents.BLOCK_WOOD_BREAK,
                    SoundCategory.BLOCKS, 1.0F, 0.9F);

            if (!player.capabilities.isCreativeMode) {
                // Same one-point durability cost as the 1.13+ vanilla AxeItem.
                stack.damageItem(1, player);
            }
        }
    }

    private static boolean isAxe(
            ItemStack stack, EntityPlayer player, IBlockState blockState) {
        Item item = stack.getItem();

        if (item instanceof ItemAxe) {
            return true;
        }

        // Forge tool classes are the normal cross-mod compatibility contract.
        if (item.getToolClasses(stack).contains("axe")) {
            return true;
        }

        // Also accept tools exposing their axe type through harvest level.
        return item.getHarvestLevel(stack, "axe", player, blockState) >= 0;
    }
}
