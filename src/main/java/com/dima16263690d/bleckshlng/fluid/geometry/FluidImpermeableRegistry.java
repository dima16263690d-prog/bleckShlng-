package com.dima16263690d.bleckshlng.fluid.geometry;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import com.dima16263690d.bleckshlng.fluid.api.IFluidImpermeable;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

/** 1.12.2 compatibility bridge for the modern #minecraft:impermeable tag. */
public final class FluidImpermeableRegistry {
    private static final Set<Block> BLOCKS = Collections.newSetFromMap(
            new IdentityHashMap<Block, Boolean>());

    static {
        // In the 1.18.2 tag: glass, every stained-glass colour and tinted glass.
        // The 1.12.2 stained-glass variants share one Block instance.
        register(Blocks.GLASS);
        register(Blocks.STAINED_GLASS);
    }

    private FluidImpermeableRegistry() { }

    public static synchronized void register(Block block) {
        if (block != null) BLOCKS.add(block);
    }

    public static synchronized void unregister(Block block) {
        if (block != null) BLOCKS.remove(block);
    }

    public static boolean isImpermeable(
            IBlockState state, IBlockAccess world, BlockPos pos) {
        if (state == null) return false;
        if (state.getBlock() instanceof IFluidImpermeable) {
            return ((IFluidImpermeable) state.getBlock())
                    .isFluidImpermeable(state, world, pos);
        }
        synchronized (FluidImpermeableRegistry.class) {
            return BLOCKS.contains(state.getBlock());
        }
    }
}
