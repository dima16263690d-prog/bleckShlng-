package com.dima16263690d.bleckshlng.fluid.engine;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.block.BlockLiquid;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import com.dima16263690d.bleckshlng.fluid.geometry.BlockGeometryAdapter;
import com.dima16263690d.bleckshlng.fluid.geometry.WaterloggedBlockRegistry;

/**
 * Bounded shortest-path search used by FlowingFluid.getSpread().
 *
 * The visited set is scoped to one search and positions are removed during
 * backtracking so distinct routes can be compared without repeated cycles.
 */
public final class FlowDirectionSearch {
    private static final int NO_PATH = 1000;

    private FlowDirectionSearch() {
    }

    public static Set<EnumFacing> getBestDirections(World world, BlockPos origin,
                                                     Material fluidMaterial, int maxDistance) {
        int bestDistance = NO_PATH;
        Set<EnumFacing> result = EnumSet.noneOf(EnumFacing.class);

        for (EnumFacing direction : EnumFacing.Plane.HORIZONTAL) {
            BlockPos neighbor = origin.offset(direction);
            if (!world.isBlockLoaded(neighbor) || !canRouteThrough(world, neighbor, fluidMaterial)) {
                continue;
            }

            int distance;
            BlockPos below = neighbor.down();
            if (!world.isBlockLoaded(below)) {
                continue;
            }

            if (!BlockGeometryAdapter.isBlocked(world, below, fluidMaterial)) {
                distance = 0;
            } else {
                Set<Long> visited = new HashSet<Long>();
                distance = getSlopeDistance(world, neighbor, 1,
                        direction.getOpposite(), fluidMaterial, maxDistance, visited);
            }

            if (distance < bestDistance) {
                result.clear();
                bestDistance = distance;
            }

            if (distance <= bestDistance && distance < NO_PATH) {
                result.add(direction);
            }
        }

        return result;
    }

    private static int getSlopeDistance(World world, BlockPos pos, int distance,
                                        EnumFacing cameFrom, Material fluidMaterial,
                                        int maxDistance, Set<Long> visited) {
        if (distance > maxDistance) {
            return NO_PATH;
        }

        long key = pos.toLong();
        if (!visited.add(key)) {
            return NO_PATH;
        }

        int best = NO_PATH;
        try {
            for (EnumFacing direction : EnumFacing.Plane.HORIZONTAL) {
                if (direction == cameFrom) {
                    continue;
                }

                BlockPos next = pos.offset(direction);
                if (!world.isBlockLoaded(next) || !canRouteThrough(world, next, fluidMaterial)) {
                    continue;
                }

                BlockPos below = next.down();
                if (!world.isBlockLoaded(below)) {
                    continue;
                }

                if (!BlockGeometryAdapter.isBlocked(world, below, fluidMaterial)) {
                    return distance;
                }

                if (distance < maxDistance) {
                    int candidate = getSlopeDistance(world, next, distance + 1,
                            direction.getOpposite(), fluidMaterial, maxDistance, visited);
                    if (candidate < best) {
                        best = candidate;
                    }
                }
            }
        } finally {
            visited.remove(key);
        }

        return best;
    }

    private static boolean canRouteThrough(World world, BlockPos pos, Material fluidMaterial) {
        IBlockState state = world.getBlockState(pos);
        Material targetMaterial = state.getMaterial();

        if (targetMaterial == fluidMaterial) {
            return state.getValue(BlockLiquid.LEVEL).intValue() > 0;
        }

        if (fluidMaterial == Material.WATER
                && WaterloggedBlockRegistry.canWaterlog(state.getBlock())) {
            return true;
        }

        if (fluidMaterial == Material.LAVA && targetMaterial == Material.WATER) {
            return true;
        }

        return !BlockGeometryAdapter.isBlocked(world, pos, fluidMaterial)
                && state.getBlock().isReplaceable(world, pos);
    }
}
