package com.dima16263690d.bleckshlng.fluid.state;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import com.dima16263690d.bleckshlng.fluid.geometry.FluidShapeCalculator;

/**
 * Java Edition 1.13.2 fluid height calculations mapped to 1.12.2 metadata.
 */
public final class FluidHeightCalculator {
    private FluidHeightCalculator() {
    }

    public static float getOwnHeight(FluidStateBridge state) {
        return state == null ? 0.0F : state.getOwnHeight();
    }

    public static float getHeight(World world, BlockPos pos, FluidStateBridge state) {
        if (world == null || pos == null || state == null
                || !world.isBlockLoaded(pos)) {
            return 0.0F;
        }

        /*
         * Match Fluidlogged-API's FluidState.getActualHeight():
         * a fluid above raises this surface to the block ceiling only when
         * both faces of the shared plane allow fluid connectivity.
         */
        IBlockState currentBlock = world.getBlockState(pos);
        if (canFluidConnect(world, pos, currentBlock, EnumFacing.UP)) {
            BlockPos above = pos.up();
            if (world.isBlockLoaded(above)) {
                IBlockState aboveBlock = world.getBlockState(above);
                if (canFluidConnect(world, above, aboveBlock, EnumFacing.DOWN)) {
                    FluidStateBridge aboveState = FluidStateMapper.fromWorld(
                            world, above, aboveBlock);
                    if (state.isSameKind(aboveState)) {
                        return 1.0F;
                    }
                }
            }
        }

        return getOwnHeight(state);
    }

    /**
     * Face is open to fluid when it is not side-solid (1.12.2 stand-in for
     * BlockFaceShape != SOLID). Half-slab rules live in FluidShapeCalculator.
     */
    private static boolean canFluidConnect(World world, BlockPos pos,
                                           IBlockState blockState,
                                           EnumFacing side) {
        return FluidShapeCalculator.isFaceOpenToFluid(world, pos, blockState, side);
    }

    /**
     * Returns the legacy surface Y coordinate in world units.
     */
    public static double getSurfaceY(World world, BlockPos pos,
                                     FluidStateBridge state) {
        return pos.getY() + (double) getHeight(world, pos, state);
    }

    public static float getBlockLiquidHeight(World world, BlockPos pos,
                                             IBlockState blockState) {
        FluidStateBridge state = FluidStateMapper.fromBlockState(blockState);
        return getHeight(world, pos, state);
    }

    public static boolean containsSurface(World world, BlockPos pos,
                                          FluidStateBridge state, double y) {
        return state != null && y <= getSurfaceY(world, pos, state);
    }
}
