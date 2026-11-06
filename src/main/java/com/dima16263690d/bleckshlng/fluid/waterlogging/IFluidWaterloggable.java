package com.dima16263690d.bleckshlng.fluid.waterlogging;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Implement on backported/modded block classes which store waterlogging
 * directly in their own block state/metadata. Existing vanilla 1.12.2 blocks
 * continue to use the persistent sidecar because their metadata cannot change.
 */
public interface IFluidWaterloggable {
    boolean isWaterlogged(IBlockState state);
    IBlockState withWaterlogged(IBlockState state, boolean waterlogged);

    /** Called after water has entered this block; implementations may extinguish it. */
    default void onWaterlogged(World world, BlockPos pos,
                               IBlockState previousState, IBlockState waterloggedState) {
    }
}
