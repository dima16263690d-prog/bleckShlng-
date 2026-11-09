package com.dima16263690d.bleckshlng.fluid.waterlogging;

import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import com.dima16263690d.bleckshlng.fluid.engine.FluidFlowEngine;
import com.dima16263690d.bleckshlng.fluid.geometry.FluidShapeCalculator;
import com.dima16263690d.bleckshlng.fluid.geometry.WaterloggedBlockRegistry;
import com.dima16263690d.bleckshlng.fluid.network.FluidNetwork;
import com.dima16263690d.bleckshlng.fluid.state.FluidStateBridge;

/**
 * Modern waterlogging adapter for legacy 1.12.2 vanilla blocks.
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

            FluidStateBridge stored = FluidStateBridge.source(FluidStateBridge.FluidKind.WATER);
            FluidShapeCalculator.invalidate(world, pos);
            ChunkFluidData data = ChunkFluidData.get(world);
            if (data != null) {
                data.putWater(pos, stored);
            }
            FluidNetwork.syncState(world, pos, stored);
            FluidTickScheduler.schedule(world, pos, 1);
            FluidFlowEngine.spreadWaterloggedSource(world, pos);
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

        FluidShapeCalculator.invalidate(world, pos);
        FluidNetwork.syncState(world, pos, stored);
        FluidTickScheduler.schedule(world, pos, 1);
        FluidFlowEngine.spreadWaterloggedSource(world, pos);
        world.notifyNeighborsOfStateChange(pos, blockState.getBlock(), false);
        return true;
    }

    /**
     * Removes stored water from a waterlogged host while keeping the block.
     * Used by empty bucket pickup and sponge absorption.
     */
    public static boolean pickupWater(World world, BlockPos pos, IBlockState state) {
        if (world == null || world.isRemote || pos == null || state == null
                || !world.isBlockLoaded(pos)) {
            return false;
        }

        boolean removed = false;

        if (state.getBlock() instanceof IFluidWaterloggable) {
            IFluidWaterloggable adapter = (IFluidWaterloggable) state.getBlock();
            if (adapter.isWaterlogged(state)) {
                IBlockState changed = adapter.withWaterlogged(state, false);
                // Do not remove the synchronized sidecar state unless the
                // host block actually accepted the transition to dry.
                if (changed == null || changed == state || changed.equals(state)
                        || !world.setBlockState(pos, changed, 3)) {
                    return false;
                }
                removed = true;
            }
        }

        ChunkFluidData data = ChunkFluidData.get(world);
        if (data != null && data.getWater(pos) != null) {
            if (data.remove(pos)) {
                removed = true;
            }
        }

        if (!removed) {
            return false;
        }

        FluidShapeCalculator.invalidate(world, pos);
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
                ChunkFluidData data = ChunkFluidData.get(world);
                if (data != null && data.getWater(pos) == null) {
                    FluidStateBridge stored = FluidStateBridge.source(
                            FluidStateBridge.FluidKind.WATER);
                    data.putWater(pos, stored);
                    FluidNetwork.syncState(world, pos, stored);
                }
                FluidFlowEngine.spreadWaterloggedSource(world, pos);
            } else {
                ChunkFluidData data = ChunkFluidData.get(world);
                if (data != null && data.remove(pos)) {
                    FluidNetwork.removeState(world, pos);
                }
            }
            return;
        }

        ChunkFluidData data = ChunkFluidData.get(world);
        if (data == null || data.getWater(pos) == null) {
            return;
        }

        if (!WaterloggedBlockRegistry.canWaterlog(state.getBlock())) {
            boolean releaseIntoWorld = world.isAirBlock(pos)
                    || state.getBlock().isReplaceable(world, pos);
            if (data.remove(pos)) {
                FluidNetwork.removeState(world, pos);
                if (releaseIntoWorld) {
                    world.setBlockState(pos, Blocks.WATER.getDefaultState(), 3);
                } else {
                    world.notifyNeighborsOfStateChange(pos, state.getBlock(), false);
                }
            }
            return;
        }

        FluidFlowEngine.spreadWaterloggedSource(world, pos);
    }
}
