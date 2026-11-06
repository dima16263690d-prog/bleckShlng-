package com.dima16263690d.bleckshlng.fluid.engine;

import java.util.Random;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Public entry point for dynamic and waterlogged fluid updates.
 * Detailed FlowingFluid-compatible rules are isolated in FluidSpreadEngine.
 */
public final class FluidFlowEngine {
    private FluidFlowEngine() { }

    public static void tick(World world, BlockPos pos, IBlockState state, Random random) {
        FluidSpreadEngine.tick(world, pos, state, random);
    }

    public static void spreadWaterloggedSource(World world, BlockPos pos) {
        FluidSpreadEngine.spreadWaterloggedSource(world, pos);
    }
}
