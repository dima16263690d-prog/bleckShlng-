package com.dima16263690d.bleckshlng.coremod;

import java.util.Random;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.dima16263690d.bleckshlng.fluid.engine.FluidFlowEngine;

/**
 * Runtime bridge called by the BlockDynamicLiquid coremod transformer.
 * Returning false leaves the original vanilla tick intact.
 */
public final class FluidCoreHooks {
    private static final Logger LOGGER = LogManager.getLogger("BleckShlng/Fluid");

    private FluidCoreHooks() {
    }

    public static boolean tryUpdateDynamicLiquid(World world, BlockPos pos,
                                                 IBlockState state, Random random) {
        try {
            FluidFlowEngine.tick(world, pos, state, random);
            return true;
        } catch (Throwable error) {
            LOGGER.error("Fluid engine failed at {}; falling back to vanilla liquid tick",
                    pos, error);
            return false;
        }
    }
}
