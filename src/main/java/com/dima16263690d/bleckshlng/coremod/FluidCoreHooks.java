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
import com.dima16263690d.bleckshlng.fluid.engine.LavaRandomTickHandler;
import com.dima16263690d.bleckshlng.fluid.state.FluidProperties;
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
            if (state.getMaterial() == Material.LAVA) {
                int tickRate = FluidProperties.tickRate(world, Material.LAVA);
                int randomTickChance = Math.max(1, 1365 / Math.max(1, tickRate));
                if (random.nextInt(randomTickChance) == 0) {
                    LavaRandomTickHandler.randomTick(world, pos, random);
                }
            }
            FluidFlowEngine.tick(world, pos, state, random);
            return true;
        } catch (Throwable error) {
            LOGGER.error("Fluid engine failed at {}; suppressing duplicate vanilla update",
                    pos, error);

            // The engine may have already changed neighboring blocks before
            // an unexpected adapter/mod exception occurred. Running the
            // vanilla tick in the same call could spread twice or react twice.
            // Retry only if this exact vanilla fluid block still occupies pos.
            try {
                if (world.isBlockLoaded(pos)
                        && world.getBlockState(pos).getBlock() == state.getBlock()) {
                    world.scheduleUpdate(pos, state.getBlock(),
                            FluidProperties.tickRate(world, material));
                }
            } catch (Throwable retryError) {
                LOGGER.error("Could not reschedule failed fluid update at {}", pos, retryError);
            }
            return true;
        }
    }

    /**
     * Extends BlockFarmland's vanilla 9x2x9 water search to water stored
     * inside legacy blocks. Vanilla water is still handled by its own check.
     */
    public static boolean applyWaterloggedFarmlandWater(boolean vanillaResult,
                                                        World world, BlockPos farmlandPos) {
        if (vanillaResult || world == null || world.isRemote || farmlandPos == null) {
            return vanillaResult;
        }

        try {
            com.dima16263690d.bleckshlng.fluid.waterlogging.ChunkFluidData data =
                    com.dima16263690d.bleckshlng.fluid.waterlogging.ChunkFluidData.get(world);
            if (data == null) {
                return false;
            }

            for (int x = -4; x <= 4; x++) {
                for (int y = 0; y <= 1; y++) {
                    for (int z = -4; z <= 4; z++) {
                        BlockPos waterPos = farmlandPos.add(x, y, z);
                        if (!world.isBlockLoaded(waterPos)) {
                            continue;
                        }

                        com.dima16263690d.bleckshlng.fluid.state.FluidStateBridge fluid =
                                data.getWater(waterPos);
                        if (fluid != null
                                && fluid.getKind()
                                == com.dima16263690d.bleckshlng.fluid.state.FluidStateBridge.FluidKind.WATER) {
                            return true;
                        }
                    }
                }
            }
            return false;
        } catch (Throwable error) {
            LOGGER.error("Waterlogged farmland hydration check failed at {}", farmlandPos, error);
            return vanillaResult;
        }
    }

    /**
     * BlockReed.canPlaceBlockAt normally requires water beside its soil.
     * In 1.13.2 a waterlogged block is also a water source for this check.
     */
    public static boolean applyWaterloggedReedWater(boolean vanillaResult,
                                                    World world, BlockPos reedPos) {
        if (vanillaResult || world == null || world.isRemote || reedPos == null) {
            return vanillaResult;
        }

        try {
            BlockPos soilPos = reedPos.down();
            if (!world.isBlockLoaded(soilPos)) {
                return false;
            }

            net.minecraft.block.Block soil = world.getBlockState(soilPos).getBlock();
            if (soil != Blocks.GRASS && soil != Blocks.DIRT
                    && soil != Blocks.SAND && soil != Blocks.REEDS) {
                return false;
            }

            com.dima16263690d.bleckshlng.fluid.waterlogging.ChunkFluidData data =
                    com.dima16263690d.bleckshlng.fluid.waterlogging.ChunkFluidData.get(world);
            if (data == null) {
                return false;
            }

            for (net.minecraft.util.EnumFacing side : net.minecraft.util.EnumFacing.Plane.HORIZONTAL) {
                BlockPos waterPos = soilPos.offset(side);
                if (!world.isBlockLoaded(waterPos)) {
                    continue;
                }
                com.dima16263690d.bleckshlng.fluid.state.FluidStateBridge fluid =
                        data.getWater(waterPos);
                if (fluid != null
                        && fluid.getKind()
                        == com.dima16263690d.bleckshlng.fluid.state.FluidStateBridge.FluidKind.WATER) {
                    return true;
                }
            }
            return false;
        } catch (Throwable error) {
            LOGGER.error("Waterlogged sugar cane placement check failed at {}", reedPos, error);
            return vanillaResult;
        }
    }

    /**
     * A lily pad can also stand on a waterlogged source, as on a regular
     * source-water block in the 1.13 water model.
     */
    public static boolean applyWaterloggedLilyPadStay(boolean vanillaResult,
                                                      World world, BlockPos lilyPos,
                                                      IBlockState lilyState) {
        if (vanillaResult || world == null || world.isRemote || lilyPos == null) {
            return vanillaResult;
        }

        try {
            BlockPos waterPos = lilyPos.down();
            if (!world.isBlockLoaded(waterPos)) {
                return false;
            }
            com.dima16263690d.bleckshlng.fluid.waterlogging.ChunkFluidData data =
                    com.dima16263690d.bleckshlng.fluid.waterlogging.ChunkFluidData.get(world);
            com.dima16263690d.bleckshlng.fluid.state.FluidStateBridge fluid =
                    data == null ? null : data.getWater(waterPos);
            return fluid != null
                    && fluid.getKind()
                    == com.dima16263690d.bleckshlng.fluid.state.FluidStateBridge.FluidKind.WATER
                    && fluid.isSource();
        } catch (Throwable error) {
            LOGGER.error("Waterlogged lily pad support check failed at {}", lilyPos, error);
            return vanillaResult;
        }
    }

    /**
     * Adds waterlogged states to the vanilla sponge's connected-water search.
     * The host block is preserved; only its embedded fluid state is removed.
     *
     * The vanilla sponge method runs first. This hook handles any adjacent
     * waterlogged chain that remains after vanilla source/flowing water was
     * absorbed, and also supports direct waterlogged neighbors.
     */
    public static boolean applyWaterloggedSpongeAbsorb(boolean vanillaResult,
                                                       World world, BlockPos spongePos) {
        if (world == null || world.isRemote || spongePos == null) {
            return vanillaResult;
        }

        try {
            com.dima16263690d.bleckshlng.fluid.waterlogging.ChunkFluidData data =
                    com.dima16263690d.bleckshlng.fluid.waterlogging.ChunkFluidData.get(world);
            java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<BlockPos>();
            java.util.Map<Long, Integer> depths = new java.util.HashMap<Long, Integer>();
            java.util.Set<Long> visited = new java.util.HashSet<Long>();
            int absorbed = 0;

            for (net.minecraft.util.EnumFacing side : net.minecraft.util.EnumFacing.values()) {
                BlockPos next = spongePos.offset(side);
                if (world.isBlockLoaded(next)) {
                    queue.add(next);
                    depths.put(Long.valueOf(next.toLong()), Integer.valueOf(1));
                }
            }

            while (!queue.isEmpty() && absorbed < 64) {
                BlockPos pos = queue.removeFirst();
                Long key = Long.valueOf(pos.toLong());
                if (!visited.add(key) || !world.isBlockLoaded(pos)) {
                    continue;
                }

                Integer depthValue = depths.get(key);
                int depth = depthValue == null ? 1 : depthValue.intValue();
                net.minecraft.block.state.IBlockState state = world.getBlockState(pos);
                boolean hasSidecar = data != null && data.getWater(pos) != null;
                boolean hasAdapterWater = state.getBlock()
                        instanceof com.dima16263690d.bleckshlng.fluid.waterlogging.IFluidWaterloggable
                        && ((com.dima16263690d.bleckshlng.fluid.waterlogging.IFluidWaterloggable)
                        state.getBlock()).isWaterlogged(state);

                if (hasSidecar || hasAdapterWater) {
                    if (hasSidecar) {
                        data.remove(pos);
                    }
                    if (hasAdapterWater) {
                        com.dima16263690d.bleckshlng.fluid.waterlogging.IFluidWaterloggable adapter =
                                (com.dima16263690d.bleckshlng.fluid.waterlogging.IFluidWaterloggable)
                                        state.getBlock();
                        net.minecraft.block.state.IBlockState dryState =
                                adapter.withWaterlogged(state, false);
                        if (dryState != null && dryState != state) {
                            world.setBlockState(pos, dryState, 3);
                        }
                    }

                    com.dima16263690d.bleckshlng.fluid.network.FluidNetwork.removeState(world, pos);
                    world.notifyNeighborsOfStateChange(pos, state.getBlock(), false);
                    com.dima16263690d.bleckshlng.fluid.geometry.FluidShapeCalculator.invalidate(world, pos);
                    ++absorbed;
                } else if (state.getMaterial() != Material.WATER) {
                    // Sponge absorption follows connected water only; it must
                    // not jump across air or solid blocks.
                    continue;
                } else {
                    // Vanilla water is handled by BlockSponge.absorbWater().
                    // Retain it as a route only if the native method left it.
                }

                if (depth < 6) {
                    for (net.minecraft.util.EnumFacing side : net.minecraft.util.EnumFacing.values()) {
                        BlockPos next = pos.offset(side);
                        if (!world.isBlockLoaded(next)) {
                            continue;
                        }
                        Long nextKey = Long.valueOf(next.toLong());
                        if (!visited.contains(nextKey) && !depths.containsKey(nextKey)) {
                            depths.put(nextKey, Integer.valueOf(depth + 1));
                            queue.addLast(next);
                        }
                    }
                }
            }

            return vanillaResult || absorbed > 0;
        } catch (Throwable error) {
            LOGGER.error("Waterlogged sponge absorption failed at {}", spongePos, error);
            return vanillaResult;
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
