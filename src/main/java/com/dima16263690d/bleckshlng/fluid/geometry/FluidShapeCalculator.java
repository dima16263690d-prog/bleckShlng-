package com.dima16263690d.bleckshlng.fluid.geometry;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Reads actual collision boxes for fluid pass-through decisions. */
public final class FluidShapeCalculator {
    private static final int FACE_SAMPLES = 8;
    private static final int VOLUME_SAMPLES = 6;
    private static final double EPSILON = 1.0E-5D;

    private FluidShapeCalculator() { }

    public static List<AxisAlignedBB> getCollisionBoxes(World world, BlockPos pos, IBlockState state) {
        if (world == null || pos == null || state == null || !world.isBlockLoaded(pos)) {
            return new ArrayList<AxisAlignedBB>(0);
        }
        List<AxisAlignedBB> cached = FluidOcclusionCache.getCollisionBoxes(world, pos);
        if (cached != null) return cached;

        List<AxisAlignedBB> boxes = new ArrayList<AxisAlignedBB>(4);
        AxisAlignedBB mask = new AxisAlignedBB(pos.getX(), pos.getY(), pos.getZ(),
                pos.getX() + 1.0D, pos.getY() + 1.0D, pos.getZ() + 1.0D);
        state.getBlock().addCollisionBoxToList(state, world, pos, mask, boxes, (Entity) null, true);
        FluidOcclusionCache.putCollisionBoxes(world, pos, boxes);
        return FluidOcclusionCache.getCollisionBoxes(world, pos);
    }

    public static boolean isFaceFullyCovered(World world, BlockPos pos, IBlockState state, EnumFacing face) {
        Boolean cached = FluidOcclusionCache.getFaceCoverage(world, pos, face);
        if (cached != null) return cached.booleanValue();
        List<AxisAlignedBB> boxes = getCollisionBoxes(world, pos, state);
        if (boxes.isEmpty()) {
            FluidOcclusionCache.putFaceCoverage(world, pos, face, false);
            return false;
        }
        for (int u = 0; u < FACE_SAMPLES; u++) {
            for (int v = 0; v < FACE_SAMPLES; v++) {
                if (!facePointCovered(boxes, pos, face,
                        (u + 0.5D) / FACE_SAMPLES, (v + 0.5D) / FACE_SAMPLES)) {
                    FluidOcclusionCache.putFaceCoverage(world, pos, face, false);
                    return false;
                }
            }
        }
        FluidOcclusionCache.putFaceCoverage(world, pos, face, true);
        return true;
    }

    public static boolean isMergedFaceOccluded(World world, BlockPos pos, EnumFacing direction) {
        Boolean cached = FluidOcclusionCache.getMergedFace(world, pos, direction);
        if (cached != null) return cached.booleanValue();
        BlockPos otherPos = pos.offset(direction);
        if (!world.isBlockLoaded(pos) || !world.isBlockLoaded(otherPos)) {
            FluidOcclusionCache.putMergedFace(world, pos, direction, true);
            return true;
        }
        List<AxisAlignedBB> first = getCollisionBoxes(world, pos, world.getBlockState(pos));
        List<AxisAlignedBB> second = getCollisionBoxes(world, otherPos, world.getBlockState(otherPos));
        if (first.isEmpty() || second.isEmpty()) {
            FluidOcclusionCache.putMergedFace(world, pos, direction, false);
            return false;
        }
        EnumFacing opposite = direction.getOpposite();
        for (int u = 0; u < FACE_SAMPLES; u++) {
            for (int v = 0; v < FACE_SAMPLES; v++) {
                double a = (u + 0.5D) / FACE_SAMPLES;
                double b = (v + 0.5D) / FACE_SAMPLES;
                if (!facePointCovered(first, pos, direction, a, b)
                        || !facePointCovered(second, otherPos, opposite, a, b)) {
                    FluidOcclusionCache.putMergedFace(world, pos, direction, false);
                    return false;
                }
            }
        }
        FluidOcclusionCache.putMergedFace(world, pos, direction, true);
        return true;
    }

    public static boolean canPassThroughWall(World world, BlockPos pos, EnumFacing direction) {
        return !isMergedFaceOccluded(world, pos, direction);
    }

    public static boolean fillsBlock(World world, BlockPos pos, IBlockState state) {
        Boolean cached = FluidOcclusionCache.getFullBlock(world, pos);
        if (cached != null) return cached.booleanValue();
        if (state.isFullBlock()) {
            FluidOcclusionCache.putFullBlock(world, pos, true);
            return true;
        }
        List<AxisAlignedBB> boxes = getCollisionBoxes(world, pos, state);
        if (boxes.isEmpty()) {
            FluidOcclusionCache.putFullBlock(world, pos, false);
            return false;
        }
        for (int x = 0; x < VOLUME_SAMPLES; x++) {
            for (int y = 0; y < VOLUME_SAMPLES; y++) {
                for (int z = 0; z < VOLUME_SAMPLES; z++) {
                    double px = pos.getX() + (x + 0.5D) / VOLUME_SAMPLES;
                    double py = pos.getY() + (y + 0.5D) / VOLUME_SAMPLES;
                    double pz = pos.getZ() + (z + 0.5D) / VOLUME_SAMPLES;
                    if (!insideAny(boxes, px, py, pz)) {
                        FluidOcclusionCache.putFullBlock(world, pos, false);
                        return false;
                    }
                }
            }
        }
        FluidOcclusionCache.putFullBlock(world, pos, true);
        return true;
    }

