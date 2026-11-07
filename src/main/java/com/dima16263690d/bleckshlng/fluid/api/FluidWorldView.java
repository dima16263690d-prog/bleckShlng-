package com.dima16263690d.bleckshlng.fluid.api;

import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import com.dima16263690d.bleckshlng.fluid.network.ClientFluidStateCache;
import com.dima16263690d.bleckshlng.fluid.state.FluidStateBridge;
import com.dima16263690d.bleckshlng.fluid.state.FluidStateMapper;
import com.dima16263690d.bleckshlng.fluid.waterlogging.ChunkFluidData;

/**
 * Unified 1x1 fluid cell access for both server physics and client render.
 *
 * A cell may hold fluid as:
 *   - vanilla BlockLiquid (world state), or
 *   - waterlogged sidecar (ChunkFluidData / ClientFluidStateCache).
 *
 * Callers never need to know which storage is used.
 */
public final class FluidWorldView {
    private FluidWorldView() {
    }

    /**
     * Fluid present in this 1x1 cell, or null if empty.
     */
    public static FluidStateBridge getFluid(World world, BlockPos pos) {
        if (world == null || pos == null || !world.isBlockLoaded(pos)) {
            return null;
        }
        IBlockState state = world.getBlockState(pos);

        // Vanilla liquid block occupies the whole cell.
        if (state.getBlock() instanceof BlockLiquid && state.getMaterial().isLiquid()) {
            return FluidStateMapper.fromWorld(world, pos, state);
        }

        // Waterlogged sidecar.
        if (world.isRemote) {
            return ClientFluidStateCache.get(
                    world.provider.getDimension(), pos.toLong());
        }
        ChunkFluidData data = ChunkFluidData.get(world);
        if (data != null) {
            FluidStateBridge stored = data.getWater(pos);
            if (stored != null) {
                return stored;
            }
        }
        return FluidStateMapper.fromWorld(world, pos, state);
    }

    /**
     * True when the neighbour cell holds the same fluid kind (for face culling
     * and continuous 1x1 volumes).
     */
    public static boolean isSameFluid(World world, BlockPos pos, FluidStateBridge.FluidKind kind) {
        if (world == null || pos == null || kind == null || !world.isBlockLoaded(pos)) {
            return false;
        }
        IBlockState state = world.getBlockState(pos);
        if (kind == FluidStateBridge.FluidKind.WATER
                && state.getMaterial() == Material.WATER) {
            return true;
        }
        if (kind == FluidStateBridge.FluidKind.LAVA
                && state.getMaterial() == Material.LAVA) {
            return true;
        }
        FluidStateBridge fluid = getFluid(world, pos);
        return fluid != null && fluid.getKind() == kind;
    }

    /**
     * Height of fluid surface in this cell (0..1), vanilla 8/9 for source.
     */
    public static float getHeight(World world, BlockPos pos, FluidStateBridge fluid) {
        if (fluid == null) {
            return 0.0F;
        }
        if (isSameFluid(world, pos.up(), fluid.getKind())) {
            return 1.0F;
        }
        return fluid.getOwnHeight() <= 0.001F ? 0.8888889F : fluid.getOwnHeight();
    }

    /**
     * Which faces of the 1x1 cell must be drawn so the volume merges with
     * neighbouring fluid (no internal walls = no blue box).
     */
    public static FluidFaceMask facesToRender(World world, BlockPos pos,
                                              FluidStateBridge fluid) {
        if (world == null || pos == null || fluid == null) {
            return FluidFaceMask.NONE;
        }
        FluidStateBridge.FluidKind kind = fluid.getKind();
        boolean up = !isSameFluid(world, pos.up(), kind);
        boolean down = !isSameFluid(world, pos.down(), kind);
        boolean north = !isSameFluid(world, pos.offset(EnumFacing.NORTH), kind);
        boolean south = !isSameFluid(world, pos.offset(EnumFacing.SOUTH), kind);
        boolean west = !isSameFluid(world, pos.offset(EnumFacing.WEST), kind);
        boolean east = !isSameFluid(world, pos.offset(EnumFacing.EAST), kind);
        return new FluidFaceMask(up, down, north, south, west, east);
    }
}
