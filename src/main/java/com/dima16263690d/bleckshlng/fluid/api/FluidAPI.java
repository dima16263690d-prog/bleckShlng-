package com.dima16263690d.bleckshlng.fluid.api;

import java.util.Random;

import com.dima16263690d.bleckshlng.fluid.compatibility.Minecraft112FluidWorldAdapter;
import com.dima16263690d.bleckshlng.fluid.core.Fluid;
import com.dima16263690d.bleckshlng.fluid.core.FluidPos;
import com.dima16263690d.bleckshlng.fluid.core.FluidState;
import com.dima16263690d.bleckshlng.fluid.core.FluidVector;
import com.dima16263690d.bleckshlng.fluid.core.ModernFluidEngine;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Public access point for the version-independent 1.18.2-style fluid model.
 *
 * The simulation itself is Java 8 code under fluid.core. This class is the
 * narrow Forge 1.12.2 API adapter: it translates World/BlockPos to the portable
 * FluidWorld interface and never exposes BlockLiquid metadata to callers.
 *
 * Host IBlockState and contained FluidState are independent values. Querying
 * the fluid in a waterlogged slab, stair or other host does not replace or
 * fabricate the host block state.
 */
public final class FluidAPI {
    private FluidAPI() { }

    /** Returns EMPTY when the cell contains no fluid. */
    public static FluidState getFluidState(World world, BlockPos pos) {
        if (world == null || pos == null || !world.isBlockLoaded(pos)) {
            return FluidState.EMPTY;
        }
        return new Minecraft112FluidWorldAdapter(world).getFluidState(toFluidPos(pos));
    }

    public static Fluid getFluid(World world, BlockPos pos) {
        FluidState state = getFluidState(world, pos);
        return state.isEmpty() ? null : state.getType();
    }

    public static boolean hasFluid(World world, BlockPos pos) {
        return !getFluidState(world, pos).isEmpty();
    }

    public static boolean isWater(World world, BlockPos pos) {
        FluidState state = getFluidState(world, pos);
        return !state.isEmpty()
                && state.getType().isSame(com.dima16263690d.bleckshlng.fluid.core.Fluids.WATER);
    }

    public static boolean isLava(World world, BlockPos pos) {
        FluidState state = getFluidState(world, pos);
        return !state.isEmpty()
                && state.getType().isSame(com.dima16263690d.bleckshlng.fluid.core.Fluids.LAVA);
    }

    public static float getFluidHeight(World world, BlockPos pos) {
        if (world == null || pos == null || !world.isBlockLoaded(pos)) {
            return 0.0F;
        }
        FluidState state = getFluidState(world, pos);
        if (state.isEmpty()) {
            return 0.0F;
        }
        return state.getHeight(
                new Minecraft112FluidWorldAdapter(world), toFluidPos(pos));
    }

    public static FluidVector getFlow(World world, BlockPos pos) {
        return ModernFluidEngine.getFlow(world, pos);
    }

    /** Executes the scheduled 1.18.2-style fluid tick at this position. */
    public static boolean tick(World world, BlockPos pos) {
        return ModernFluidEngine.tick(world, pos);
    }

    public static boolean randomTick(World world, BlockPos pos, Random random) {
        return ModernFluidEngine.randomTick(world, pos, random);
    }

    public static boolean animateTick(World world, BlockPos pos, Random random) {
        return ModernFluidEngine.animateTick(world, pos, random);
    }

    /**
     * Writes a fluid state through the platform adapter. The adapter preserves
     * the host block whenever it is a supported water container.
     */
    public static void setFluidState(World world, BlockPos pos, FluidState state) {
        if (world == null || pos == null || state == null || world.isRemote
                || !world.isBlockLoaded(pos)) {
            return;
        }
        new Minecraft112FluidWorldAdapter(world).setFluidState(toFluidPos(pos), state);
    }

    public static void removeFluid(World world, BlockPos pos) {
        if (world == null || pos == null || world.isRemote
                || !world.isBlockLoaded(pos)) {
            return;
        }
        new Minecraft112FluidWorldAdapter(world).removeFluid(toFluidPos(pos));
    }

    private static FluidPos toFluidPos(BlockPos pos) {
        return new FluidPos(pos.getX(), pos.getY(), pos.getZ());
    }
}
