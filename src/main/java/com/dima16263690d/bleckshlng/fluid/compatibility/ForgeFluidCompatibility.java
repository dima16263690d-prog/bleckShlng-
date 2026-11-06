package com.dima16263690d.bleckshlng.fluid.compatibility;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidBlock;

/**
 * Read-only adapter for Forge IFluidBlock implementations.
 * It intentionally leaves the third-party block's tick, state and renderer
 * under that mod's ownership.
 */
public final class ForgeFluidCompatibility {
    private ForgeFluidCompatibility() {
    }

    public static boolean isForgeFluidBlock(IBlockState state) {
        return state != null && state.getBlock() instanceof IFluidBlock;
    }

    public static Fluid getFluid(IBlockState state) {
        return isForgeFluidBlock(state)
                ? ((IFluidBlock) state.getBlock()).getFluid() : null;
    }

    public static boolean canDrain(World world, BlockPos pos, IBlockState state) {
        return isForgeFluidBlock(state)
                && ((IFluidBlock) state.getBlock()).canDrain(world, pos);
    }

    public static FluidStack simulateDrain(World world, BlockPos pos, IBlockState state) {
        if (!canDrain(world, pos, state)) {
            return null;
        }
        return ((IFluidBlock) state.getBlock()).drain(world, pos, false);
    }

    public static float getFilledPercentage(World world, BlockPos pos, IBlockState state) {
        if (!isForgeFluidBlock(state)) {
            return 0.0F;
        }
        return ((IFluidBlock) state.getBlock()).getFilledPercentage(world, pos);
    }

    public static boolean isSameFluid(IBlockState first, IBlockState second) {
        Fluid a = getFluid(first);
        Fluid b = getFluid(second);
        return a != null && a == b;
    }
}
