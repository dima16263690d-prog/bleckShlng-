package com.dima16263690d.bleckshlng.fluid.interaction;

import net.minecraft.block.BlockLiquid;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionUtils;
import net.minecraft.init.PotionTypes;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.player.FillBucketEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fluids.FluidActionResult;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import com.dima16263690d.bleckshlng.BleckShlng;
import com.dima16263690d.bleckshlng.fluid.geometry.WaterloggedBlockRegistry;
import com.dima16263690d.bleckshlng.fluid.api.FluidAPI;
import com.dima16263690d.bleckshlng.fluid.core.Fluids;
import com.dima16263690d.bleckshlng.fluid.state.FluidStateBridge;
import com.dima16263690d.bleckshlng.fluid.state.FluidStateMapper;
import com.dima16263690d.bleckshlng.fluid.waterlogging.ChunkFluidData;
import com.dima16263690d.bleckshlng.fluid.waterlogging.WaterloggedFluidHandler;

/**
 * Bucket / bottle interaction with waterlogged hosts.
 *
 * Empty-bucket fill MUST use {@link FillBucketEvent}: ItemBucket does a liquid
 * raytrace and never treats a waterlogged slab as BlockLiquid, so RightClickBlock
 * alone is not enough for pickup.
 */
@Mod.EventBusSubscriber(modid = BleckShlng.MODID)
public final class FluidBucketHandler {
    private FluidBucketHandler() {
    }

