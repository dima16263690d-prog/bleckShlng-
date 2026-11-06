package com.dima16263690d.bleckshlng.fluid.physics;

import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import com.dima16263690d.bleckshlng.fluid.state.FluidStateBridge;
import com.dima16263690d.bleckshlng.fluid.state.FluidHeightCalculator;
import com.dima16263690d.bleckshlng.fluid.state.FluidStateMapper;

/**
 * Flow vector adapted from 1.18.2 FlowingFluid.getFlow().
 * Uses one immutable fluid-state read per neighbor and never loads chunks.
 */
public final class FluidFlowVector {
    private static final float FALLING_HEIGHT_OFFSET = 0.8888889F;

    private FluidFlowVector() {
    }

    public static Vec3d calculate(World world, BlockPos pos, IBlockState currentBlockState) {
        if (world == null || pos == null || currentBlockState == null) {
            return Vec3d.ZERO;
        }

        FluidStateBridge current = FluidStateMapper.fromWorld(world, pos, currentBlockState);
        if (current == null) {
            return Vec3d.ZERO;
        }

        double flowX = 0.0D;
        double flowZ = 0.0D;
        float currentHeight = FluidHeightCalculator.getOwnHeight(current);

        for (EnumFacing direction : EnumFacing.Plane.HORIZONTAL) {
            BlockPos neighborPos = pos.offset(direction);
            if (!world.isBlockLoaded(neighborPos)) {
                continue;
            }

            IBlockState neighborBlock = world.getBlockState(neighborPos);
            FluidStateBridge neighbor = FluidStateMapper.fromWorld(
                    world, neighborPos, neighborBlock);

            // Modern affectsFlow: empty or same fluid; different fluids are ignored.
            if (neighbor != null && !current.isSameKind(neighbor)) {
                continue;
            }

            float neighborHeight = neighbor == null
                    ? 0.0F : FluidHeightCalculator.getOwnHeight(neighbor);
            float difference = 0.0F;

            if (neighborHeight == 0.0F) {
                if (!neighborBlock.getMaterial().blocksMovement()) {
                    BlockPos belowPos = neighborPos.down();
                    if (world.isBlockLoaded(belowPos)) {
                        FluidStateBridge below = FluidStateMapper.fromWorld(
                                world, belowPos, world.getBlockState(belowPos));
                        if (current.isSameKind(below)) {
                            neighborHeight = FluidHeightCalculator.getOwnHeight(below);
                            if (neighborHeight > 0.0F) {
                                difference = currentHeight
                                        - (neighborHeight - FALLING_HEIGHT_OFFSET);
                            }
                        }
                    }
                }
            } else {
                difference = currentHeight - neighborHeight;
            }

            flowX += direction.getFrontOffsetX() * (double) difference;
            flowZ += direction.getFrontOffsetZ() * (double) difference;
        }

        Vec3d flow = new Vec3d(flowX, 0.0D, flowZ);

        if (current.isFalling()) {
            for (EnumFacing direction : EnumFacing.Plane.HORIZONTAL) {
                BlockPos side = pos.offset(direction);
                if (!world.isBlockLoaded(side)) {
                    continue;
                }

                if (isSolidFace(world, side, direction)
                        || (world.isBlockLoaded(side.up())
                        && isSolidFace(world, side.up(), direction))) {
                    flow = flow.normalize().addVector(0.0D, -6.0D, 0.0D);
                    break;
                }
            }
        }

        return flow.normalize();
    }

    private static boolean isSolidFace(World world, BlockPos pos, EnumFacing face) {
        IBlockState state = world.getBlockState(pos);
        FluidStateBridge fluid = FluidStateMapper.fromWorld(world, pos, state);

        if (fluid != null && fluid.getKind() == FluidStateBridge.FluidKind.WATER
                && state.getMaterial() == Material.WATER) {
            return false;
        }

        if (face == EnumFacing.UP) {
            return true;
        }

        if (state.getMaterial() == Material.ICE) {
            return false;
        }

        return state.getBlockFaceShape(world, pos, face) == BlockFaceShape.SOLID;
    }
}
