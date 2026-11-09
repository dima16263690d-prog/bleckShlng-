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
            if (u1 - u0 <= EPSILON) continue;
            for (int j = 0; j + 1 < v.size(); j++) {
                double v0 = v.get(j).doubleValue();
                double v1 = v.get(j + 1).doubleValue();
                if (v1 - v0 <= EPSILON) continue;
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
            if (!touchesFace(box, pos, face)) continue;
            double minU, maxU, minV, maxV;
            switch (face) {
                case DOWN:
                case UP:
                    minU = box.minX - pos.getX(); maxU = box.maxX - pos.getX();
                    minV = box.minZ - pos.getZ(); maxV = box.maxZ - pos.getZ();
                    break;
                case NORTH:
                case SOUTH:
                    minU = box.minX - pos.getX(); maxU = box.maxX - pos.getX();
                    minV = box.minY - pos.getY(); maxV = box.maxY - pos.getY();
                    break;
                case WEST:
                case EAST:
                    minU = box.minY - pos.getY(); maxU = box.maxY - pos.getY();
                    minV = box.minZ - pos.getZ(); maxV = box.maxZ - pos.getZ();
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

    /**
     * Tests whether collision geometry completely covers a horizontal face
     * rectangle between minHeight and maxHeight. This is used by the fluid
     * renderer to avoid drawing water through slab/stair side faces.
     */
    public static boolean isFaceRectangleCovered(World world, BlockPos pos,
                                                  IBlockState state, EnumFacing face,
                                                  float minHeight, float maxHeight) {
        if (world == null || pos == null || state == null
                || face == null || face.getAxis() == EnumFacing.Axis.Y
                || !world.isBlockLoaded(pos)) {
            return false;
        }
        double low = Math.max(0.0D, Math.min(1.0D, minHeight));
        double high = Math.max(low, Math.min(1.0D, maxHeight));
        if (high - low <= EPSILON) {
            return true;
        }

        List<AxisAlignedBB> boxes = getCollisionBoxes(world, pos, state);
        TreeSet<Double> uEdges = new TreeSet<Double>();
        TreeSet<Double> vEdges = new TreeSet<Double>();
        if (face.getAxis() == EnumFacing.Axis.Z) {
            uEdges.add(Double.valueOf(0.0D));
            uEdges.add(Double.valueOf(1.0D));
            vEdges.add(Double.valueOf(low));
            vEdges.add(Double.valueOf(high));
        } else {
            uEdges.add(Double.valueOf(low));
            uEdges.add(Double.valueOf(high));
            vEdges.add(Double.valueOf(0.0D));
            vEdges.add(Double.valueOf(1.0D));
        }

        for (AxisAlignedBB box : boxes) {
            if (!touchesFace(box, pos, face)) {
                continue;
            }
            double minU;
            double maxU;
            double minV;
            double maxV;
            if (face.getAxis() == EnumFacing.Axis.Z) {
                minU = box.minX - pos.getX();
                maxU = box.maxX - pos.getX();
                minV = box.minY - pos.getY();
                maxV = box.maxY - pos.getY();
            } else {
                minU = box.minY - pos.getY();
                maxU = box.maxY - pos.getY();
                minV = box.minZ - pos.getZ();
                maxV = box.maxZ - pos.getZ();
            }
            addClippedEdge(uEdges, minU);
            addClippedEdge(uEdges, maxU);
            if (face.getAxis() == EnumFacing.Axis.Z) {
                if (maxV > low + EPSILON && minV < high - EPSILON) {
                    vEdges.add(Double.valueOf(Math.max(low, Math.min(high, minV))));
                    vEdges.add(Double.valueOf(Math.max(low, Math.min(high, maxV))));
                }
            } else if (maxU > low + EPSILON && minU < high - EPSILON) {
                uEdges.add(Double.valueOf(Math.max(low, Math.min(high, minU))));
                uEdges.add(Double.valueOf(Math.max(low, Math.min(high, maxU))));
            }
        }

        List<Double> us = new ArrayList<Double>(uEdges);
        List<Double> vs = new ArrayList<Double>(vEdges);
        for (int i = 0; i + 1 < us.size(); i++) {
            double u0 = us.get(i).doubleValue();
            double u1 = us.get(i + 1).doubleValue();
            if (u1 - u0 <= EPSILON) continue;
            for (int j = 0; j + 1 < vs.size(); j++) {
                double v0 = vs.get(j).doubleValue();
                double v1 = vs.get(j + 1).doubleValue();
                if (v1 - v0 <= EPSILON) continue;
                if (!facePointCovered(boxes, pos, face,
                        (u0 + u1) * 0.5D, (v0 + v1) * 0.5D)) {
                    return false;
                }
            }
        }
        return !boxes.isEmpty();
    }

    /**
     * Whether fluid can cross the shared face between pos and the neighbour.
     * Uses IBlockState.isSideSolid (1.12.2 equivalent of non-SOLID face shape).
     * Half slabs open the empty half and all horizontal sides to water.
     */
    public static boolean canPassThroughWall(World world, BlockPos pos, EnumFacing direction) {
        if (world == null || pos == null || direction == null || !world.isBlockLoaded(pos)) {
            return false;
        }
        BlockPos otherPos = pos.offset(direction);
        if (!world.isBlockLoaded(otherPos)) {
            return false;
        }
        // FlowingFluid.canPassThroughWall uses the merged occlusion
        // shape of both cells, not a pair of coarse side-solid booleans.
        // This matters for complementary slabs, stairs, fences and panes:
        // only a completely covered shared plane blocks the fluid path.
        return !isMergedFaceOccluded(world, pos, direction);
    }

    /**
     * True when fluid may enter/leave through face of this cell.
     */
    public static boolean isFaceOpenToFluid(World world, BlockPos pos, IBlockState state,
                                            EnumFacing face) {
        if (world == null || pos == null || state == null || face == null) {
            return false;
        }
        return !isSideSolidFace(world, pos, state, face);
    }

    /**
     * 1.12.2 stand-in for a non-SOLID face. Prefer isSideSolid; for single
     * slabs force the open half (and all horizontal sides) to be non-solid so
     * waterlogging matches 1.13 behaviour.
     */
    private static boolean isSideSolidFace(World world, BlockPos pos, IBlockState state,
                                           EnumFacing face) {
        if (state.getBlock() instanceof net.minecraft.block.BlockSlab) {
            net.minecraft.block.BlockSlab slab = (net.minecraft.block.BlockSlab) state.getBlock();
            if (!slab.isDouble()) {
                boolean topHalf = state.getValue(net.minecraft.block.BlockSlab.HALF)
                        == net.minecraft.block.BlockSlab.EnumBlockHalf.TOP;
                if (face == EnumFacing.UP) {
                    return topHalf;
                }
                if (face == EnumFacing.DOWN) {
                    return !topHalf;
                }
                return false;
            }
        }
        return state.isSideSolid(world, pos, face);
    }

    public static boolean fillsBlock(World world, BlockPos pos, IBlockState state) {
        Boolean cached = FluidOcclusionCache.getFullBlock(world, pos);
        if (cached != null) return cached.booleanValue();
        if (state.isFullBlock()) {
            FluidOcclusionCache.putFullBlock(world, pos, true);
            return true;
        }
        List<AxisAlignedBB> boxes = getCollisionBoxes(world, pos, state);
        if (!coversUnitVolume(boxes, pos)) {
            FluidOcclusionCache.putFullBlock(world, pos, false);
            return false;
        }
        FluidOcclusionCache.putFullBlock(world, pos, true);
        return true;
    }

    public static void invalidate(World world, BlockPos pos) {
        if (world != null && pos != null) {
            FluidOcclusionCache.invalidate(world, pos);
        }
    }

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
        if (maxX - minX <= EPSILON || maxY - minY <= EPSILON || maxZ - minZ <= EPSILON) {
            return false;
        }
        List<AxisAlignedBB> boxes = getCollisionBoxes(world, pos, state);
        TreeSet<Double> xEdges = new TreeSet<Double>();
        TreeSet<Double> yEdges = new TreeSet<Double>();
        TreeSet<Double> zEdges = new TreeSet<Double>();
        xEdges.add(Double.valueOf(minX)); xEdges.add(Double.valueOf(maxX));
        yEdges.add(Double.valueOf(minY)); yEdges.add(Double.valueOf(maxY));
        zEdges.add(Double.valueOf(minZ)); zEdges.add(Double.valueOf(maxZ));
        for (AxisAlignedBB box : boxes) {
            addClippedEdge(xEdges, box.minX, minX, maxX);
            addClippedEdge(xEdges, box.maxX, minX, maxX);
            addClippedEdge(yEdges, box.minY, minY, maxY);
            addClippedEdge(yEdges, box.maxY, minY, maxY);
            addClippedEdge(zEdges, box.minZ, minZ, maxZ);
            addClippedEdge(zEdges, box.maxZ, minZ, maxZ);
        }
        List<Double> xs = new ArrayList<Double>(xEdges);
        List<Double> ys = new ArrayList<Double>(yEdges);
        List<Double> zs = new ArrayList<Double>(zEdges);
        for (int x = 0; x + 1 < xs.size(); x++) {
            double px = (xs.get(x).doubleValue() + xs.get(x + 1).doubleValue()) * 0.5D;
            for (int y = 0; y + 1 < ys.size(); y++) {
                double py = (ys.get(y).doubleValue() + ys.get(y + 1).doubleValue()) * 0.5D;
                for (int z = 0; z + 1 < zs.size(); z++) {
                    double pz = (zs.get(z).doubleValue() + zs.get(z + 1).doubleValue()) * 0.5D;
                    if (!insideAny(boxes, px, py, pz)) return true;
                }
            }
        }
        return false;
    }

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
                    || z <= box.minZ + EPSILON || z >= box.maxZ - EPSILON) continue;
            double minY = box.minY - pos.getY();
            double maxY = box.maxY - pos.getY();
            if (minY <= EPSILON && maxY >= (double) height - EPSILON) return 0.0F;
            if (minY > EPSILON && minY < (double) height && maxY >= 1.0D - EPSILON) {
                height = (float) Math.min((double) height, minY);
            }
        }
        return Math.max(0.0F, height);
    }

    public static float getMaxFluidColumnHeight(World world, BlockPos pos,
                                                IBlockState state, AxisAlignedBB bounds,
                                                float nominalHeight) {
        if (world == null || pos == null || state == null || bounds == null
                || nominalHeight <= 0.0F || !world.isBlockLoaded(pos)) {
            return 0.0F;
        }
        double minX = Math.max(bounds.minX, pos.getX());
        double maxX = Math.min(bounds.maxX, pos.getX() + 1.0D);
        double minZ = Math.max(bounds.minZ, pos.getZ());
        double maxZ = Math.min(bounds.maxZ, pos.getZ() + 1.0D);
        if (maxX - minX <= EPSILON || maxZ - minZ <= EPSILON) return 0.0F;
        List<AxisAlignedBB> boxes = getCollisionBoxes(world, pos, state);
        TreeSet<Double> xEdges = new TreeSet<Double>();
        TreeSet<Double> zEdges = new TreeSet<Double>();
        xEdges.add(Double.valueOf(minX)); xEdges.add(Double.valueOf(maxX));
        zEdges.add(Double.valueOf(minZ)); zEdges.add(Double.valueOf(maxZ));
        for (AxisAlignedBB box : boxes) {
            addClippedEdge(xEdges, box.minX, minX, maxX);
            addClippedEdge(xEdges, box.maxX, minX, maxX);
            addClippedEdge(zEdges, box.minZ, minZ, maxZ);
            addClippedEdge(zEdges, box.maxZ, minZ, maxZ);
        }
        List<Double> xs = new ArrayList<Double>(xEdges);
        List<Double> zs = new ArrayList<Double>(zEdges);
        float highest = 0.0F;
        for (int x = 0; x + 1 < xs.size(); x++) {
            double x0 = xs.get(x).doubleValue();
            double x1 = xs.get(x + 1).doubleValue();
            if (x1 - x0 <= EPSILON) continue;
            for (int z = 0; z + 1 < zs.size(); z++) {
                double z0 = zs.get(z).doubleValue();
                double z1 = zs.get(z + 1).doubleValue();
                if (z1 - z0 <= EPSILON) continue;
                double centerX = (x0 + x1) * 0.5D;
                double centerZ = (z0 + z1) * 0.5D;
                float columnHeight = getFluidColumnHeight(world, pos, state, centerX, centerZ, nominalHeight);
                if (columnHeight <= highest) continue;
                AxisAlignedBB columnBounds = new AxisAlignedBB(x0, bounds.minY, z0, x1, bounds.maxY, z1);
                if (intersectsFluidSpace(world, pos, state, columnBounds, columnHeight)) {
                    highest = columnHeight;
                }
            }
        }
        return highest;
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

    private static boolean isFaceCoveredExactly(List<AxisAlignedBB> boxes,
                                                 BlockPos pos, EnumFacing face) {
        TreeSet<Double> uEdges = new TreeSet<Double>();
        TreeSet<Double> vEdges = new TreeSet<Double>();
        uEdges.add(Double.valueOf(0.0D)); uEdges.add(Double.valueOf(1.0D));
        vEdges.add(Double.valueOf(0.0D)); vEdges.add(Double.valueOf(1.0D));
        for (AxisAlignedBB box : boxes) {
            if (!touchesFace(box, pos, face)) continue;
            double minU, maxU, minV, maxV;
            switch (face) {
                case DOWN: case UP:
                    minU = box.minX - pos.getX(); maxU = box.maxX - pos.getX();
                    minV = box.minZ - pos.getZ(); maxV = box.maxZ - pos.getZ();
                    break;
                case NORTH: case SOUTH:
                    minU = box.minX - pos.getX(); maxU = box.maxX - pos.getX();
                    minV = box.minY - pos.getY(); maxV = box.maxY - pos.getY();
                    break;
                case WEST: case EAST:
                    minU = box.minY - pos.getY(); maxU = box.maxY - pos.getY();
                    minV = box.minZ - pos.getZ(); maxV = box.maxZ - pos.getZ();
                    break;
                default:
                    return false;
            }
            addClippedEdge(uEdges, minU); addClippedEdge(uEdges, maxU);
            addClippedEdge(vEdges, minV); addClippedEdge(vEdges, maxV);
        }
        List<Double> u = new ArrayList<Double>(uEdges);
        List<Double> v = new ArrayList<Double>(vEdges);
        for (int i = 0; i + 1 < u.size(); i++) {
            double u0 = u.get(i).doubleValue();
            double u1 = u.get(i + 1).doubleValue();
            if (u1 - u0 <= EPSILON) continue;
            for (int j = 0; j + 1 < v.size(); j++) {
                double v0 = v.get(j).doubleValue();
                double v1 = v.get(j + 1).doubleValue();
                if (v1 - v0 <= EPSILON) continue;
                if (!facePointCovered(boxes, pos, face, (u0 + u1) * 0.5D, (v0 + v1) * 0.5D)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean coversUnitVolume(List<AxisAlignedBB> boxes, BlockPos pos) {
        TreeSet<Double> xEdges = new TreeSet<Double>();
        TreeSet<Double> yEdges = new TreeSet<Double>();
        TreeSet<Double> zEdges = new TreeSet<Double>();
        xEdges.add(Double.valueOf(0.0D)); xEdges.add(Double.valueOf(1.0D));
        yEdges.add(Double.valueOf(0.0D)); yEdges.add(Double.valueOf(1.0D));
        zEdges.add(Double.valueOf(0.0D)); zEdges.add(Double.valueOf(1.0D));
        for (AxisAlignedBB box : boxes) {
            addClippedEdge(xEdges, box.minX - pos.getX());
            addClippedEdge(xEdges, box.maxX - pos.getX());
            addClippedEdge(yEdges, box.minY - pos.getY());
            addClippedEdge(yEdges, box.maxY - pos.getY());
            addClippedEdge(zEdges, box.minZ - pos.getZ());
            addClippedEdge(zEdges, box.maxZ - pos.getZ());
        }
        List<Double> xs = new ArrayList<Double>(xEdges);
        List<Double> ys = new ArrayList<Double>(yEdges);
        List<Double> zs = new ArrayList<Double>(zEdges);
        for (int x = 0; x + 1 < xs.size(); x++) {
            double x0 = xs.get(x).doubleValue();
            double x1 = xs.get(x + 1).doubleValue();
            if (x1 - x0 <= EPSILON) continue;
            for (int y = 0; y + 1 < ys.size(); y++) {
                double y0 = ys.get(y).doubleValue();
                double y1 = ys.get(y + 1).doubleValue();
                if (y1 - y0 <= EPSILON) continue;
                for (int z = 0; z + 1 < zs.size(); z++) {
                    double z0 = zs.get(z).doubleValue();
                    double z1 = zs.get(z + 1).doubleValue();
                    if (z1 - z0 <= EPSILON) continue;
                    if (!insideAny(boxes, pos.getX() + (x0 + x1) * 0.5D,
                            pos.getY() + (y0 + y1) * 0.5D,
                            pos.getZ() + (z0 + z1) * 0.5D)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static void addClippedEdge(TreeSet<Double> edges, double value,
                                       double min, double max) {
        if (value > min + EPSILON && value < max - EPSILON) {
            edges.add(Double.valueOf(value));
        }
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
            case UP:    x=pos.getX()+a; y=pos.getY()+1.0D;  z=pos.getZ()+b; break;
            case NORTH: x=pos.getX()+a; y=pos.getY()+b;     z=pos.getZ(); break;
            case SOUTH: x=pos.getX()+a; y=pos.getY()+b;     z=pos.getZ()+1.0D; break;
            case WEST:  x=pos.getX();   y=pos.getY()+a;     z=pos.getZ()+b; break;
            case EAST:  x=pos.getX()+1.0D; y=pos.getY()+a;  z=pos.getZ()+b; break;
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
