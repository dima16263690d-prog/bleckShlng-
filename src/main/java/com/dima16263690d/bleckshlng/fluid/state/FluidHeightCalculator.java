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

        // Exact FlowingFluid#getHeight(1.18.2): any matching fluid state
        // directly above raises this state's surface to the cell ceiling.
        // The renderer/flow engine separately decide whether the shared
        // collision face permits movement; height itself has no face test.
        BlockPos abovePos = pos.up();
        if (world.isBlockLoaded(abovePos)) {
            IBlockState aboveBlock = world.getBlockState(abovePos);
            FluidStateBridge above = FluidStateMapper.fromWorld(
                    world, abovePos, aboveBlock);
            if (state.isSameKind(above)) {
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
