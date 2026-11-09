package com.dima16263690d.bleckshlng.fluid.render;

import java.util.ArrayList;
import java.util.List;

import com.dima16263690d.bleckshlng.fluid.api.IFluidOcclusionShapeProvider;
import com.dima16263690d.bleckshlng.fluid.geometry.FluidShapeCalculator;

import net.minecraft.block.BlockSlab;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.BlockTrapDoor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.material.Material;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/**
 * Forge 1.12.2 adapter for the modern face-occlusion shape contract.
 *
 * Physics continues to use FluidShapeCalculator's collision-space routines.
 * The renderer calls this class instead and consumes only 2D face rectangles.
 * Blocks with visual geometry that differs from collision can implement
 * IFluidOcclusionShapeProvider to supply the exact modern occlusion shape.
 */
public final class FluidOcclusionShapeAdapter {
    private static final double EPSILON = 1.0E-5D;

    private FluidOcclusionShapeAdapter() { }

    public static FluidOcclusionShape getFaceShape(
            IBlockAccess access, BlockPos pos, IBlockState state, EnumFacing face) {
        World world = access instanceof World ? (World) access : null;
        return getFaceShape(access, world, pos, state, face);
    }

    public static FluidOcclusionShape getFaceShape(
            IBlockAccess access, World world, BlockPos pos,
            IBlockState state, EnumFacing face) {
        if (access == null || pos == null || state == null || face == null) {
            return FluidOcclusionShape.EMPTY;
        }

        if (isTransparentFluidOverlayBlock(state)) {
            return FluidOcclusionShape.EMPTY;
        }

        if (state.getBlock() instanceof IFluidOcclusionShapeProvider) {
            FluidOcclusionShape provided =
                    ((IFluidOcclusionShapeProvider) state.getBlock())
                            .getFluidOcclusionShape(state, access, pos, face);
            return provided == null ? FluidOcclusionShape.EMPTY : provided;
        }

        BlockFaceShape faceShape = state.getBlock().getBlockFaceShape(
                access, state, pos, face);
        if (faceShape == BlockFaceShape.SOLID && state.isOpaqueCube()) {
            return FluidOcclusionShape.FULL;
        }

        // Slabs and trapdoors expose their own state-dependent block
        // bounds. Read those visual bounds directly instead of consulting
        // entity collision geometry.
        if (state.getBlock() instanceof BlockSlab
                || state.getBlock() instanceof BlockTrapDoor) {
            AxisAlignedBB visualBounds =
                    state.getBlock().getBoundingBox(state, access, pos);
            FluidOcclusionShape shape = fromLocalBounds(visualBounds, face);
            if (!shape.isEmpty()) {
                return shape;
            }
        }

        // Stairs have a composite shape in 1.12.2 but no public occlusion
        // shape. Their geometry is isolated in the platform fallback below;
        // mods can provide exact face geometry through IFluidOcclusionShapeProvider.
        boolean hasPartialVanillaShape =
                state.getBlock() instanceof BlockSlab
                || state.getBlock() instanceof BlockStairs
                || state.getBlock() instanceof BlockTrapDoor;

        if (!hasPartialVanillaShape && faceShape != BlockFaceShape.SOLID
                && !state.isOpaqueCube()) {
            return FluidOcclusionShape.EMPTY;
        }

        if (world == null || !world.isBlockLoaded(pos)) {
            return faceShape == BlockFaceShape.SOLID
                    ? FluidOcclusionShape.FULL : FluidOcclusionShape.EMPTY;
        }

        List<AxisAlignedBB> boxes =
                FluidShapeCalculator.getCollisionBoxes(world, pos, state);
        if (boxes.isEmpty()) {
            return faceShape == BlockFaceShape.SOLID
                    ? FluidOcclusionShape.FULL : FluidOcclusionShape.EMPTY;
        }

        List<FluidOcclusionRect> rectangles = new ArrayList<FluidOcclusionRect>();
        for (AxisAlignedBB box : boxes) {
            if (!touchesFace(box, pos, face)) {
                continue;
            }

            double minU;
            double maxU;
            if (face.getAxis() == EnumFacing.Axis.Z) {
                minU = box.minX - pos.getX();
                maxU = box.maxX - pos.getX();
            } else {
                minU = box.minZ - pos.getZ();
                maxU = box.maxZ - pos.getZ();
            }
            double minV = box.minY - pos.getY();
            double maxV = box.maxY - pos.getY();

            minU = clamp(minU);
            maxU = clamp(maxU);
            minV = clamp(minV);
            maxV = clamp(maxV);
            if (maxU - minU > EPSILON && maxV - minV > EPSILON) {
                rectangles.add(new FluidOcclusionRect(minU, maxU, minV, maxV));
            }
        }
        return rectangles.isEmpty()
                ? FluidOcclusionShape.EMPTY : new FluidOcclusionShape(rectangles);
    }

    public static boolean canOcclude(
            IBlockAccess access, BlockPos pos, IBlockState state, EnumFacing face) {
        return !getFaceShape(access, pos, state, face).isEmpty();
    }

    private static FluidOcclusionShape fromLocalBounds(
            AxisAlignedBB bounds, EnumFacing face) {
        if (bounds == null || face == null || !touchesLocalFace(bounds, face)) {
            return FluidOcclusionShape.EMPTY;
        }
        double minU;
        double maxU;
        if (face.getAxis() == EnumFacing.Axis.Z) {
            minU = bounds.minX;
            maxU = bounds.maxX;
        } else {
            minU = bounds.minZ;
            maxU = bounds.maxZ;
        }
        double minV = bounds.minY;
        double maxV = bounds.maxY;
        minU = clamp(minU);
        maxU = clamp(maxU);
        minV = clamp(minV);
        maxV = clamp(maxV);
        if (maxU - minU <= EPSILON || maxV - minV <= EPSILON) {
            return FluidOcclusionShape.EMPTY;
        }
        return FluidOcclusionShape.of(
                new FluidOcclusionRect(minU, maxU, minV, maxV));
    }

    private static boolean touchesLocalFace(AxisAlignedBB box, EnumFacing face) {
        switch (face) {
            case DOWN: return box.minY <= EPSILON;
            case UP: return box.maxY >= 1.0D - EPSILON;
            case NORTH: return box.minZ <= EPSILON;
            case SOUTH: return box.maxZ >= 1.0D - EPSILON;
            case WEST: return box.minX <= EPSILON;
            case EAST: return box.maxX >= 1.0D - EPSILON;
            default: return false;
        }
    }

    private static boolean isTransparentFluidOverlayBlock(IBlockState state) {
        return state.getBlock() instanceof net.minecraft.block.BlockLeaves
                || state.getMaterial() == Material.GLASS
                || state.getMaterial() == Material.ICE;
    }

    private static boolean touchesFace(
            AxisAlignedBB box, BlockPos pos, EnumFacing face) {
        switch (face) {
            case DOWN: return box.minY <= pos.getY() + EPSILON;
            case UP: return box.maxY >= pos.getY() + 1.0D - EPSILON;
            case NORTH: return box.minZ <= pos.getZ() + EPSILON;
            case SOUTH: return box.maxZ >= pos.getZ() + 1.0D - EPSILON;
            case WEST: return box.minX <= pos.getX() + EPSILON;
            case EAST: return box.maxX >= pos.getX() + 1.0D - EPSILON;
            default: return false;
        }
    }

    private static double clamp(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }
}
