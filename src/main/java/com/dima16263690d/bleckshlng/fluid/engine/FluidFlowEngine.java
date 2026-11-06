package com.dima16263690d.bleckshlng.fluid.engine;

import java.util.Random;
import java.util.Set;

import net.minecraft.block.BlockDynamicLiquid;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import com.dima16263690d.bleckshlng.fluid.geometry.BlockGeometryAdapter;
import com.dima16263690d.bleckshlng.fluid.geometry.WaterloggedBlockRegistry;
import com.dima16263690d.bleckshlng.fluid.state.FluidProperties;
import com.dima16263690d.bleckshlng.fluid.waterlogging.FluidTickScheduler;
import com.dima16263690d.bleckshlng.fluid.waterlogging.WaterloggedFluidHandler;

/**
 * Server-side fluid tick engine.
 *
 * This is an API-adapted port of the modern FlowingFluid update model:
 * recompute the local state, inherit a vertical flow, recover water sources,
 * then prefer the shortest route to a downward opening before spreading
 * horizontally. Vanilla 1.12.2 liquid block identities and metadata remain
 * the serialized representation.
 */
public final class FluidFlowEngine {
    private FluidFlowEngine() {
    }

    public static void tick(World world, BlockPos pos, IBlockState state, Random random) {
        if (world == null || world.isRemote || pos == null || state == null
                || !world.isBlockLoaded(pos)) {
            return;
        }

        Material material = state.getMaterial();
        if (material != Material.WATER && material != Material.LAVA) {
            return;
        }

        BlockDynamicLiquid flowing = material == Material.WATER
                ? Blocks.FLOWING_WATER : Blocks.FLOWING_LAVA;

        if (material == Material.LAVA && FluidMixingHandler.reactAt(world, pos, state)) {
            return;
        }

        int level = state.getValue(BlockLiquid.LEVEL).intValue();
        int increment = FluidProperties.horizontalDecay(world, material);
        int tickRate = FluidProperties.tickRate(world, material);

        if (level > 0) {
            int minAdjacent = -100;
            int adjacentSources = 0;

            for (EnumFacing direction : EnumFacing.Plane.HORIZONTAL) {
                BlockPos neighbor = pos.offset(direction);
                if (!world.isBlockLoaded(neighbor)) {
                    continue;
                }

                int neighborLevel = getDepth(world.getBlockState(neighbor), material);
                if (neighborLevel < 0) {
                    continue;
                }

                if (neighborLevel == 0) {
                    adjacentSources++;
                }

                if (neighborLevel >= 8) {
                    neighborLevel = 0;
                }

                if (minAdjacent < 0 || neighborLevel < minAdjacent) {
                    minAdjacent = neighborLevel;
                }
            }

            int newLevel = minAdjacent + increment;
            if (newLevel >= 8 || minAdjacent < 0) {
                newLevel = -1;
            }

            BlockPos abovePos = pos.up();
            if (world.isBlockLoaded(abovePos)) {
                int aboveLevel = getDepth(world.getBlockState(abovePos), material);
                if (aboveLevel >= 0) {
                    newLevel = aboveLevel >= 8 ? aboveLevel : aboveLevel + 8;
                }
            }

            if (material == Material.WATER && adjacentSources >= 2) {
                BlockPos belowPos = pos.down();
                if (world.isBlockLoaded(belowPos)) {
                    IBlockState below = world.getBlockState(belowPos);
                    if (below.getMaterial().isSolid()
                            || (below.getMaterial() == Material.WATER
                            && getDepth(below, Material.WATER) == 0)) {
                        newLevel = 0;
                    }
                }
            }

            if (material == Material.LAVA && level < 8
                    && newLevel >= 0 && newLevel < 8 && newLevel > level
                    && random.nextInt(4) != 0) {
                tickRate *= 4;
            }

            if (newLevel == level) {
                placeStaticBlock(world, pos, material, state);
            } else {
                level = newLevel;
                if (newLevel < 0) {
                    world.setBlockToAir(pos);
                } else {
                    IBlockState changed = flowing.getDefaultState()
                            .withProperty(BlockLiquid.LEVEL, Integer.valueOf(newLevel));
                    world.setBlockState(pos, changed, 2);
                    world.scheduleUpdate(pos, flowing, tickRate);
                    world.notifyNeighborsOfStateChange(pos, flowing, false);
                    state = changed;
                }
            }
        } else {
            placeStaticBlock(world, pos, material, state);
        }

        if (level < 0) {
            return;
        }

        BlockPos down = pos.down();
        if (!world.isBlockLoaded(down)) {
            return;
        }

        IBlockState downState = world.getBlockState(down);
        if (canFlowInto(world, down, downState, material)) {
            if (material == Material.LAVA && downState.getMaterial() == Material.WATER) {
                FluidMixingHandler.reactDownward(world, down, downState);
                return;
            }

            int fallingLevel = level >= 8 ? level : level + 8;
            tryFlowInto(world, down, downState, material, fallingLevel);
            return;
        }

        if (level == 0 || BlockGeometryAdapter.isBlocked(world, down, material)) {
            int nextLevel = level >= 8 ? 1 : level + increment;
            if (nextLevel >= 8) {
                return;
            }

            Set<EnumFacing> directions = FlowDirectionSearch.getBestDirections(
                    world, pos, material,
                    FluidProperties.slopeFindDistance(world, material));

            for (EnumFacing direction : directions) {
                BlockPos target = pos.offset(direction);
                if (world.isBlockLoaded(target)) {
                    tryFlowInto(world, target, world.getBlockState(target),
                            material, nextLevel);
                }
            }
        }
    }

