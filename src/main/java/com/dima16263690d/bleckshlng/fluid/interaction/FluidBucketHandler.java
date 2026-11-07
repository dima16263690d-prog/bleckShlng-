package com.dima16263690d.bleckshlng.fluid.interaction;

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
import net.minecraft.world.World;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fluids.FluidActionResult;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import com.dima16263690d.bleckshlng.BleckShlng;
import com.dima16263690d.bleckshlng.fluid.geometry.WaterloggedBlockRegistry;
import com.dima16263690d.bleckshlng.fluid.state.FluidStateBridge;
import com.dima16263690d.bleckshlng.fluid.state.FluidStateMapper;
import com.dima16263690d.bleckshlng.fluid.waterlogging.ChunkFluidData;
import com.dima16263690d.bleckshlng.fluid.waterlogging.WaterloggedFluidHandler;

/**
 * Water bucket interaction for waterlogged hosts (1.13-style).
 * Pour onto slab → tryWaterlog. Empty bucket on waterlogged host → pickupWater.
 */
@Mod.EventBusSubscriber(modid = BleckShlng.MODID)
public final class FluidBucketHandler {
    private FluidBucketHandler() {
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

        // --- Place water (waterlog host) ---
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

        // --- Bottle sample (does not remove waterlog) ---
        if (held.getItem() == Items.GLASS_BOTTLE) {
            FluidStateBridge sampled = findWaterlogSource(world, pos, event.getFace());
            if (sampled != null) {
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
                world.playSound(null, pos, SoundEvents.ITEM_BOTTLE_FILL,
                        SoundCategory.BLOCKS, 1.0F, 1.0F);
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

        // --- Empty bucket: pick up waterlogged source ---
        if (held.getItem() == Items.BUCKET) {
            BlockPos pickPos = findWaterlogSourcePos(world, pos, event.getFace());
            if (pickPos != null) {
                IBlockState pickState = world.getBlockState(pickPos);
                if (WaterloggedFluidHandler.pickupWater(world, pickPos, pickState)) {
                    world.playSound(null, pickPos, SoundEvents.ITEM_BUCKET_FILL,
                            SoundCategory.BLOCKS, 1.0F, 1.0F);
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
        FluidStateBridge clientFluid = FluidStateMapper.fromWorld(world, pos, clickedState);
        boolean waterSource = clientFluid != null
                && clientFluid.getKind() == FluidStateBridge.FluidKind.WATER
                && clientFluid.isSource();

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
        } else if (waterSource && held.getItem() == Items.GLASS_BOTTLE) {
            event.setCanceled(true);
            event.setCancellationResult(net.minecraft.util.EnumActionResult.SUCCESS);
        } else if (held.getItem() == Items.BUCKET) {
            // Also predict when fluid is on the face cell.
            if (waterSource && !(clickedState.getBlock()
                    instanceof net.minecraft.block.BlockLiquid)) {
                event.setCanceled(true);
                event.setCancellationResult(net.minecraft.util.EnumActionResult.SUCCESS);
            } else if (event.getFace() != null) {
                BlockPos other = pos.offset(event.getFace());
                if (world.isBlockLoaded(other)) {
                    FluidStateBridge otherFluid = FluidStateMapper.fromWorld(
                            world, other, world.getBlockState(other));
                    if (otherFluid != null
                            && otherFluid.getKind() == FluidStateBridge.FluidKind.WATER
                            && otherFluid.isSource()
                            && !(world.getBlockState(other).getBlock()
                            instanceof net.minecraft.block.BlockLiquid)) {
                        event.setCanceled(true);
                        event.setCancellationResult(net.minecraft.util.EnumActionResult.SUCCESS);
                    }
                }
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

    /**
     * Find a waterlogged source at pos or along the clicked face.
     */
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
        // Empty-bucket ray often hits the slab top while fluid is the slab itself.
        for (EnumFacing d : EnumFacing.VALUES) {
            BlockPos n = pos.offset(d);
            if (world.isBlockLoaded(n) && isWaterlogSource(world, n)) {
                // Only immediate waterlog hosts, not distant liquids.
                IBlockState st = world.getBlockState(n);
                if (!(st.getBlock() instanceof net.minecraft.block.BlockLiquid)
                        && WaterloggedBlockRegistry.canWaterlog(st.getBlock())) {
                    return n;
                }
            }
        }
        return null;
    }

    private static FluidStateBridge findWaterlogSource(World world, BlockPos pos,
                                                       EnumFacing face) {
        BlockPos at = findWaterlogSourcePos(world, pos, face);
        if (at == null) {
            return null;
        }
        return FluidStateMapper.fromWorld(world, at, world.getBlockState(at));
    }

    private static boolean isWaterlogSource(World world, BlockPos pos) {
        IBlockState state = world.getBlockState(pos);
        if (state.getBlock() instanceof net.minecraft.block.BlockLiquid) {
            return false;
        }
        FluidStateBridge fluid = FluidStateMapper.fromWorld(world, pos, state);
        if (fluid != null
                && fluid.getKind() == FluidStateBridge.FluidKind.WATER
                && fluid.isSource()) {
            return true;
        }
        // Direct sidecar read — do not depend only on mapper edge cases.
        if (!world.isRemote) {
            ChunkFluidData data = ChunkFluidData.get(world);
            if (data != null) {
                FluidStateBridge stored = data.getWater(pos);
                if (stored != null
                        && stored.getKind() == FluidStateBridge.FluidKind.WATER
                        && stored.isSource()) {
                    return true;
                }
            }
        }
        return false;
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
