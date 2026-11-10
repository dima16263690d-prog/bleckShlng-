package com.dima16263690d.bleckshlng.fluid.waterlogging;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.stats.StatList;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import com.dima16263690d.bleckshlng.BleckShlng;
import com.dima16263690d.bleckshlng.fluid.geometry.BlockGeometryAdapter;
import com.dima16263690d.bleckshlng.fluid.geometry.WaterloggedBlockRegistry;
import com.dima16263690d.bleckshlng.fluid.network.ClientFluidStateCache;

/**
 * Backports BucketItem's 1.18.2 LiquidBlockContainer placement rule to 1.12.2.
 *
 * Modern BucketItem targets the clicked waterloggable block itself. Legacy
 * ItemBucket places into the adjacent cell, so this event routes a water bucket
 * directly into the clicked host and lets the regular fluid tick spread it.
 */
@Mod.EventBusSubscriber(modid = BleckShlng.MODID)
public final class WaterloggedBucketEvents {
    private WaterloggedBucketEvents() {
    }

    @SubscribeEvent
    public static void onRightClickWaterloggable(PlayerInteractEvent.RightClickBlock event) {
        if (event == null || event.isCanceled()) {
            return;
        }

        EntityPlayer player = event.getEntityPlayer();
        World world = event.getWorld();
        ItemStack held = event.getItemStack();
        EnumFacing face = event.getFace();
        if (player == null || world == null || held == null
                || held.isEmpty() || held.getItem() != Items.WATER_BUCKET
                || face == null) {
            return;
        }

        BlockPos pos = event.getPos();
        if (pos == null || !world.isBlockLoaded(pos)) {
            return;
        }

        IBlockState host = world.getBlockState(pos);
        if (!canAcceptWater(world, pos, host)) {
            return;
        }

        if (!world.isRemote) {
            if (!world.isBlockModifiable(player, pos)
                    || !player.canPlayerEdit(pos, face, held)
                    || !WaterloggedFluidHandler.tryWaterlog(world, pos, host, 0)) {
                return;
            }

            consumeWaterBucket(player, event.getHand());
            player.addStat(StatList.getObjectUseStats(Items.WATER_BUCKET));
            world.playSound(null, pos, SoundEvents.ITEM_BUCKET_EMPTY,
                    SoundCategory.BLOCKS, 1.0F, 1.0F);
        }

        // Stop legacy ItemBucket from also creating a liquid block beside
        // the clicked host after the waterlogged state has been accepted.
        event.setCancellationResult(EnumActionResult.SUCCESS);
        event.setCanceled(true);
    }

    private static boolean canAcceptWater(World world, BlockPos pos, IBlockState state) {
        if (state == null || !WaterloggedBlockRegistry.canWaterlog(state.getBlock())) {
            return false;
        }

        if (!world.isRemote) {
            return BlockGeometryAdapter.canFluidEnter(
                    world, pos, state, net.minecraft.block.material.Material.WATER,
                    EnumFacing.UP);
        }

        if (state.getBlock() instanceof IFluidWaterloggable) {
            return !((IFluidWaterloggable) state.getBlock()).isWaterlogged(state);
        }

        // ChunkFluidData is server-owned. The client uses the synchronized
        // sidecar cache only for prediction; the server makes the final check.
        return ClientFluidStateCache.get(
                world.provider.getDimension(), pos.toLong()) == null;
    }

    private static void consumeWaterBucket(EntityPlayer player, EnumHand hand) {
        if (player.capabilities.isCreativeMode) {
            return;
        }

        ItemStack held = player.getHeldItem(hand);
        if (held.isEmpty() || held.getItem() != Items.WATER_BUCKET) {
            return;
        }

        ItemStack emptyBucket = new ItemStack(Items.BUCKET);
        if (held.getCount() <= 1) {
            player.setHeldItem(hand, emptyBucket);
        } else {
            held.shrink(1);
            if (!player.inventory.addItemStackToInventory(emptyBucket)) {
                player.dropItem(emptyBucket, false);
            }
        }
    }
}
