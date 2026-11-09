package com.dima16263690d.bleckshlng.fluid.engine;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import com.dima16263690d.bleckshlng.fluid.state.FluidStateBridge;
import com.dima16263690d.bleckshlng.fluid.state.FluidStateMapper;

/** FlowingFluid shortest-slope search using the legacy 1.12.2 block metadata. */
public final class FlowDirectionSearch {
    private static final int NO_PATH = 1000;
    private FlowDirectionSearch() { }

    public static Set<EnumFacing> getBestDirections(World world, BlockPos origin,
                                                     Material fluidMaterial, int maxDistance) {
        int bestDistance = NO_PATH;
        Set<EnumFacing> result = EnumSet.noneOf(EnumFacing.class);
        for (EnumFacing direction : EnumFacing.Plane.HORIZONTAL) {
            BlockPos neighbor = origin.offset(direction);
            if (!world.isBlockLoaded(neighbor)
                    || !FluidSpreadEngine.canSpreadThrough(world, origin, direction, fluidMaterial)
                    || !canRouteThrough(world, neighbor, fluidMaterial, direction)) continue;
            int distance;
            if (isWaterHole(world, neighbor, fluidMaterial)) {
                distance = 0;
            } else {
                distance = getSlopeDistance(world, neighbor, 1, direction.getOpposite(),
                        fluidMaterial, maxDistance, new HashSet<Long>());
            }
            if (distance < bestDistance) {
                result.clear();
                bestDistance = distance;
            }
            // Match FlowingFluid.getSpread(): directions with no reachable
            // drop-off (distance == 1000) are still valid. On a flat floor,
            // all equally distant directions must remain candidates.
            if (distance <= bestDistance) result.add(direction);
        }
        return result;
    }

    private static int getSlopeDistance(World world, BlockPos pos, int distance,
                                        EnumFacing cameFrom, Material fluidMaterial,
                                        int maxDistance, Set<Long> visited) {
        if (distance > maxDistance) return NO_PATH;
        long key = pos.toLong();
        if (!visited.add(key)) return NO_PATH;
        int best = NO_PATH;
        try {
            for (EnumFacing direction : EnumFacing.Plane.HORIZONTAL) {
                if (direction == cameFrom) continue;
                BlockPos next = pos.offset(direction);
                if (!world.isBlockLoaded(next)
                        || !FluidSpreadEngine.canSpreadThrough(world, pos, direction, fluidMaterial)
                        || !canRouteThrough(world, next, fluidMaterial, direction)) continue;
                if (isWaterHole(world, next, fluidMaterial)) return distance;
                if (distance < maxDistance) {
                    int candidate = getSlopeDistance(world, next, distance + 1,
                            direction.getOpposite(), fluidMaterial, maxDistance, visited);
                    if (candidate < best) best = candidate;
                }
            }
        } finally {
            visited.remove(key);
        }
        return best;
    }

    private static boolean isWaterHole(World world, BlockPos pos, Material fluidMaterial) {
        BlockPos below = pos.down();
        if (!world.isBlockLoaded(below)
                || !FluidSpreadEngine.canSpreadThrough(world, pos, EnumFacing.DOWN, fluidMaterial)) return false;
        IBlockState state = world.getBlockState(below);
        FluidStateBridge existingFluid = FluidStateMapper.fromWorld(world, below, state);
        FluidStateBridge.FluidKind expectedKind = fluidMaterial == Material.WATER
                ? FluidStateBridge.FluidKind.WATER : FluidStateBridge.FluidKind.LAVA;
        if (existingFluid != null && existingFluid.getKind() == expectedKind) {
            return true;
        }
        return com.dima16263690d.bleckshlng.fluid.geometry.BlockGeometryAdapter
                .canHoldFluid(world, below, state, fluidMaterial);
    }

    private static boolean canRouteThrough(World world, BlockPos pos,
                                           Material fluidMaterial, EnumFacing direction) {
        IBlockState state = world.getBlockState(pos);
        Material targetMaterial = state.getMaterial();
        if (targetMaterial == fluidMaterial) {
            return state.getBlock() instanceof BlockLiquid
                    && state.getValue(BlockLiquid.LEVEL).intValue() > 0;
        }
        // FlowingFluid#getSlopeDistance uses canPassThrough(), whose
        // destination check is canHoldFluid(), not canSpreadTo() and therefore
        // does not apply directional replacement/height restrictions.
        return com.dima16263690d.bleckshlng.fluid.geometry.BlockGeometryAdapter
                .canHoldFluid(world, pos, state, fluidMaterial);
    }
}
