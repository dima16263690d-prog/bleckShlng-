package com.dima16263690d.bleckshlng.fluid.geometry;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Reads actual collision boxes for fluid pass-through decisions. */
public final class FluidShapeCalculator {
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
        boolean covered = !boxes.isEmpty() && isFaceCoveredExactly(boxes, pos, face);
        FluidOcclusionCache.putFaceCoverage(world, pos, face, covered);
        return covered;
    }

    public static boolean isMergedFaceOccluded(World world, BlockPos pos, EnumFacing direction) {
        Boolean cached = FluidOcclusionCache.getMergedFace(world, pos, direction);
        if (cached != null) return cached.booleanValue();
        BlockPos otherPos = pos.offset(direction);
        if (!world.isBlockLoaded(pos) || !world.isBlockLoaded(otherPos)) {
            FluidOcclusionCache.putMergedFace(world, pos, direction, true);
            return true;
        }
        IBlockState firstState = world.getBlockState(pos);
        IBlockState secondState = world.getBlockState(otherPos);
        List<AxisAlignedBB> firstBoxes = getCollisionBoxes(world, pos, firstState);
        List<AxisAlignedBB> secondBoxes = getCollisionBoxes(world, otherPos, secondState);
        boolean occluded = isMergedFaceCoveredExactly(
                firstBoxes, pos, direction, secondBoxes, otherPos, direction.getOpposite());
        FluidOcclusionCache.putMergedFace(world, pos, direction, occluded);
        return occluded;
    }

    /**
     * Equivalent to a merged-face occlusion test: the shared face is closed
     * when the union of both blocks' collision projections covers every point.
     * Checking each block independently with AND is incorrect for stairs,
     * panes, fences and other composite shapes.
     */
    private static boolean isMergedFaceCoveredExactly(
            List<AxisAlignedBB> firstBoxes, BlockPos firstPos, EnumFacing firstFace,
            List<AxisAlignedBB> secondBoxes, BlockPos secondPos, EnumFacing secondFace) {
        TreeSet<Double> uEdges = new TreeSet<Double>();
        TreeSet<Double> vEdges = new TreeSet<Double>();
        uEdges.add(Double.valueOf(0.0D));
        uEdges.add(Double.valueOf(1.0D));
        vEdges.add(Double.valueOf(0.0D));
        vEdges.add(Double.valueOf(1.0D));

        collectFaceEdges(firstBoxes, firstPos, firstFace, uEdges, vEdges);
        collectFaceEdges(secondBoxes, secondPos, secondFace, uEdges, vEdges);

        List<Double> u = new ArrayList<Double>(uEdges);
        List<Double> v = new ArrayList<Double>(vEdges);
        for (int i = 0; i + 1 < u.size(); i++) {
            double u0 = u.get(i).doubleValue();
            double u1 = u.get(i + 1).doubleValue();
            if (u1 - u0 <= EPSILON) {
                continue;
            }

            for (int j = 0; j + 1 < v.size(); j++) {
                double v0 = v.get(j).doubleValue();
                double v1 = v.get(j + 1).doubleValue();
                if (v1 - v0 <= EPSILON) {
                    continue;
                }

                double uMid = (u0 + u1) * 0.5D;
                double vMid = (v0 + v1) * 0.5D;
                if (!facePointCovered(firstBoxes, firstPos, firstFace, uMid, vMid)
                        && !facePointCovered(secondBoxes, secondPos, secondFace, uMid, vMid)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static void collectFaceEdges(List<AxisAlignedBB> boxes, BlockPos pos,
                                         EnumFacing face, TreeSet<Double> uEdges,
                                         TreeSet<Double> vEdges) {
        for (AxisAlignedBB box : boxes) {
            if (!touchesFace(box, pos, face)) {
                continue;
            }

            double minU;
            double maxU;
            double minV;
            double maxV;
            switch (face) {
                case DOWN:
                case UP:
                    minU = box.minX - pos.getX();
                    maxU = box.maxX - pos.getX();
                    minV = box.minZ - pos.getZ();
                    maxV = box.maxZ - pos.getZ();
                    break;
                case NORTH:
                case SOUTH:
                    minU = box.minX - pos.getX();
                    maxU = box.maxX - pos.getX();
                    minV = box.minY - pos.getY();
                    maxV = box.maxY - pos.getY();
                    break;
                case WEST:
                case EAST:
                    minU = box.minY - pos.getY();
                    maxU = box.maxY - pos.getY();
                    minV = box.minZ - pos.getZ();
                    maxV = box.maxZ - pos.getZ();
                    break;
                default:
                    return;
            }

            addClippedEdge(uEdges, minU);
            addClippedEdge(uEdges, maxU);
            addClippedEdge(vEdges, minV);
            addClippedEdge(vEdges, maxV);
        }
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

    /**
     * Computes the top of the fluid pocket visible in one vertical column.
     *
     * A waterlogged upper slab has its free volume below the collision shape,
     * so a block-wide 8/9 fluid height would put the surface inside the slab
     * and make the water disappear. Lower slabs keep the normal 8/9 surface
     * above their collision shape. Composite shapes (stairs, trapdoors,
     * fences and panes) are evaluated per collision-boundary cell.
     */
    public static float getFluidColumnHeight(World world, BlockPos pos,
                                             IBlockState state, double worldX,
                                             double worldZ, float nominalHeight) {
        if (world == null || pos == null || state == null
                || nominalHeight <= 0.0F || !world.isBlockLoaded(pos)) {
            return 0.0F;
        }

        double x = worldX;
        double z = worldZ;
        float height = Math.min(1.0F, nominalHeight);
        List<AxisAlignedBB> boxes = getCollisionBoxes(world, pos, state);

        for (AxisAlignedBB box : boxes) {
            if (x <= box.minX + EPSILON || x >= box.maxX - EPSILON
                    || z <= box.minZ + EPSILON || z >= box.maxZ - EPSILON) {
                continue;
            }

            double minY = box.minY - pos.getY();
            double maxY = box.maxY - pos.getY();

            // A full-height post/pane column contains no visible fluid.
            if (minY <= EPSILON && maxY >= (double) height - EPSILON) {
                return 0.0F;
            }

            // A shape that starts above the floor and reaches the block
            // ceiling (top slab, upper stair step, closed top trapdoor)
            // caps the water surface at the underside of that shape.
            if (minY > EPSILON && minY < (double) height
                    && maxY >= 1.0D - EPSILON) {
                height = (float) Math.min((double) height, minY);
            }
        }

        return Math.max(0.0F, height);
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

    /**
     * Exact axis-aligned rectangle coverage for a block face. Coordinate
     * compression tests every rectangle cell boundary, avoiding the missed
     * narrow gaps that a fixed 8x8 sample grid can produce on fences, panes,
     * walls and stair collision shapes.
     */
    private static boolean isFaceCoveredExactly(List<AxisAlignedBB> boxes,
                                                 BlockPos pos, EnumFacing face) {
        TreeSet<Double> uEdges = new TreeSet<Double>();
        TreeSet<Double> vEdges = new TreeSet<Double>();
        uEdges.add(Double.valueOf(0.0D));
        uEdges.add(Double.valueOf(1.0D));
        vEdges.add(Double.valueOf(0.0D));
        vEdges.add(Double.valueOf(1.0D));

        for (AxisAlignedBB box : boxes) {
            if (!touchesFace(box, pos, face)) {
                continue;
            }

            double minU;
            double maxU;
            double minV;
            double maxV;
            switch (face) {
                case DOWN:
                case UP:
                    minU = box.minX - pos.getX();
                    maxU = box.maxX - pos.getX();
                    minV = box.minZ - pos.getZ();
                    maxV = box.maxZ - pos.getZ();
                    break;
                case NORTH:
                case SOUTH:
                    minU = box.minX - pos.getX();
                    maxU = box.maxX - pos.getX();
                    minV = box.minY - pos.getY();
                    maxV = box.maxY - pos.getY();
                    break;
                case WEST:
                case EAST:
                    minU = box.minY - pos.getY();
                    maxU = box.maxY - pos.getY();
                    minV = box.minZ - pos.getZ();
                    maxV = box.maxZ - pos.getZ();
                    break;
                default:
                    return false;
            }

            addClippedEdge(uEdges, minU);
            addClippedEdge(uEdges, maxU);
            addClippedEdge(vEdges, minV);
            addClippedEdge(vEdges, maxV);
        }

        List<Double> u = new ArrayList<Double>(uEdges);
        List<Double> v = new ArrayList<Double>(vEdges);
        for (int i = 0; i + 1 < u.size(); i++) {
            double u0 = u.get(i).doubleValue();
            double u1 = u.get(i + 1).doubleValue();
            if (u1 - u0 <= EPSILON) {
                continue;
            }

            for (int j = 0; j + 1 < v.size(); j++) {
                double v0 = v.get(j).doubleValue();
                double v1 = v.get(j + 1).doubleValue();
                if (v1 - v0 <= EPSILON) {
                    continue;
                }

                if (!facePointCovered(boxes, pos, face,
                        (u0 + u1) * 0.5D, (v0 + v1) * 0.5D)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static void addClippedEdge(TreeSet<Double> edges, double value) {
        if (value > EPSILON && value < 1.0D - EPSILON) {
            edges.add(Double.valueOf(value));
        }
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
