package com.dima16263690d.bleckshlng.fluid.interaction;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import com.dima16263690d.bleckshlng.BleckShlng;
import com.dima16263690d.bleckshlng.fluid.geometry.WaterloggedBlockRegistry;
import com.dima16263690d.bleckshlng.fluid.state.FluidStateBridge;
import com.dima16263690d.bleckshlng.fluid.state.FluidStateMapper;
import com.dima16263690d.bleckshlng.fluid.waterlogging.WaterloggedFluidHandler;

/**
 * Water bucket interaction adapter for legacy waterlogged blocks.
 * All world mutation is performed on the logical server.
 */
@Mod.EventBusSubscriber(modid = BleckShlng.MODID)
public final class FluidBucketHandler {
    private FluidBucketHandler() {
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        EntityPlayer player = event.getEntityPlayer();
        World world = event.getWorld();
        if (player == null || world == null || world.isRemote) {
            return;
        }

        EnumHand hand = event.getHand();
        ItemStack held = player.getHeldItem(hand);
        if (held.isEmpty()) {
            return;
        }

        BlockPos pos = event.getPos();
        if (!world.isBlockLoaded(pos)) {
            return;
        }

        if (held.getItem() == Items.WATER_BUCKET) {
            if (!WaterloggedBlockRegistry.canWaterlog(world.getBlockState(pos).getBlock())) {
                return;
            }

            if (WaterloggedFluidHandler.tryWaterlog(
                    world, pos, world.getBlockState(pos), 0)) {
                replaceFilledBucket(player, hand, held, new ItemStack(Items.BUCKET));
                event.setCanceled(true);
            }
            return;
        }

        if (held.getItem() == Items.BUCKET) {
            FluidStateBridge fluid = FluidStateMapper.fromWorld(
                    world, pos, world.getBlockState(pos));
            if (fluid == null
                    || fluid.getKind() != FluidStateBridge.FluidKind.WATER
                    || !fluid.isSource()
                    || world.getBlockState(pos).getBlock() instanceof net.minecraft.block.BlockLiquid) {
                return;
            }

            if (WaterloggedFluidHandler.pickupWater(
                    world, pos, world.getBlockState(pos))) {
                replaceEmptyBucket(player, hand, held,
                        new ItemStack(Items.WATER_BUCKET));
                event.setCanceled(true);
            }
        }
    }

    private static void replaceFilledBucket(EntityPlayer player, EnumHand hand,
                                            ItemStack held, ItemStack emptyBucket) {
        if (player.capabilities.isCreativeMode) {
            return;
        }
        if (held.getCount() <= 1) {
            player.setHeldItem(hand, emptyBucket);
        } else {
            held.shrink(1);
            if (!player.inventory.addItemStackToInventory(emptyBucket)) {
                player.dropItem(emptyBucket, false);
            }
        }
    }

    private static void replaceEmptyBucket(EntityPlayer player, EnumHand hand,
                                           ItemStack held, ItemStack filledBucket) {
        if (player.capabilities.isCreativeMode) {
            return;
        }
        if (held.getCount() <= 1) {
            player.setHeldItem(hand, filledBucket);
        } else {
            held.shrink(1);
            if (!player.inventory.addItemStackToInventory(filledBucket)) {
                player.dropItem(filledBucket, false);
            }
        }
    }
}
