package com.dima16263690d.bleckshlng.fluid.interaction;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionUtils;
import net.minecraft.init.PotionTypes;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fluids.FluidActionResult;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.IFluidBlock;
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
        if (player == null || world == null) {
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

        IBlockState clickedState = world.getBlockState(pos);
        if (world.isRemote) {
            FluidStateBridge clientFluid = FluidStateMapper.fromWorld(
                    world, pos, clickedState);
            boolean waterSource = clientFluid != null
                    && clientFluid.getKind() == FluidStateBridge.FluidKind.WATER
                    && clientFluid.isSource();

            // Suppress vanilla client prediction for waterlogging and for
            // bucket/bottle interactions with fluids stored outside BlockLiquid.
            if (held.getItem() == Items.WATER_BUCKET && clientFluid == null) {
                boolean host = WaterloggedBlockRegistry.canWaterlog(clickedState.getBlock());
                boolean alongFace = false;
                if (event.getFace() != null) {
                    BlockPos placePos = pos.offset(event.getFace());
                    if (world.isBlockLoaded(placePos)) {
                        alongFace = WaterloggedBlockRegistry.canWaterlog(
                                world.getBlockState(placePos).getBlock());
                    }
                }
                if (host || alongFace) {
                    event.setCanceled(true);
                    event.setCancellationResult(net.minecraft.util.EnumActionResult.SUCCESS);
                }
            } else if (waterSource
                    && held.getItem() == Items.GLASS_BOTTLE) {
                event.setCanceled(true);
                event.setCancellationResult(net.minecraft.util.EnumActionResult.SUCCESS);
            } else if (waterSource && held.getItem() == Items.BUCKET
                    && !(clickedState.getBlock() instanceof net.minecraft.block.BlockLiquid)) {
                event.setCanceled(true);
                event.setCancellationResult(net.minecraft.util.EnumActionResult.SUCCESS);
            }
            return;
        }

        // Java Edition 1.13 water buckets waterlog the clicked host block
        // before any adjacent-block placement logic is attempted. Keep this
        // ahead of FluidUtil so a generic Forge handler cannot consume the
        // interaction before the vanilla Update Aquatic behavior runs.
        if (held.getItem() == Items.WATER_BUCKET) {
            if (WaterloggedBlockRegistry.canWaterlog(clickedState.getBlock())
                    && WaterloggedFluidHandler.tryWaterlog(world, pos, clickedState, 0)) {
                world.playSound(null, pos, net.minecraft.init.SoundEvents.ITEM_BUCKET_EMPTY,
                        net.minecraft.util.SoundCategory.BLOCKS, 1.0F, 1.0F);
                replaceFilledBucket(player, hand, held, new ItemStack(Items.BUCKET));
                event.setCanceled(true);
                return;
            }

            // Click on the top face of a bottom slab (or similar) may resolve
            // to the solid host while the intended waterlog target is the
            // cell along the clicked face. Try that cell as a second chance.
            if (event.getFace() != null) {
                BlockPos placePos = pos.offset(event.getFace());
                if (world.isBlockLoaded(placePos)) {
                    IBlockState placeState = world.getBlockState(placePos);
                    if (WaterloggedBlockRegistry.canWaterlog(placeState.getBlock())
                            && WaterloggedFluidHandler.tryWaterlog(
                                    world, placePos, placeState, 0)) {
                        world.playSound(null, placePos,
                                net.minecraft.init.SoundEvents.ITEM_BUCKET_EMPTY,
                                net.minecraft.util.SoundCategory.BLOCKS, 1.0F, 1.0F);
                        replaceFilledBucket(player, hand, held, new ItemStack(Items.BUCKET));
                        event.setCanceled(true);
                        return;
                    }
                }
            }
        }

        // Prefer the official Forge capability/API path for modded tanks and
        // container items after the vanilla waterlogging target was checked.
        if (FluidUtil.interactWithFluidHandler(
                player, hand, world, pos, event.getFace())) {
            event.setCanceled(true);
            return;
        }

        // Java Edition 1.13 waterlogged states are recognized as water by
        // glass bottles as well as buckets. Bottles take a sample and do not
        // remove the stored source from the host block.
        if (held.getItem() == Items.GLASS_BOTTLE) {
            FluidStateBridge sampledWater = FluidStateMapper.fromWorld(
                    world, pos, clickedState);
            if (sampledWater != null
                    && sampledWater.getKind() == FluidStateBridge.FluidKind.WATER
                    && sampledWater.isSource()) {
                ItemStack waterBottle = PotionUtils.addPotionToItemStack(
                        new ItemStack(Items.POTIONITEM), PotionTypes.WATER);
                if (player.capabilities.isCreativeMode) {
                    if (!player.inventory.addItemStackToInventory(waterBottle)) {
                        player.dropItem(waterBottle, false);
                    }
                } else if (held.getCount() <= 1) {
                    player.setHeldItem(hand, waterBottle);
                } else {
                    held.shrink(1);
                    if (!player.inventory.addItemStackToInventory(waterBottle)) {
                        player.dropItem(waterBottle, false);
                    }
                }
                world.playSound(null, pos, net.minecraft.init.SoundEvents.ITEM_BOTTLE_FILL,
                        net.minecraft.util.SoundCategory.BLOCKS, 1.0F, 1.0F);
                event.setCanceled(true);
                return;
            }
        }

        FluidStack contained = FluidUtil.getFluidContained(held);
        if (contained != null && contained.amount > 0) {
            BlockPos target = clickedState.getBlock().isReplaceable(world, pos)
                    ? pos : pos.offset(event.getFace());
            if (world.isBlockLoaded(target)) {
                FluidActionResult placed = FluidUtil.tryPlaceFluid(
                        player, world, target, held, contained);
                if (placed.isSuccess()) {
                    replaceFilledBucket(player, hand, held, placed.getResult());
                    event.setCanceled(true);
                    return;
                }
            }
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
                world.playSound(null, pos, net.minecraft.init.SoundEvents.ITEM_BUCKET_FILL,
                        net.minecraft.util.SoundCategory.BLOCKS, 1.0F, 1.0F);
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
