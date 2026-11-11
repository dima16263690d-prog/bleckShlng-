package com.dima16263690d.bleckshlng.fluid.geometry;

import java.util.Collections;
import java.util.List;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Regression tests for 1.12.2 half-block collision-face merging. */
public class FluidShapeGeometryTest {
    private static final BlockPos ORIGIN = new BlockPos(0, 0, 0);
    private static final BlockPos NORTH = new BlockPos(0, 0, -1);

    @Test
    public void bottomSlabDoesNotSealItsWholeHorizontalSide() {
        List<AxisAlignedBB> bottomSlab = Collections.singletonList(
                new AxisAlignedBB(0.0D, 0.0D, 0.0D, 1.0D, 0.5D, 1.0D));

        assertFalse(FluidShapeCalculator.isMergedFaceCoveredExactly(
                bottomSlab, ORIGIN, EnumFacing.NORTH,
                Collections.<AxisAlignedBB>emptyList(), NORTH, EnumFacing.SOUTH));
    }

    @Test
    public void topSlabDoesNotSealItsWholeHorizontalSide() {
        List<AxisAlignedBB> topSlab = Collections.singletonList(
                new AxisAlignedBB(0.0D, 0.5D, 0.0D, 1.0D, 1.0D, 1.0D));

        assertFalse(FluidShapeCalculator.isMergedFaceCoveredExactly(
                topSlab, ORIGIN, EnumFacing.NORTH,
                Collections.<AxisAlignedBB>emptyList(), NORTH, EnumFacing.SOUTH));
    }

    @Test
    public void complementarySlabFacesSealTheSharedPlane() {
        List<AxisAlignedBB> bottomSlab = Collections.singletonList(
                new AxisAlignedBB(0.0D, 0.0D, 0.0D, 1.0D, 0.5D, 1.0D));
        List<AxisAlignedBB> topSlab = Collections.singletonList(
                new AxisAlignedBB(0.0D, 0.5D, -1.0D, 1.0D, 1.0D, 0.0D));

        assertTrue(FluidShapeCalculator.isMergedFaceCoveredExactly(
                bottomSlab, ORIGIN, EnumFacing.NORTH,
                topSlab, NORTH, EnumFacing.SOUTH));
    }

    @Test
    public void twoBottomSlabsStillLeaveTheUpperHalfOpen() {
        List<AxisAlignedBB> first = Collections.singletonList(
                new AxisAlignedBB(0.0D, 0.0D, 0.0D, 1.0D, 0.5D, 1.0D));
        List<AxisAlignedBB> second = Collections.singletonList(
                new AxisAlignedBB(0.0D, 0.0D, -1.0D, 1.0D, 0.5D, 0.0D));

        assertFalse(FluidShapeCalculator.isMergedFaceCoveredExactly(
                first, ORIGIN, EnumFacing.NORTH,
                second, NORTH, EnumFacing.SOUTH));
    }

    @Test
    public void fullBlockStillSealsItsFaceAgainstAir() {
        List<AxisAlignedBB> fullBlock = Collections.singletonList(
                new AxisAlignedBB(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D));

        assertTrue(FluidShapeCalculator.isMergedFaceCoveredExactly(
                fullBlock, ORIGIN, EnumFacing.NORTH,
                Collections.<AxisAlignedBB>emptyList(), NORTH, EnumFacing.SOUTH));
    }

    @Test
    public void topSlabClipsFluidColumnAtItsLowerSurface() {
        List<AxisAlignedBB> topSlab = Collections.singletonList(
                new AxisAlignedBB(0.0D, 0.5D, 0.0D, 1.0D, 1.0D, 1.0D));

        assertEquals(0.5F,
                FluidShapeCalculator.calculateFluidColumnHeight(
                        topSlab, ORIGIN, 0.5D, 0.5D, 8.0F / 9.0F),
                1.0E-5F);
    }

    @Test
    public void bottomSlabLeavesTheFluidVolumeAboveItOpen() {
        List<AxisAlignedBB> bottomSlab = Collections.singletonList(
                new AxisAlignedBB(0.0D, 0.0D, 0.0D, 1.0D, 0.5D, 1.0D));

        assertEquals(8.0F / 9.0F,
                FluidShapeCalculator.calculateFluidColumnHeight(
                        bottomSlab, ORIGIN, 0.5D, 0.5D, 8.0F / 9.0F),
                1.0E-5F);
    }

    @Test
    public void touchingStairCollisionBoxesMergeIntoAFullSolidColumn() {
        // A vanilla stair column consists of a bottom half slab and an
        // upper step box over only half the X/Z footprint.
        List<AxisAlignedBB> stairColumn = java.util.Arrays.asList(
                new AxisAlignedBB(0.0D, 0.0D, 0.0D, 1.0D, 0.5D, 1.0D),
                new AxisAlignedBB(0.0D, 0.5D, 0.0D, 0.5D, 1.0D, 1.0D));

        assertEquals(0.0F,
                FluidShapeCalculator.calculateFluidColumnHeight(
                        stairColumn, ORIGIN, 0.25D, 0.5D, 8.0F / 9.0F),
                1.0E-5F);
    }

    @Test
    public void openHalfOfStairKeepsTheFluidColumnAboveItsBase() {
        List<AxisAlignedBB> stairColumn = java.util.Arrays.asList(
                new AxisAlignedBB(0.0D, 0.0D, 0.0D, 1.0D, 0.5D, 1.0D),
                new AxisAlignedBB(0.0D, 0.5D, 0.0D, 0.5D, 1.0D, 1.0D));

        assertEquals(8.0F / 9.0F,
                FluidShapeCalculator.calculateFluidColumnHeight(
                        stairColumn, ORIGIN, 0.75D, 0.5D, 8.0F / 9.0F),
                1.0E-5F);
    }

    @Test
    public void collisionAboveFluidSurfaceDoesNotClipIt() {
        List<AxisAlignedBB> upperObstacle = Collections.singletonList(
                new AxisAlignedBB(0.0D, 0.6D, 0.0D, 1.0D, 0.8D, 1.0D));

        assertEquals(0.4F,
                FluidShapeCalculator.calculateFluidColumnHeight(
                        upperObstacle, ORIGIN, 0.5D, 0.5D, 0.4F),
                1.0E-5F);
    }

    @Test
    public void fullCubeBlocksTheWholeFluidColumn() {
        List<AxisAlignedBB> fullCube = Collections.singletonList(
                new AxisAlignedBB(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D));

        assertEquals(0.0F,
                FluidShapeCalculator.calculateFluidColumnHeight(
                        fullCube, ORIGIN, 0.5D, 0.5D, 8.0F / 9.0F),
                1.0E-5F);
    }

}
