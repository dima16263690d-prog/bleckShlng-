package com.dima16263690d.bleckshlng.fluid.render;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import com.dima16263690d.bleckshlng.fluid.api.IFluidOcclusionShapeProvider;

import net.minecraft.block.Block;
import net.minecraft.block.BlockChest;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.BlockEnderChest;
import net.minecraft.block.BlockFence;
import net.minecraft.block.BlockLadder;
import net.minecraft.block.BlockPane;
import net.minecraft.block.BlockSign;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.BlockTrapDoor;
import net.minecraft.block.BlockWall;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.block.material.Material;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/**
 * Snapshot-only 1.12.2 adapter for LiquidBlockRenderer's independent
 * face-occlusion geometry. It never substitutes entity collision boxes for
 * a face shape during chunk compilation.
 */
public final class FluidOcclusionShapeAdapter {
    private static final double EPSILON = 1.0E-5D;
    private static final Method STAIRS_COLLISION_BOX_LIST = findStairsCollisionBoxList();

    private FluidOcclusionShapeAdapter() { }

    private static Method findStairsCollisionBoxList() {
        Method fallback = null;
        for (Method method : BlockStairs.class.getDeclaredMethods()) {
            Class<?>[] parameters = method.getParameterTypes();
            if (!Modifier.isStatic(method.getModifiers())
                    || !List.class.isAssignableFrom(method.getReturnType())
                    || parameters.length != 1
                    || parameters[0] != IBlockState.class) {
                continue;
            }
            if ("getCollisionBoxList".equals(method.getName())) {
                try {
                    method.setAccessible(true);
                    return method;
                } catch (Throwable ignored) {
                    return null;
                }
            }
            fallback = method;
        }
        if (fallback != null) {
            try {
                fallback.setAccessible(true);
                return fallback;
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    private static List<AxisAlignedBB> getStairLocalBoxes(
            IBlockState state, IBlockAccess access, BlockPos pos) {
        if (STAIRS_COLLISION_BOX_LIST == null) return Collections.emptyList();
        try {
            IBlockState actual = state.getActualState(access, pos);
            Object value = STAIRS_COLLISION_BOX_LIST.invoke(null, actual);
            if (!(value instanceof List)) return Collections.emptyList();
            List<AxisAlignedBB> boxes = new ArrayList<AxisAlignedBB>();
            for (Object item : (List<?>) value) {
                if (item instanceof AxisAlignedBB) boxes.add((AxisAlignedBB) item);
            }
            return boxes;
        } catch (Throwable ignored) {
            return Collections.emptyList();
        }
    }

    private static boolean propertyIsTrue(IBlockState state, String name) {
        for (Map.Entry<IProperty<?>, Comparable<?>> entry
                : state.getProperties().entrySet()) {
            if (name.equals(entry.getKey().getName())) {
                return Boolean.TRUE.equals(entry.getValue());
            }
        }
        return false;
    }

    /**
     * BlockWall connections are EnumWallDirection values (NONE/LOW/TALL),
     * unlike fence and pane connections, which are Boolean properties.
     */
    private static boolean propertyIsConnected(IBlockState state, String name) {
        for (Map.Entry<IProperty<?>, Comparable<?>> entry
                : state.getProperties().entrySet()) {
            if (!name.equals(entry.getKey().getName())) continue;
            Object value = entry.getValue();
            if (value instanceof Boolean) return ((Boolean) value).booleanValue();
            String serialized = String.valueOf(value);
            return !"none".equalsIgnoreCase(serialized)
                    && !"false".equalsIgnoreCase(serialized);
        }
        return false;
    }

    private static List<AxisAlignedBB> getCompositeLocalBoxes(
            Block block, IBlockState state, IBlockAccess access, BlockPos pos) {
        IBlockState actual;
        try {
            actual = state.getActualState(access, pos);
        } catch (Throwable ignored) {
            actual = state;
        }
        List<AxisAlignedBB> boxes = new ArrayList<AxisAlignedBB>();
        if (block instanceof BlockFence) {
            boxes.add(new AxisAlignedBB(0.375D, 0.0D, 0.375D,
                    0.625D, 1.5D, 0.625D));
            addFenceArm(boxes, actual, "north", 0);
            addFenceArm(boxes, actual, "east", 1);
            addFenceArm(boxes, actual, "south", 2);
            addFenceArm(boxes, actual, "west", 3);
            return boxes;
        }
        if (block instanceof BlockPane) {
            boxes.add(new AxisAlignedBB(0.4375D, 0.0D, 0.4375D,
                    0.5625D, 1.0D, 0.5625D));
            addPaneArm(boxes, actual, "north", 0);
            addPaneArm(boxes, actual, "east", 1);
            addPaneArm(boxes, actual, "south", 2);
            addPaneArm(boxes, actual, "west", 3);
            return boxes;
        }
        if (block instanceof BlockWall) {
            boxes.add(new AxisAlignedBB(0.25D, 0.0D, 0.25D,
                    0.75D, 1.0D, 0.75D));
            addWallArm(boxes, actual, "north", 0);
            addWallArm(boxes, actual, "east", 1);
            addWallArm(boxes, actual, "south", 2);
            addWallArm(boxes, actual, "west", 3);
            return boxes;
        }
        return boxes;
    }

    private static void addFenceArm(List<AxisAlignedBB> boxes, IBlockState state,
                                    String side, int direction) {
        if (!propertyIsTrue(state, side)) return;
        double x0 = direction == 1 ? 0.5D : direction == 3 ? 0.0D : 0.375D;
        double x1 = direction == 1 ? 1.0D : direction == 3 ? 0.5D : 0.625D;
        double z0 = direction == 0 ? 0.0D : direction == 2 ? 0.5D : 0.375D;
        double z1 = direction == 0 ? 0.5D : direction == 2 ? 1.0D : 0.625D;
        boxes.add(new AxisAlignedBB(x0, 0.375D, z0, x1, 0.5625D, z1));
        boxes.add(new AxisAlignedBB(x0, 0.75D, z0, x1, 0.9375D, z1));
    }

    private static void addPaneArm(List<AxisAlignedBB> boxes, IBlockState state,
                                   String side, int direction) {
        if (!propertyIsTrue(state, side)) return;
        switch (direction) {
            case 0: boxes.add(new AxisAlignedBB(0.4375D, 0.0D, 0.0D,
                    0.5625D, 1.0D, 0.5D)); break;
            case 1: boxes.add(new AxisAlignedBB(0.5D, 0.0D, 0.4375D,
                    1.0D, 1.0D, 0.5625D)); break;
            case 2: boxes.add(new AxisAlignedBB(0.4375D, 0.0D, 0.5D,
                    0.5625D, 1.0D, 1.0D)); break;
            default: boxes.add(new AxisAlignedBB(0.0D, 0.0D, 0.4375D,
                    0.5D, 1.0D, 0.5625D)); break;
        }
    }

    private static void addWallArm(List<AxisAlignedBB> boxes, IBlockState state,
                                   String side, int direction) {
        if (!propertyIsConnected(state, side)) return;
        switch (direction) {
            case 0: boxes.add(new AxisAlignedBB(0.25D, 0.25D, 0.0D,
                    0.75D, 0.75D, 0.5D)); break;
            case 1: boxes.add(new AxisAlignedBB(0.5D, 0.25D, 0.25D,
                    1.0D, 0.75D, 0.75D)); break;
            case 2: boxes.add(new AxisAlignedBB(0.25D, 0.25D, 0.5D,
                    0.75D, 0.75D, 1.0D)); break;
            default: boxes.add(new AxisAlignedBB(0.0D, 0.25D, 0.25D,
                    0.5D, 0.75D, 0.75D)); break;
        }
    }

    public static FluidOcclusionShape getFaceShape(
            IBlockAccess access, BlockPos pos, IBlockState state, EnumFacing face) {
        return getFaceShape(access, null, pos, state, face);
    }

    /**
     * Compatibility overload. The World parameter is intentionally ignored:
     * mesh occlusion is calculated exclusively from the IBlockAccess snapshot.
     */
    @Deprecated
    public static FluidOcclusionShape getFaceShape(
            IBlockAccess access, World ignoredWorld, BlockPos pos,
            IBlockState state, EnumFacing face) {
        if (access == null || pos == null || state == null || face == null) {
            return FluidOcclusionShape.EMPTY;
        }
        if (isTransparentFluidOverlayBlock(state)) return FluidOcclusionShape.EMPTY;

        IBlockState actual = state;
        Block block = state.getBlock();
        if (block instanceof BlockStairs || block instanceof BlockFence
                || block instanceof BlockPane || block instanceof BlockWall
                || block instanceof BlockDoor || block instanceof BlockSlab
                || block instanceof BlockTrapDoor) {
            try {
                actual = state.getActualState(access, pos);
            } catch (Throwable ignored) {
                actual = state;
            }
        }

        if (actual.getBlock() instanceof IFluidOcclusionShapeProvider) {
            FluidOcclusionShape supplied =
                    ((IFluidOcclusionShapeProvider) actual.getBlock())
                            .getFluidOcclusionShape(actual, access, pos, face);
            return supplied == null ? FluidOcclusionShape.EMPTY : supplied;
        }

        block = actual.getBlock();
        if (block instanceof BlockStairs) {
            return fromLocalBoxes(getStairLocalBoxes(actual, access, pos), face);
        }
        if (block instanceof BlockFence || block instanceof BlockPane
                || block instanceof BlockWall) {
            return fromLocalBoxes(
                    getCompositeLocalBoxes(block, actual, access, pos), face);
        }
        if (block instanceof BlockSlab || block instanceof BlockTrapDoor
                || block instanceof BlockDoor || block instanceof BlockLadder
                || block instanceof BlockSign || block instanceof BlockChest
                || block instanceof BlockEnderChest) {
            return fromLocalBounds(block.getBoundingBox(actual, access, pos), face);
        }

        if (actual.isOpaqueCube() && actual.isFullBlock()
                && actual.doesSideBlockRendering(access, pos, face)) {
            return FluidOcclusionShape.FULL;
        }
        return FluidOcclusionShape.EMPTY;
    }

    /** Projects independent local AABBs to a normalized 2-D face shape. */
    public static FluidOcclusionShape fromLocalBoxes(
            List<AxisAlignedBB> boxes, EnumFacing face) {
        if (boxes == null || boxes.isEmpty() || face == null) {
            return FluidOcclusionShape.EMPTY;
        }
        List<FluidOcclusionRect> rectangles = new ArrayList<FluidOcclusionRect>();
        for (AxisAlignedBB box : boxes) {
            if (!touchesLocalFace(box, face)) continue;
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

    /** Exact rectangle-union test corresponding to Shapes.blockOccudes. */
    public static boolean isFaceRectangleCovered(FluidOcclusionShape shape,
                                                   double minU, double maxU,
                                                   double minV, double maxV) {
        if (shape == null || shape.isEmpty()
                || maxU - minU <= EPSILON || maxV - minV <= EPSILON) return false;

        TreeSet<Double> uEdges = new TreeSet<Double>();
        TreeSet<Double> vEdges = new TreeSet<Double>();
        uEdges.add(Double.valueOf(minU)); uEdges.add(Double.valueOf(maxU));
        vEdges.add(Double.valueOf(minV)); vEdges.add(Double.valueOf(maxV));
        for (FluidOcclusionRect rectangle : shape.getRectangles()) {
            double u0 = Math.max(minU, rectangle.getMinU());
            double u1 = Math.min(maxU, rectangle.getMaxU());
            double v0 = Math.max(minV, rectangle.getMinV());
            double v1 = Math.min(maxV, rectangle.getMaxV());
            if (u1 - u0 > EPSILON && v1 - v0 > EPSILON) {
                uEdges.add(Double.valueOf(u0)); uEdges.add(Double.valueOf(u1));
                vEdges.add(Double.valueOf(v0)); vEdges.add(Double.valueOf(v1));
            }
        }
        Double[] us = uEdges.toArray(new Double[uEdges.size()]);
        Double[] vs = vEdges.toArray(new Double[vEdges.size()]);
        for (int u = 0; u + 1 < us.length; u++) {
            double midU = (us[u].doubleValue() + us[u + 1].doubleValue()) * 0.5D;
            for (int v = 0; v + 1 < vs.length; v++) {
                double midV = (vs[v].doubleValue() + vs[v + 1].doubleValue()) * 0.5D;
                boolean covered = false;
                for (FluidOcclusionRect rectangle : shape.getRectangles()) {
                    if (midU >= rectangle.getMinU() - EPSILON
                            && midU <= rectangle.getMaxU() + EPSILON
                            && midV >= rectangle.getMinV() - EPSILON
                            && midV <= rectangle.getMaxV() + EPSILON) {
                        covered = true;
                        break;
                    }
                }
                if (!covered) return false;
            }
        }
        return true;
    }

    public static boolean isFaceFullyCovered(FluidOcclusionShape shape) {
        return isFaceRectangleCovered(shape, 0.0D, 1.0D, 0.0D, 1.0D);
    }

    /**
     * Tests the liquid prism against the opposing face, mirroring the
     * direction and height inputs used by LiquidBlockRenderer.
     */
    public static boolean isFluidFaceOccluded(
            FluidOcclusionShape opposingFace, EnumFacing direction,
            float maxFluidHeight) {
        if (opposingFace == null || opposingFace.isEmpty()
                || direction == null || Float.isNaN(maxFluidHeight)) return false;
        if (direction == EnumFacing.UP) {
            return maxFluidHeight >= 1.0F - 1.0E-7F
                    && isFaceFullyCovered(opposingFace);
        }
        if (direction == EnumFacing.DOWN) return isFaceFullyCovered(opposingFace);
        double top = Math.max(0.0D, Math.min(1.0D, (double) maxFluidHeight));
        return isFaceRectangleCovered(opposingFace, 0.0D, 1.0D, 0.0D, top);
    }

    /**
     * Clips a fluid column against a simple host AABB. Water in an upper
     * slab occupies the lower half of the cell; the source FluidState still
     * reports its normal 8/9 height, so the renderer must cap that surface at
     * the bottom of the slab. Lower slabs leave the upper cavity available.
     */
    public static float clipFluidHeightToShape(float nominalHeight,
                                                AxisAlignedBB localShape,
                                                double localX, double localZ) {
        if (Float.isNaN(nominalHeight) || nominalHeight <= 0.0F) {
            return 0.0F;
        }
        float height = Math.min(1.0F, nominalHeight);
        if (localShape == null
                || localX < localShape.minX - EPSILON
                || localX > localShape.maxX + EPSILON
                || localZ < localShape.minZ - EPSILON
                || localZ > localShape.maxZ + EPSILON) {
            return height;
        }

        // If the host fills this entire sampled column up to the fluid
        // surface, no fluid surface is visible in that column.
        if (localShape.minY <= EPSILON
                && localShape.maxY >= height - EPSILON) {
            return 0.0F;
        }

        // An upper slab seals the upper part of the cell. Water stored in it
        // is visible only in the cavity below the slab.
        if (localShape.minY > EPSILON
                && localShape.minY < height
                && localShape.maxY >= 1.0D - EPSILON) {
            return (float) localShape.minY;
        }
        return height;
    }

    /**
     * True when the fluid surface lies inside the solid part of an upper slab.
     * The fluid's real height must remain unchanged for side geometry and flow;
     * only its hidden top quad is suppressed.
     */
    public static boolean isFluidSurfaceBuriedBySlab(
            IBlockAccess access, BlockPos pos, IBlockState state,
            float nominalHeight) {
        if (access == null || pos == null || state == null
                || !(state.getBlock() instanceof BlockSlab)
                || Float.isNaN(nominalHeight) || nominalHeight <= 0.0F) {
            return false;
        }
        IBlockState actual = state;
        try {
            actual = state.getActualState(access, pos);
        } catch (Throwable ignored) {
            // Some modded slabs do not support actual-state resolution.
        }
        AxisAlignedBB bounds = actual.getBlock().getBoundingBox(actual, access, pos);
        float clipped = clipFluidHeightToShape(
                nominalHeight, bounds, 0.5D, 0.5D);
        return clipped + EPSILON < nominalHeight;
    }

    /**
     * Applies slab-only height clipping using the current chunk snapshot.
     * Other waterloggable shapes (fences, stairs, panes) retain the fluid
     * height and rely on their face geometry, as in 1.18.2.
     */
    public static float clipSlabFluidSurfaceHeight(
            IBlockAccess access, BlockPos pos, IBlockState state,
            float nominalHeight) {
        if (access == null || pos == null || state == null
                || !(state.getBlock() instanceof BlockSlab)) {
            return nominalHeight;
        }
        IBlockState actual = state;
        try {
            actual = state.getActualState(access, pos);
        } catch (Throwable ignored) {
            // Some modded slabs do not support actual-state resolution.
        }
        AxisAlignedBB bounds = actual.getBlock().getBoundingBox(actual, access, pos);
        return clipFluidHeightToShape(nominalHeight, bounds, 0.5D, 0.5D);
    }

    public static boolean isFaceOccludedByState(
            IBlockAccess access, World ignoredWorld, BlockPos pos,
            IBlockState state, EnumFacing direction, float maxFluidHeight) {
        if (access == null || pos == null || state == null || direction == null) {
            return false;
        }
        FluidOcclusionShape opposingFace = getFaceShape(
                access, null, pos, state, direction.getOpposite());
        return isFluidFaceOccluded(opposingFace, direction, maxFluidHeight);
    }

    public static boolean canOcclude(
            IBlockAccess access, BlockPos pos, IBlockState state, EnumFacing face) {
        return !getFaceShape(access, pos, state, face).isEmpty();
    }

    private static FluidOcclusionShape fromLocalBounds(
            AxisAlignedBB bounds, EnumFacing face) {
        if (bounds == null || !touchesLocalFace(bounds, face)) {
            return FluidOcclusionShape.EMPTY;
        }
        double minU;
        double maxU;
        double minV;
        double maxV;
        if (face.getAxis() == EnumFacing.Axis.Y) {
            minU = bounds.minX; maxU = bounds.maxX;
            minV = bounds.minZ; maxV = bounds.maxZ;
        } else if (face.getAxis() == EnumFacing.Axis.Z) {
            minU = bounds.minX; maxU = bounds.maxX;
            minV = bounds.minY; maxV = bounds.maxY;
        } else {
            minU = bounds.minZ; maxU = bounds.maxZ;
            minV = bounds.minY; maxV = bounds.maxY;
        }
        minU = clamp(minU); maxU = clamp(maxU);
        minV = clamp(minV); maxV = clamp(maxV);
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

    private static double clamp(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }
}
