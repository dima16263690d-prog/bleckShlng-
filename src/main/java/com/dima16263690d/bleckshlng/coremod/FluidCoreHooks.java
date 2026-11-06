package com.dima16263690d.bleckshlng.coremod;

import java.util.Random;

import net.minecraft.block.BlockDynamicLiquid;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.dima16263690d.bleckshlng.fluid.engine.FluidFlowEngine;
import com.dima16263690d.bleckshlng.fluid.physics.FluidFlowVector;
import com.dima16263690d.bleckshlng.fluid.physics.FluidEntityHandler;

/**
 * Runtime bridge called by the guarded coremod transformers.
 * Returning false/null leaves the original vanilla implementation active.
 */
public final class FluidCoreHooks {
    private static final Logger LOGGER = LogManager.getLogger("BleckShlng/Fluid");

    private FluidCoreHooks() {
    }

    public static boolean tryUpdateDynamicLiquid(World world, BlockPos pos,
                                                 IBlockState state, Random random) {
        // Do not take ownership of client ticks, modded liquids, or unrelated
        // BlockDynamicLiquid subclasses. Their original vanilla/Forge code runs.
        if (world == null || world.isRemote || pos == null || state == null
                || random == null || !(state.getBlock() instanceof BlockDynamicLiquid)
                || (state.getBlock() != Blocks.FLOWING_WATER
                && state.getBlock() != Blocks.FLOWING_LAVA)) {
            return false;
        }

        Material material = state.getMaterial();
        if (material != Material.WATER && material != Material.LAVA) {
            return false;
        }

        try {
            FluidFlowEngine.tick(world, pos, state, random);
            return true;
        } catch (Throwable error) {
            LOGGER.error("Fluid engine failed at {}; falling back to vanilla liquid tick",
                    pos, error);
            return false;
        }
    }

    /**
     * Returns null when the block is not one of the vanilla liquids handled
     * here. The transformer then executes the original BlockLiquid method.
     */
    public static boolean applyWaterloggedMovement(boolean vanillaResult, Entity entity) {
        if (vanillaResult) {
            return true;
        }
        try {
            return FluidEntityHandler.updateWaterloggedMovement(entity);
        } catch (Throwable error) {
            LOGGER.error("Waterlogged entity movement check failed; keeping vanilla result", error);
            return false;
        }
    }

    public static boolean applyWaterloggedInWater(boolean vanillaResult, Entity entity) {
        if (vanillaResult) {
            return true;
        }
        try {
            return FluidEntityHandler.isInWaterloggedFluid(entity);
        } catch (Throwable error) {
            LOGGER.error("Waterlogged isInWater check failed; keeping vanilla result", error);
            return false;
        }
    }

    public static boolean applyWaterloggedMaterialCheck(boolean vanillaResult,
                                                        Entity entity, Material material) {
        if (vanillaResult || material != Material.WATER) {
            return vanillaResult;
        }
        try {
            return FluidEntityHandler.isEyeInWaterloggedFluid(entity);
        } catch (Throwable error) {
            LOGGER.error("Waterlogged eye-fluid check failed; keeping vanilla result", error);
            return false;
        }
    }

    public static Vec3d tryModifyAcceleration(BlockLiquid block, World world,
                                               BlockPos pos, Entity entity,
                                               Vec3d originalMotion) {
        if (block == null || world == null || pos == null || originalMotion == null) {
            return null;
        }

        if (block != Blocks.WATER && block != Blocks.FLOWING_WATER
                && block != Blocks.LAVA && block != Blocks.FLOWING_LAVA) {
            return null;
        }

        IBlockState state = world.getBlockState(pos);
        if (state.getBlock() != block) {
            return null;
        }

        try {
            return originalMotion.add(FluidFlowVector.calculate(world, pos, state));
        } catch (Throwable error) {
            LOGGER.error("Fluid acceleration calculation failed at {}; using vanilla current",
                    pos, error);
            return null;
        }
    }
}
