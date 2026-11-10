package com.dima16263690d.bleckshlng.fluid.waterlogging;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import com.dima16263690d.bleckshlng.fluid.state.FluidStateBridge;

/**
 * 1.18.2 SimpleWaterloggedBlock contract for 1.12.2 blocks whose water state
 * is represented by their own IBlockState. The default survival check is the
 * closest 1.12.2 equivalent; blocks with state-dependent survival rules can
 * override it.
 */
public interface IFluidWaterloggable {
    boolean isWaterlogged(IBlockState state);

    IBlockState withWaterlogged(IBlockState state, boolean waterlogged);

    /** Equivalent to SimpleWaterloggedBlock#canPlaceLiquid for water. */
    default boolean canPlaceFluid(IBlockState state, FluidStateBridge incoming) {
        return state != null && incoming != null
                && incoming.getKind() == FluidStateBridge.FluidKind.WATER
                && !isWaterlogged(state);
    }

    /**
     * Equivalent to BlockState#canSurvive after clearing WATERLOGGED.
     * Override for blocks whose support rule depends on the dry state rather
     * than the legacy Block#canPlaceBlockAt implementation.
     */
    default boolean canSurviveAfterPickup(
            World world, BlockPos pos, IBlockState dryState) {
        return world != null && pos != null && dryState != null
                && dryState.getBlock().canPlaceBlockAt(world, pos);
    }
}
