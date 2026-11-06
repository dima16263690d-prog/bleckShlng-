package com.dima16263690d.bleckshlng.fluid.waterlogging;

import net.minecraft.block.state.IBlockState;

/**
 * Implement on backported/modded block classes which store waterlogging
 * directly in their own block state/metadata. Existing vanilla 1.12.2 blocks
 * continue to use the persistent sidecar because their metadata cannot change.
 */
public interface IFluidWaterloggable {
    boolean isWaterlogged(IBlockState state);
    IBlockState withWaterlogged(IBlockState state, boolean waterlogged);
}
