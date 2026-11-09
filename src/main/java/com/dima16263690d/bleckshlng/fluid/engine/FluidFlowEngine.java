package com.dima16263690d.bleckshlng.fluid.engine;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import com.dima16263690d.bleckshlng.fluid.core.ModernFluidEngine;

/**
 * Compatibility entry points retained for existing Forge hooks.
 * All fluid semantics are implemented by the portable 1.18.2 core.
 */
public final class FluidFlowEngine {
    private FluidFlowEngine() { }

    public static void tick(World world, BlockPos pos,
                            net.minecraft.block.state.IBlockState ignoredState,
                            java.util.Random ignoredRandom) {
        ModernFluidEngine.tick(world, pos);
    }

    public static void spreadWaterloggedSource(World world, BlockPos pos) {
        ModernFluidEngine.tick(world, pos);
    }
}
