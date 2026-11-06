package com.dima16263690d.bleckshlng.fluid.state;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * 1.18-style fluid height calculations mapped to 1.12.2 block metadata.
 */
public final class FluidHeightCalculator {
    private FluidHeightCalculator() {
    }

    public static float getOwnHeight(FluidStateBridge state) {
        return state == null ? 0.0F : state.getOwnHeight();
    }

    public static float getHeight(World world, BlockPos pos, FluidStateBridge state) {
        if (world == null || pos == null || state == null) {
            return 0.0F;
        }

        BlockPos above = pos.up();
        if (world.isBlockLoaded(above)) {
            FluidStateBridge aboveState = FluidStateMapper.fromWorld(
                    world, above, world.getBlockState(above));
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
