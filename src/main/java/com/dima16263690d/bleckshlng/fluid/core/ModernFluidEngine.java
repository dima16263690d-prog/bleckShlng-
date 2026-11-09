package com.dima16263690d.bleckshlng.fluid.core;

import java.util.Random;

import com.dima16263690d.bleckshlng.fluid.compatibility.Minecraft112FluidWorldAdapter;
import com.dima16263690d.bleckshlng.fluid.engine.FluidMixingHandler;
import com.dima16263690d.bleckshlng.fluid.waterlogging.FluidTickScheduler;

import net.minecraft.util.math.BlockPos;
import net.minecraft.block.BlockDynamicLiquid;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.state.IBlockState;
import net.minecraft.world.World;

/** Entry points that execute the portable fluid domain through the 1.12.2 adapter. */
public final class ModernFluidEngine {
    private ModernFluidEngine() { }

    /**
     * Mirrors LiquidBlock#onPlace for native source cells. Flowing liquid
     * blocks keep their normal scheduled-update path, which is intercepted
     * by the coremod; static source blocks use the portable scheduler.
     */
    public static void onBlockAdded(World world, BlockPos blockPos) {
        if (!reactLavaAt(world, blockPos)) {
            scheduleStaticSource(world, blockPos);
        }
    }

    /**
     * Mirrors LiquidBlock#neighborChanged: lava reacts immediately to water
     * and source blocks are re-ticked when their neighborhood changes.
     */
    public static void onNeighborChanged(World world, BlockPos blockPos) {
        if (reactLavaAt(world, blockPos)) {
            return;
        }
        scheduleStaticSource(world, blockPos);
    }

    private static boolean reactLavaAt(World world, BlockPos blockPos) {
        if (world == null || world.isRemote || blockPos == null
                || !world.isBlockLoaded(blockPos)) {
            return false;
        }
        IBlockState state = world.getBlockState(blockPos);
        if (!(state.getBlock() instanceof BlockLiquid)
                || state.getMaterial() != net.minecraft.block.material.Material.LAVA) {
            return false;
        }
        return FluidMixingHandler.reactAt(world, blockPos, state);
    }

    private static void scheduleStaticSource(World world, BlockPos blockPos) {
        if (world == null || world.isRemote || blockPos == null
                || !world.isBlockLoaded(blockPos)) {
            return;
        }
        IBlockState legacyState = world.getBlockState(blockPos);
        if (!(legacyState.getBlock() instanceof BlockLiquid)
                || legacyState.getBlock() instanceof BlockDynamicLiquid) {
            return;
        }

        Minecraft112FluidWorldAdapter adapter =
                new Minecraft112FluidWorldAdapter(world);
        FluidPos pos = toFluidPos(blockPos);
        FluidState state = adapter.getFluidState(pos);
        if (!state.isEmpty() && state.isSource()) {
            FluidTickScheduler.schedule(world, blockPos,
                    state.getType().getTickDelay(adapter));
        }
    }

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
