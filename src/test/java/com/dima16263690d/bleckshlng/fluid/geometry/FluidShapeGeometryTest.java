package com.dima16263690d.bleckshlng.fluid.geometry;

import java.util.Collections;
import java.util.List;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;

import org.junit.Test;

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
}
