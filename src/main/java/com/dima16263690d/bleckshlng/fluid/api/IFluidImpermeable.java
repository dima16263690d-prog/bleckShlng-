package com.dima16263690d.bleckshlng.fluid.api;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

/** Optional equivalent of Minecraft 1.18.2's #minecraft:impermeable tag. */
public interface IFluidImpermeable {
    boolean isFluidImpermeable(IBlockState state, IBlockAccess world, BlockPos pos);
}