    /**
     * A stored waterlogged state behaves as a source without replacing its host
     * block. It uses the same downward-first and shortest-slope spread logic.
     */
    public static void spreadWaterloggedSource(World world, BlockPos pos) {
        if (world == null || world.isRemote || !world.isBlockLoaded(pos)) {
            return;
        }

        Material material = Material.WATER;
        BlockPos down = pos.down();
        if (world.isBlockLoaded(down)) {
            IBlockState below = world.getBlockState(down);
            if (canFlowInto(world, down, below, material)) {
                tryFlowInto(world, down, below, material, 8);
                return;
            }
        }

        if (world.isBlockLoaded(down)
                && BlockGeometryAdapter.isBlocked(world, down, material)) {
            Set<EnumFacing> directions = FlowDirectionSearch.getBestDirections(
                    world, pos, material, FluidProperties.slopeFindDistance(world, material));
            for (EnumFacing direction : directions) {
                BlockPos target = pos.offset(direction);
                if (world.isBlockLoaded(target)) {
                    tryFlowInto(world, target, world.getBlockState(target),
                            material, 1);
                }
            }
        }

    }

    private static int getDepth(IBlockState state, Material material) {
        if (state.getMaterial() != material || !(state.getBlock() instanceof BlockLiquid)) {
            return -1;
        }
        return state.getValue(BlockLiquid.LEVEL).intValue();
    }

    private static void placeStaticBlock(World world, BlockPos pos,
                                         Material material, IBlockState currentState) {
        IBlockState staticState = (material == Material.WATER
                ? Blocks.WATER : Blocks.LAVA).getDefaultState()
                .withProperty(BlockLiquid.LEVEL, currentState.getValue(BlockLiquid.LEVEL));
        world.setBlockState(pos, staticState, 2);
    }

    private static boolean canFlowInto(World world, BlockPos pos,
                                       IBlockState state, Material material) {
        return BlockGeometryAdapter.canFluidEnter(world, pos, state, material);
    }

    private static void tryFlowInto(World world, BlockPos pos, IBlockState state,
                                    Material material, int level) {
        if (!canFlowInto(world, pos, state, material)) {
            return;
        }

        if (material == Material.WATER
                && WaterloggedBlockRegistry.canWaterlog(state.getBlock())) {
            if (WaterloggedFluidHandler.tryWaterlog(world, pos, state, level)) {
                return;
            }
        }

        if (state.getMaterial() != Material.AIR) {
            if (material == Material.LAVA) {
                FluidMixingHandler.playExtinguishEffects(world, pos);
            } else {
                state.getBlock().dropBlockAsItem(world, pos, state, 0);
            }
        }

        BlockDynamicLiquid flowing = material == Material.WATER
                ? Blocks.FLOWING_WATER : Blocks.FLOWING_LAVA;
        IBlockState flowingState = flowing.getDefaultState()
                .withProperty(BlockLiquid.LEVEL, Integer.valueOf(level));
        world.setBlockState(pos, flowingState, 3);

        if (material == Material.LAVA) {
            FluidMixingHandler.reactAt(world, pos, world.getBlockState(pos));
        }
    }
}
