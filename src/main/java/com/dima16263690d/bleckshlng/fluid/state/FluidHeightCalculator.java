package com.dima16263690d.bleckshlng.fluid.state;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

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

        // Port of FlowingFluid.getHeight() / hasSameAbove() from 1.21:
        // a matching fluid state directly above makes this column full-height.
        // The host block's collision shape is handled separately by
        // FluidShapeCalculator when building the visible/free fluid volume.
        BlockPos above = pos.up();
        if (world.isBlockLoaded(above)) {
            IBlockState aboveBlock = world.getBlockState(above);
            FluidStateBridge aboveState = FluidStateMapper.fromWorld(
                    world, above, aboveBlock);
            if (state.isSameKind(aboveState)) {
                return 1.0F;
            }
        }

        return getOwnHeight(state);
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