    /**
     * Primary empty-bucket path (Forge ItemBucket).
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onFillBucket(FillBucketEvent event) {
        World world = event.getWorld();
        if (world == null || event.getEmptyBucket().isEmpty()) {
            return;
        }
        if (event.getEmptyBucket().getItem() != Items.BUCKET) {
            return;
        }

        RayTraceResult target = event.getTarget();
        if (target == null || target.typeOfHit != RayTraceResult.Type.BLOCK) {
            return;
        }

        BlockPos pos = target.getBlockPos();
        if (!world.isBlockLoaded(pos)) {
            return;
        }

        BlockPos pickPos = findWaterlogSourcePos(world, pos, target.sideHit);
        if (pickPos == null) {
            return;
        }

        // Client: allow the action so server applies it.
        if (world.isRemote) {
            event.setResult(Event.Result.ALLOW);
            event.setFilledBucket(new ItemStack(Items.WATER_BUCKET));
            return;
        }

        IBlockState pickState = world.getBlockState(pickPos);
        if (!WaterloggedFluidHandler.pickupWater(world, pickPos, pickState)) {
            return;
        }

        FluidAPI.playPickupSound(world, pickPos, Fluids.WATER);
        event.setFilledBucket(new ItemStack(Items.WATER_BUCKET));
        event.setResult(Event.Result.ALLOW);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
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
            handleClientPredict(event, held, world, pos, clickedState);
            return;
        }

        // Place waterlog with water bucket
        if (held.getItem() == Items.WATER_BUCKET) {
            if (tryWaterlogAt(world, pos, clickedState, player, hand, held, event)) {
                return;
            }
            if (event.getFace() != null) {
                BlockPos placePos = pos.offset(event.getFace());
                if (world.isBlockLoaded(placePos)) {
                    IBlockState placeState = world.getBlockState(placePos);
                    if (tryWaterlogAt(world, placePos, placeState, player, hand, held, event)) {
                        return;
                    }
                }
            }
        }

        if (FluidUtil.interactWithFluidHandler(player, hand, world, pos, event.getFace())) {
            event.setCanceled(true);
            return;
        }

        if (held.getItem() == Items.GLASS_BOTTLE) {
            BlockPos samplePos = findWaterlogSourcePos(world, pos, event.getFace());
            if (samplePos != null) {
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
                world.playSound(null, samplePos, SoundEvents.ITEM_BOTTLE_FILL,
                        SoundCategory.BLOCKS, 1.0F, 1.0F);
                event.setCanceled(true);
                event.setCancellationResult(net.minecraft.util.EnumActionResult.SUCCESS);
                return;
            }
        }

        FluidStack contained = FluidUtil.getFluidContained(held);
        if (contained != null && contained.amount > 0) {
            BlockPos placeTarget = clickedState.getBlock().isReplaceable(world, pos)
                    ? pos : pos.offset(event.getFace());
            if (world.isBlockLoaded(placeTarget)) {
                FluidActionResult placed = FluidUtil.tryPlaceFluid(
                        player, world, placeTarget, held, contained);
                if (placed.isSuccess()) {
                    replaceFilledBucket(player, hand, held, placed.getResult());
                    event.setCanceled(true);
                    return;
                }
            }
        }

        // Backup empty-bucket path if FillBucketEvent did not run
        if (held.getItem() == Items.BUCKET) {
            BlockPos pickPos = findWaterlogSourcePos(world, pos, event.getFace());
            if (pickPos != null) {
                IBlockState pickState = world.getBlockState(pickPos);
                if (WaterloggedFluidHandler.pickupWater(world, pickPos, pickState)) {
                    FluidAPI.playPickupSound(world, pickPos, Fluids.WATER);
                    replaceEmptyBucket(player, hand, held, new ItemStack(Items.WATER_BUCKET));
                    event.setCanceled(true);
                    event.setCancellationResult(net.minecraft.util.EnumActionResult.SUCCESS);
                }
            }
        }
    }

    private static void handleClientPredict(PlayerInteractEvent.RightClickBlock event,
                                            ItemStack held, World world, BlockPos pos,
                                            IBlockState clickedState) {
        if (held.getItem() == Items.WATER_BUCKET) {
            boolean host = WaterloggedBlockRegistry.canWaterlog(clickedState.getBlock())
                    && !hasWaterlog(world, pos);
            boolean alongFace = false;
            if (event.getFace() != null) {
                BlockPos placePos = pos.offset(event.getFace());
                if (world.isBlockLoaded(placePos)) {
                    alongFace = WaterloggedBlockRegistry.canWaterlog(
                            world.getBlockState(placePos).getBlock())
                            && !hasWaterlog(world, placePos);
                }
            }
            if (host || alongFace) {
                event.setCanceled(true);
                event.setCancellationResult(net.minecraft.util.EnumActionResult.SUCCESS);
            }
        } else if (held.getItem() == Items.BUCKET || held.getItem() == Items.GLASS_BOTTLE) {
            if (findWaterlogSourcePos(world, pos, event.getFace()) != null) {
                event.setCanceled(true);
                event.setCancellationResult(net.minecraft.util.EnumActionResult.SUCCESS);
            }
        }
    }

    private static boolean tryWaterlogAt(World world, BlockPos pos, IBlockState state,
                                         EntityPlayer player, EnumHand hand,
                                         ItemStack held,
                                         PlayerInteractEvent.RightClickBlock event) {
        if (!WaterloggedBlockRegistry.canWaterlog(state.getBlock())) {
            return false;
        }
        if (hasWaterlog(world, pos)) {
            return false;
        }
        if (!WaterloggedFluidHandler.tryWaterlog(world, pos, state, 0)) {
            return false;
        }
        world.playSound(null, pos, SoundEvents.ITEM_BUCKET_EMPTY,
                SoundCategory.BLOCKS, 1.0F, 1.0F);
        replaceFilledBucket(player, hand, held, new ItemStack(Items.BUCKET));
        event.setCanceled(true);
        event.setCancellationResult(net.minecraft.util.EnumActionResult.SUCCESS);
        return true;
    }

    private static BlockPos findWaterlogSourcePos(World world, BlockPos pos,
                                                  EnumFacing face) {
        if (isWaterlogSource(world, pos)) {
            return pos;
        }
        if (face != null) {
            BlockPos other = pos.offset(face);
            if (world.isBlockLoaded(other) && isWaterlogSource(world, other)) {
                return other;
            }
        }
        for (EnumFacing d : EnumFacing.VALUES) {
            BlockPos n = pos.offset(d);
            if (!world.isBlockLoaded(n)) {
                continue;
            }
            IBlockState st = world.getBlockState(n);
            if (st.getBlock() instanceof BlockLiquid) {
                continue;
            }
            if (isWaterlogSource(world, n)
                    && WaterloggedBlockRegistry.canWaterlog(st.getBlock())) {
                return n;
            }
        }
        return null;
    }

    private static boolean hasWaterlog(World world, BlockPos pos) {
        if (world.isRemote) {
            FluidStateBridge fluid = FluidStateMapper.fromWorld(
                    world, pos, world.getBlockState(pos));
            return fluid != null && fluid.getKind() == FluidStateBridge.FluidKind.WATER;
        }
        ChunkFluidData data = ChunkFluidData.get(world);
        return data != null && data.getWater(pos) != null;
    }

    private static boolean isWaterlogSource(World world, BlockPos pos) {
        IBlockState state = world.getBlockState(pos);
        if (state.getBlock() instanceof BlockLiquid) {
            return false;
        }
        if (world.isRemote) {
            FluidStateBridge fluid = FluidStateMapper.fromWorld(world, pos, state);
            return fluid != null
                    && fluid.getKind() == FluidStateBridge.FluidKind.WATER
                    && fluid.isSource();
        }
        ChunkFluidData data = ChunkFluidData.get(world);
        if (data == null) {
            return false;
        }
        FluidStateBridge stored = data.getWater(pos);
        return stored != null
                && stored.getKind() == FluidStateBridge.FluidKind.WATER
                && stored.isSource();
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