    public static void invalidate(World world, BlockPos pos) {
        if (world != null && pos != null) {
            FluidOcclusionCache.invalidate(world, pos);
        }
    }

    /**
     * Tests whether an entity AABB overlaps the free volume of a waterlogged
     * block. The host block's actual collision boxes are excluded.
     */
    public static boolean intersectsFluidSpace(World world, BlockPos pos,
                                               IBlockState state, AxisAlignedBB bounds,
                                               float fluidHeight) {
        if (world == null || pos == null || state == null || bounds == null
                || fluidHeight <= 0.0F || !world.isBlockLoaded(pos)) {
            return false;
        }

        double minX = Math.max(bounds.minX, pos.getX());
        double maxX = Math.min(bounds.maxX, pos.getX() + 1.0D);
        double minY = Math.max(bounds.minY, pos.getY());
        double maxY = Math.min(bounds.maxY, pos.getY() + (double) fluidHeight);
        double minZ = Math.max(bounds.minZ, pos.getZ());
        double maxZ = Math.min(bounds.maxZ, pos.getZ() + 1.0D);
        if (maxX - minX <= EPSILON || maxY - minY <= EPSILON
                || maxZ - minZ <= EPSILON) {
            return false;
        }

        List<AxisAlignedBB> boxes = getCollisionBoxes(world, pos, state);
        for (int x = 0; x < 4; x++) {
            for (int y = 0; y < 4; y++) {
                for (int z = 0; z < 4; z++) {
                    double px = minX + (maxX - minX) * (x + 0.5D) / 4.0D;
                    double py = minY + (maxY - minY) * (y + 0.5D) / 4.0D;
                    double pz = minZ + (maxZ - minZ) * (z + 0.5D) / 4.0D;
                    if (!insideAny(boxes, px, py, pz)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static boolean containsFluidPoint(World world, BlockPos pos,
                                             IBlockState state, double x,
                                             double y, double z, float fluidHeight) {
        if (world == null || pos == null || state == null || !world.isBlockLoaded(pos)
                || x < pos.getX() || x >= pos.getX() + 1.0D
                || y < pos.getY() || y >= pos.getY() + (double) fluidHeight
                || z < pos.getZ() || z >= pos.getZ() + 1.0D) {
            return false;
        }
        return !insideAny(getCollisionBoxes(world, pos, state), x, y, z);
    }

    private static boolean facePointCovered(List<AxisAlignedBB> boxes, BlockPos pos,
                                            EnumFacing face, double a, double b) {
        double x, y, z;
        switch (face) {
            case DOWN:  x=pos.getX()+a; y=pos.getY();       z=pos.getZ()+b; break;
            case UP:    x=pos.getX()+a; y=pos.getY()+1.0D; z=pos.getZ()+b; break;
            case NORTH: x=pos.getX()+a; y=pos.getY()+b;   z=pos.getZ(); break;
            case SOUTH: x=pos.getX()+a; y=pos.getY()+b;   z=pos.getZ()+1.0D; break;
            case WEST:  x=pos.getX();   y=pos.getY()+a;   z=pos.getZ()+b; break;
            case EAST:  x=pos.getX()+1.0D; y=pos.getY()+a; z=pos.getZ()+b; break;
            default: return false;
        }
        for (AxisAlignedBB box : boxes) {
            if (touchesFace(box, pos, face)
                    && x >= box.minX-EPSILON && x <= box.maxX+EPSILON
                    && y >= box.minY-EPSILON && y <= box.maxY+EPSILON
                    && z >= box.minZ-EPSILON && z <= box.maxZ+EPSILON) return true;
        }
        return false;
    }

    private static boolean touchesFace(AxisAlignedBB box, BlockPos pos, EnumFacing face) {
        switch(face) {
            case DOWN: return box.minY <= pos.getY()+EPSILON;
            case UP: return box.maxY >= pos.getY()+1.0D-EPSILON;
            case NORTH: return box.minZ <= pos.getZ()+EPSILON;
            case SOUTH: return box.maxZ >= pos.getZ()+1.0D-EPSILON;
            case WEST: return box.minX <= pos.getX()+EPSILON;
            case EAST: return box.maxX >= pos.getX()+1.0D-EPSILON;
            default: return false;
        }
    }

    private static boolean insideAny(List<AxisAlignedBB> boxes, double x, double y, double z) {
        for (AxisAlignedBB box : boxes) {
            if (x > box.minX-EPSILON && x < box.maxX+EPSILON
                    && y > box.minY-EPSILON && y < box.maxY+EPSILON
                    && z > box.minZ-EPSILON && z < box.maxZ+EPSILON) return true;
        }
        return false;
    }
}
