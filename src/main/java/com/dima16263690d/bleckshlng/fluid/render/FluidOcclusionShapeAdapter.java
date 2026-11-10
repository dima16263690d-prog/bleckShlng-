package com.dima16263690d.bleckshlng.fluid.render;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

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
    private static final Method STAIRS_COLLISION_BOX_LIST = findStairsCollisionBoxList();

    private FluidOcclusionShapeAdapter() { }

    private static Method findStairsCollisionBoxList() {
        for (Method method : BlockStairs.class.getDeclaredMethods()) {
            Class<?>[] parameters = method.getParameterTypes();
            if (!Modifier.isStatic(method.getModifiers())
                    || !List.class.isAssignableFrom(method.getReturnType())
                    || parameters.length != 1
                    || parameters[0] != IBlockState.class) {
                continue;
            }
            try {
                method.setAccessible(true);
                return method;
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    private static List<AxisAlignedBB> getStairLocalBoxes(
            IBlockState state, IBlockAccess access, BlockPos pos) {
        if (STAIRS_COLLISION_BOX_LIST == null) {
            return java.util.Collections.emptyList();
        }
        try {
            IBlockState actualState = state.getActualState(access, pos);
            Object value = STAIRS_COLLISION_BOX_LIST.invoke(null, actualState);
            if (!(value instanceof List)) {
                return java.util.Collections.emptyList();
            }
            List<?> raw = (List<?>) value;
            List<AxisAlignedBB> boxes = new ArrayList<AxisAlignedBB>(raw.size());
            for (Object item : raw) {
                if (item instanceof AxisAlignedBB) {
                    boxes.add((AxisAlignedBB) item);
                }
            }
            return boxes;
        } catch (Throwable ignored) {
            return java.util.Collections.emptyList();
        }
    }

    private static FluidOcclusionShape fromLocalBoxes(
            List<AxisAlignedBB> boxes, EnumFacing face) {
        if (boxes == null || boxes.isEmpty() || face == null) {
            return FluidOcclusionShape.EMPTY;
        }
        List<FluidOcclusionRect> rectangles = new ArrayList<FluidOcclusionRect>();
        for (AxisAlignedBB box : boxes) {
            if (!touchesLocalFace(box, face)) {
                continue;
            }
            double minU;
            double maxU;
            double minV;
            double maxV;
            if (face.getAxis() == EnumFacing.Axis.Y) {
                minU = box.minX; maxU = box.maxX;
                minV = box.minZ; maxV = box.maxZ;
            } else if (face.getAxis() == EnumFacing.Axis.Z) {
                minU = box.minX; maxU = box.maxX;
                minV = box.minY; maxV = box.maxY;
            } else {
                minU = box.minZ; maxU = box.maxZ;
                minV = box.minY; maxV = box.maxY;
            }
            minU = clamp(minU); maxU = clamp(maxU);
            minV = clamp(minV); maxV = clamp(maxV);
            if (maxU - minU > EPSILON && maxV - minV > EPSILON) {
                rectangles.add(new FluidOcclusionRect(minU, maxU, minV, maxV));
            }
        }
        return rectangles.isEmpty()
                ? FluidOcclusionShape.EMPTY : new FluidOcclusionShape(rectangles);
    }

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

        // Stairs are composite voxel shapes. Read their state-only boxes from
        // the same IBlockAccess snapshot used by the chunk compiler.
        if (state.getBlock() instanceof BlockStairs) {
            return fromLocalBoxes(getStairLocalBoxes(state, access, pos), face);
        }

        // Slabs and trapdoors expose state-dependent bounds via IBlockAccess.
        if (state.getBlock() instanceof BlockSlab
                || state.getBlock() instanceof BlockTrapDoor) {
            AxisAlignedBB visualBounds =
                    state.getBlock().getBoundingBox(state, access, pos);
            FluidOcclusionShape shape = fromLocalBounds(visualBounds, face);
            if (!shape.isEmpty()) {
                return shape;
            }
        }

        // Unknown partial blocks are non-occluding unless they implement
        // IFluidOcclusionShapeProvider; do not guess from collision boxes.
        boolean hasPartialVanillaShape =
                state.getBlock() instanceof BlockSlab
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
            double minV;
            double maxV;
            if (face.getAxis() == EnumFacing.Axis.Y) {
                // Horizontal faces are projected onto the X/Z plane.
                // The previous implementation used Y as the second axis,
                // turning a full slab face into a narrow stripe.
                minU = box.minX - pos.getX();
                maxU = box.maxX - pos.getX();
                minV = box.minZ - pos.getZ();
                maxV = box.maxZ - pos.getZ();
            } else if (face.getAxis() == EnumFacing.Axis.Z) {
                minU = box.minX - pos.getX();
                maxU = box.maxX - pos.getX();
                minV = box.minY - pos.getY();
                maxV = box.maxY - pos.getY();
            } else {
                minU = box.minZ - pos.getZ();
                maxU = box.maxZ - pos.getZ();
                minV = box.minY - pos.getY();
                maxV = box.maxY - pos.getY();
            }

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

    /**
     * Tests whether the union of face rectangles covers the requested
     * rectangle. This is the 2-D equivalent of the face-slice comparison
     * used by Minecraft 1.18.2 Shapes.blockOccudes().
     */
    public static boolean isFaceRectangleCovered(FluidOcclusionShape shape,
                                                   double minU, double maxU,
                                                   double minV, double maxV) {
        if (shape == null || shape.isEmpty()
                || maxU - minU <= EPSILON || maxV - minV <= EPSILON) {
            return false;
        }

        TreeSet<Double> uEdges = new TreeSet<Double>();
        TreeSet<Double> vEdges = new TreeSet<Double>();
        uEdges.add(Double.valueOf(minU));
        uEdges.add(Double.valueOf(maxU));
        vEdges.add(Double.valueOf(minV));
        vEdges.add(Double.valueOf(maxV));

        for (FluidOcclusionRect rectangle : shape.getRectangles()) {
            double clippedMinU = Math.max(minU, rectangle.getMinU());
            double clippedMaxU = Math.min(maxU, rectangle.getMaxU());
            double clippedMinV = Math.max(minV, rectangle.getMinV());
            double clippedMaxV = Math.min(maxV, rectangle.getMaxV());
            if (clippedMaxU - clippedMinU <= EPSILON
                    || clippedMaxV - clippedMinV <= EPSILON) {
                continue;
            }
            uEdges.add(Double.valueOf(clippedMinU));
            uEdges.add(Double.valueOf(clippedMaxU));
            vEdges.add(Double.valueOf(clippedMinV));
            vEdges.add(Double.valueOf(clippedMaxV));
        }

        Double[] us = uEdges.toArray(new Double[uEdges.size()]);
        Double[] vs = vEdges.toArray(new Double[vEdges.size()]);
        for (int u = 0; u + 1 < us.length; u++) {
            double middleU = (us[u].doubleValue() + us[u + 1].doubleValue()) * 0.5D;
            for (int v = 0; v + 1 < vs.length; v++) {
                double middleV = (vs[v].doubleValue() + vs[v + 1].doubleValue()) * 0.5D;
                boolean covered = false;
                for (FluidOcclusionRect rectangle : shape.getRectangles()) {
                    if (middleU >= rectangle.getMinU() - EPSILON
                            && middleU <= rectangle.getMaxU() + EPSILON
                            && middleV >= rectangle.getMinV() - EPSILON
                            && middleV <= rectangle.getMaxV() + EPSILON) {
                        covered = true;
                        break;
                    }
                }
                if (!covered) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Returns true only when the union covers the complete unit face. */
    public static boolean isFaceFullyCovered(FluidOcclusionShape shape) {
        return isFaceRectangleCovered(shape, 0.0D, 1.0D, 0.0D, 1.0D);
    }

    /**
     * Port of LiquidBlockRenderer.isFaceOccludedByState from 1.18.2.
     *
     * A liquid occupies a full X/Z column up to maxFluidHeight. On UP it
     * touches the cell boundary only when the surface reaches height 1;
     * DOWN always tests the full bottom face. Horizontal faces test the
     * projected liquid side from y=0 to its highest corner.
     */
    public static boolean isFaceOccludedByState(
            IBlockAccess access, World world, BlockPos pos,
            IBlockState state, EnumFacing direction, float maxFluidHeight) {
        if (access == null || pos == null || state == null || direction == null) {
            return false;
        }

        FluidOcclusionShape shape = getFaceShape(
                access, world, pos, state, direction.getOpposite());
        if (shape == null || shape.isEmpty()) {
            return false;
        }

        if (direction == EnumFacing.UP) {
            if (maxFluidHeight < 1.0F - 1.0E-7F) {
                return false;
            }
            return isFaceFullyCovered(shape);
        }
        if (direction == EnumFacing.DOWN) {
            return isFaceFullyCovered(shape);
        }

        double top = Math.max(0.0D, Math.min(1.0D, (double) maxFluidHeight));
        return isFaceRectangleCovered(shape, 0.0D, 1.0D, 0.0D, top);
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
        double minV;
        double maxV;
        if (face.getAxis() == EnumFacing.Axis.Y) {
            // UP/DOWN face coordinates are X/Z, not Z/Y.
            minU = bounds.minX;
            maxU = bounds.maxX;
            minV = bounds.minZ;
            maxV = bounds.maxZ;
        } else if (face.getAxis() == EnumFacing.Axis.Z) {
            minU = bounds.minX;
            maxU = bounds.maxX;
            minV = bounds.minY;
            maxV = bounds.maxY;
        } else {
            minU = bounds.minZ;
            maxU = bounds.maxZ;
            minV = bounds.minY;
            maxV = bounds.maxY;
        }
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
