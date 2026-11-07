package com.dima16263690d.bleckshlng.fluid.waterlogging;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

import net.minecraft.block.BlockDynamicLiquid;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import com.dima16263690d.bleckshlng.BleckShlng;
import com.dima16263690d.bleckshlng.fluid.geometry.WaterloggedBlockRegistry;
import com.dima16263690d.bleckshlng.fluid.state.FluidStateBridge;
import com.dima16263690d.bleckshlng.fluid.state.FluidStateMapper;

/**
 * Guarantees waterlogging when a waterloggable block is placed in / next to
 * water. PlaceEvent alone is not enough: some paths replace water without a
 * reliable capture, and static ocean sources do not re-tick promptly.
 *
 * Strategy:
 * 1. PlaceEvent (LOWEST) — immediate try
 * 2. NeighborNotifyEvent — host became waterloggable near water
 * 3. Next-tick retry queue — covers ordering races with setBlockState
 */
@Mod.EventBusSubscriber(modid = BleckShlng.MODID)
public final class WaterlogPlacementHandler {
    private static final Set<Pending> PENDING = new LinkedHashSet<Pending>();

    private WaterlogPlacementHandler() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockPlace(BlockEvent.PlaceEvent event) {
        World world = event.getWorld();
        BlockPos pos = event.getPos();
        if (world == null || world.isRemote || pos == null) {
            return;
        }
        tryWaterlogAt(world, pos, true);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
        World world = event.getWorld();
        BlockPos pos = event.getPos();
        if (world == null || world.isRemote || pos == null) {
            return;
        }
        IBlockState state = world.getBlockState(pos);
        if (state == null || !WaterloggedBlockRegistry.canWaterlog(state.getBlock())) {
            return;
        }
        tryWaterlogAt(world, pos, true);
    }

    @SubscribeEvent
    public static void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.world == null
                || event.world.isRemote || PENDING.isEmpty()) {
            return;
        }
        World world = event.world;
        int dim = world.provider.getDimension();
        Iterator<Pending> it = PENDING.iterator();
        while (it.hasNext()) {
            Pending pending = it.next();
            if (pending.dimension != dim) {
                continue;
            }
            it.remove();
            if (!world.isBlockLoaded(pending.pos)) {
                continue;
            }
            tryWaterlogAt(world, pending.pos, false);
        }
    }

    private static void tryWaterlogAt(World world, BlockPos pos, boolean enqueueRetry) {
        IBlockState placed = world.getBlockState(pos);
        if (placed == null || !WaterloggedBlockRegistry.canWaterlog(placed.getBlock())) {
            return;
        }

        FluidStateBridge existing = FluidStateMapper.fromWorld(world, pos, placed);
        if (existing != null && existing.getKind() == FluidStateBridge.FluidKind.WATER) {
            scheduleNeighborFluidUpdates(world, pos);
            return;
        }

        if (!hasAdjacentWater(world, pos)) {
            if (enqueueRetry) {
                enqueue(world, pos);
            }
            return;
        }

        if (WaterloggedFluidHandler.tryWaterlog(world, pos, placed, 0)) {
            scheduleNeighborFluidUpdates(world, pos);
            if (BleckShlng.LOGGER != null) {
                BleckShlng.LOGGER.debug("Waterlogged {} at {}",
                        placed.getBlock().getRegistryName(), pos);
            }
            return;
        }

        if (enqueueRetry) {
            enqueue(world, pos);
        }
    }

    private static void enqueue(World world, BlockPos pos) {
        PENDING.add(new Pending(world.provider.getDimension(), pos.toImmutable()));
    }

    private static boolean hasAdjacentWater(World world, BlockPos pos) {
        for (EnumFacing facing : EnumFacing.VALUES) {
            BlockPos neighbor = pos.offset(facing);
            if (!world.isBlockLoaded(neighbor)) {
                continue;
            }
            IBlockState state = world.getBlockState(neighbor);
            if (state.getMaterial() == Material.WATER) {
                return true;
            }
            FluidStateBridge fluid = FluidStateMapper.fromWorld(world, neighbor, state);
            if (fluid != null && fluid.getKind() == FluidStateBridge.FluidKind.WATER) {
                return true;
            }
        }
        return false;
    }

    private static void scheduleNeighborFluidUpdates(World world, BlockPos pos) {
        for (EnumFacing facing : EnumFacing.VALUES) {
            BlockPos neighbor = pos.offset(facing);
            if (!world.isBlockLoaded(neighbor)) {
                continue;
            }
            IBlockState state = world.getBlockState(neighbor);
            if (state.getMaterial() != Material.WATER
                    || !(state.getBlock() instanceof BlockLiquid)) {
                continue;
            }
            if (!(state.getBlock() instanceof BlockDynamicLiquid)) {
                int level = state.getValue(BlockLiquid.LEVEL).intValue();
                world.setBlockState(neighbor, Blocks.FLOWING_WATER.getDefaultState()
                        .withProperty(BlockLiquid.LEVEL, Integer.valueOf(level)), 2);
                state = world.getBlockState(neighbor);
            }
            world.scheduleUpdate(neighbor, state.getBlock(), 1);
        }
    }

    private static final class Pending {
        final int dimension;
        final BlockPos pos;

        Pending(int dimension, BlockPos pos) {
            this.dimension = dimension;
            this.pos = pos;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Pending)) {
                return false;
            }
            Pending o = (Pending) other;
            return dimension == o.dimension && pos.equals(o.pos);
        }

        @Override
        public int hashCode() {
            return 31 * dimension + pos.hashCode();
        }
    }
}
