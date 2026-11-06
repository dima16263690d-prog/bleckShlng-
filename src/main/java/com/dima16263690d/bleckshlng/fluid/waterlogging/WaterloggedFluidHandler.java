package com.dima16263690d.bleckshlng.fluid.waterlogging;

import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import com.dima16263690d.bleckshlng.fluid.engine.FluidFlowEngine;
import com.dima16263690d.bleckshlng.fluid.geometry.WaterloggedBlockRegistry;
import com.dima16263690d.bleckshlng.fluid.network.FluidNetwork;
import com.dima16263690d.bleckshlng.fluid.state.FluidStateBridge;

/**
 * Waterlogging adapter for 1.12.2 blocks with 1.18.2 waterlogged counterparts.
 * The target block itself is never replaced and its metadata stays unchanged.
 */
public final class WaterloggedFluidHandler {
    private WaterloggedFluidHandler() {
    }

    public static boolean tryWaterlog(World world, BlockPos pos,
                                      IBlockState blockState, int legacyLevel) {
        if (world == null || world.isRemote || pos == null || blockState == null
                || !world.isBlockLoaded(pos)
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
            if (changed == null || changed == blockState
                    || !world.setBlockState(pos, changed, 3)) {
                return false;
            }

            FluidNetwork.syncState(world, pos,
                    FluidStateBridge.source(FluidStateBridge.FluidKind.WATER));
            FluidTickScheduler.schedule(world, pos, 1);
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

        // The sidecar is not a block-state property, so explicitly notify
        // neighbors and synchronize it to clients.
        FluidNetwork.syncState(world, pos, stored);
        world.notifyNeighborsOfStateChange(pos, blockState.getBlock(), false);
        FluidTickScheduler.schedule(world, pos, 1);
        return true;
    }

    public static boolean pickupWater(World world, BlockPos pos, IBlockState state) {
        if (world == null || world.isRemote || pos == null || state == null
                || !world.isBlockLoaded(pos)) {
            return false;
        }

        if (state.getBlock() instanceof IFluidWaterloggable) {
            IFluidWaterloggable adapter = (IFluidWaterloggable) state.getBlock();
            if (!adapter.isWaterlogged(state)) {
                return false;
            }
            IBlockState changed = adapter.withWaterlogged(state, false);
            if (changed == null || changed == state
                    || !world.setBlockState(pos, changed, 3)) {
                return false;
            }
            FluidNetwork.removeState(world, pos);
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

        if (!data.remove(pos)) {
            return false;
        }

        FluidNetwork.removeState(world, pos);
        world.notifyNeighborsOfStateChange(pos, state.getBlock(), false);
        return true;
    }

    public static void tick(World world, BlockPos pos) {
        if (world == null || world.isRemote || pos == null
                || !world.isBlockLoaded(pos)) {
            return;
        }

        IBlockState state = world.getBlockState(pos);
        if (state.getBlock() instanceof IFluidWaterloggable) {
            IFluidWaterloggable adapter = (IFluidWaterloggable) state.getBlock();
            if (adapter.isWaterlogged(state)) {
                FluidFlowEngine.spreadWaterloggedSource(world, pos);
            }
            return;
        }

        ChunkFluidData data = ChunkFluidData.get(world);
        if (data == null || data.getWater(pos) == null) {
            return;
        }

        if (!WaterloggedBlockRegistry.canWaterlog(state.getBlock())) {
            // The host block may have been replaced by an explosion or another
            // mod without a player BreakEvent. Remove the stale client state too.
            if (data.remove(pos)) {
                FluidNetwork.removeState(world, pos);
                world.notifyNeighborsOfStateChange(pos, state.getBlock(), false);
            }
            return;
        }

        FluidFlowEngine.spreadWaterloggedSource(world, pos);
    }
}
