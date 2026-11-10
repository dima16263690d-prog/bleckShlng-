package com.dima16263690d.bleckshlng.fluid.api;

import com.dima16263690d.bleckshlng.fluid.render.FluidOcclusionShape;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

/**
 * Optional block API for exact fluid-render occlusion geometry.
 *
 * Implement this on a block when its visual occlusion differs from its
 * collision boxes. The returned face shape is independent of entity/block
 * collision and uses local 0..1 face coordinates.
 */
public interface IFluidOcclusionShapeProvider {
    FluidOcclusionShape getFluidOcclusionShape(
            IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing face);
}
