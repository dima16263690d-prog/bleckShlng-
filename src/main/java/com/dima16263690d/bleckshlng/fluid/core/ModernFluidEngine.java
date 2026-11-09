package com.dima16263690d.bleckshlng.fluid.core;

import java.util.Random;

import com.dima16263690d.bleckshlng.fluid.compatibility.Minecraft112FluidWorldAdapter;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Entry points that execute the portable fluid domain through the 1.12.2 adapter. */
public final class ModernFluidEngine {
    private ModernFluidEngine() { }

    public static boolean tick(World world, BlockPos blockPos) {
        if (world == null || world.isRemote || blockPos == null
                || !world.isBlockLoaded(blockPos)) {
            return false;
        }

        Minecraft112FluidWorldAdapter adapter =
                new Minecraft112FluidWorldAdapter(world);
        FluidPos pos = toFluidPos(blockPos);
        FluidState state = adapter.getFluidState(pos);
        if (state.isEmpty()) {
            return false;
        }

        state.getType().tick(adapter, pos, state);
        return true;
    }

    public static boolean randomTick(World world, BlockPos blockPos, Random random) {
        if (world == null || world.isRemote || blockPos == null || random == null
                || !world.isBlockLoaded(blockPos)) {
            return false;
        }

        Minecraft112FluidWorldAdapter adapter =
                new Minecraft112FluidWorldAdapter(world);
        FluidPos pos = toFluidPos(blockPos);
        FluidState state = adapter.getFluidState(pos);
        if (state.isEmpty() || !state.getType().isRandomlyTicking()) {
            return false;
        }

        state.getType().randomTick(adapter, pos, state, random);
        return true;
    }

    public static boolean animateTick(World world, BlockPos blockPos, Random random) {
        if (world == null || !world.isRemote || blockPos == null || random == null
                || !world.isBlockLoaded(blockPos)) {
            return false;
        }

        Minecraft112FluidWorldAdapter adapter =
                new Minecraft112FluidWorldAdapter(world);
        FluidPos pos = toFluidPos(blockPos);
        FluidState state = adapter.getFluidState(pos);
        if (state.isEmpty()) {
            return false;
        }

        state.getType().animateTick(adapter, pos, state, random);
        return true;
    }

    public static FluidVector getFlow(World world, BlockPos blockPos) {
        if (world == null || blockPos == null || !world.isBlockLoaded(blockPos)) {
            return FluidVector.ZERO;
        }

        Minecraft112FluidWorldAdapter adapter =
                new Minecraft112FluidWorldAdapter(world);
        FluidPos pos = toFluidPos(blockPos);
        FluidState state = adapter.getFluidState(pos);
        return state.isEmpty()
                ? FluidVector.ZERO : state.getType().getFlow(adapter, pos, state);
    }

    private static FluidPos toFluidPos(BlockPos pos) {
        return new FluidPos(pos.getX(), pos.getY(), pos.getZ());
    }
}
