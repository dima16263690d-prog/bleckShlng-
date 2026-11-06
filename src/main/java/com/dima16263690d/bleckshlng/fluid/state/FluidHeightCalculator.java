package com.dima16263690d.bleckshlng.fluid.state;

import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
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

        /*
         * Match Fluidlogged-API's FluidState.getActualHeight():
         * a fluid above raises this surface to the block ceiling only when
         * the current block's UP face and the upper block's DOWN face both
         * allow fluid connectivity. Merely finding fluid in pos.up() is not
         * enough: a solid slab face must stop the height extension.
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
     * Fluidlogged-API's default IFluidloggable rule: a face connects unless
     * its BlockFaceShape is SOLID. This is the correct vanilla rule for the
     * waterloggable slabs, stairs, fences, walls, trapdoors and panes handled
     * by this backport.
     */
    private static boolean canFluidConnect(World world, BlockPos pos,
                                           IBlockState blockState,
                                           EnumFacing side) {
        return blockState.getBlockFaceShape(world, pos, side)
                != BlockFaceShape.SOLID;
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
