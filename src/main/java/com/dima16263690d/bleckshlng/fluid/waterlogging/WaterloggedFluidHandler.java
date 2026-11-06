package com.dima16263690d.bleckshlng.fluid.waterlogging;

import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import com.dima16263690d.bleckshlng.fluid.engine.FluidFlowEngine;
import com.dima16263690d.bleckshlng.fluid.geometry.WaterloggedBlockRegistry;
import com.dima16263690d.bleckshlng.fluid.state.FluidStateBridge;

/**
 * Waterlogging adapter for 1.12.2 blocks that have modern waterlogged
 * counterparts. The target block itself is never replaced.
 */
public final class WaterloggedFluidHandler {
    private WaterloggedFluidHandler() {
    }

    public static boolean tryWaterlog(World world, BlockPos pos,
                                      IBlockState blockState, int legacyLevel) {
        if (world == null || world.isRemote || blockState == null
                || blockState.getMaterial() == Material.WATER
                || !WaterloggedBlockRegistry.canWaterlog(blockState.getBlock())) {
            return false;
        }

        if (blockState.getBlock() instanceof IFluidWaterloggable) {
            IFluidWaterloggable adapter = (IFluidWaterloggable) blockState.getBlock();
            if (adapter.isWaterlogged(blockState)) {
                return false;
            }
            IBlockState changed = adapter.withWaterlogged(blockState, true);
            if (changed == null || changed == blockState) {
                return false;
            }
            world.setBlockState(pos, changed, 3);
            world.notifyNeighborsOfStateChange(pos, changed.getBlock(), false);
            return true;
        }

        ChunkFluidData data = ChunkFluidData.get(world);
        if (data == null || data.getWater(pos) != null) {
            return false;
        }

        FluidStateBridge stored = FluidStateBridge.source(FluidStateBridge.FluidKind.WATER);
        if (!data.putWater(pos, stored)) {
            return false;
        }

        world.notifyNeighborsOfStateChange(pos, blockState.getBlock(), false);
        FluidTickScheduler.schedule(world, pos, 1);
        return true;
    }

    public static boolean pickupWater(World world, BlockPos pos, IBlockState state) {
        if (world == null || world.isRemote || pos == null || state == null) {
            return false;
        }

        if (state.getBlock() instanceof IFluidWaterloggable) {
            IFluidWaterloggable adapter = (IFluidWaterloggable) state.getBlock();
            if (!adapter.isWaterlogged(state)) {
                return false;
            }
            IBlockState changed = adapter.withWaterlogged(state, false);
            if (changed == null || changed == state) {
                return false;
            }
            world.setBlockState(pos, changed, 3);
            world.notifyNeighborsOfStateChange(pos, changed.getBlock(), false);
            return true;
        }

        ChunkFluidData data = ChunkFluidData.get(world);
        if (data == null) {
            return false;
        }
        FluidStateBridge fluid = data.getWater(pos);
        if (fluid == null || !fluid.isSource()) {
            return false;
        }
        return data.remove(pos);
    }

    public static void tick(World world, BlockPos pos) {
        if (world == null || world.isRemote || !world.isBlockLoaded(pos)) {
            return;
        }

        IBlockState state = world.getBlockState(pos);
        if (state.getBlock() instanceof IFluidWaterloggable) {
            IFluidWaterloggable adapter = (IFluidWaterloggable) state.getBlock();
            if (!adapter.isWaterlogged(state)) {
                return;
            }
            FluidFlowEngine.spreadWaterloggedSource(world, pos);
            return;
        }

        ChunkFluidData data = ChunkFluidData.get(world);
        if (data == null || data.getWater(pos) == null) {
            return;
        }

        if (!WaterloggedBlockRegistry.canWaterlog(state.getBlock())) {
            data.remove(pos);
            return;
        }

        FluidFlowEngine.spreadWaterloggedSource(world, pos);
    }
}
