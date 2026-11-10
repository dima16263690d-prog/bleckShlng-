package com.dima16263690d.bleckshlng.fluid.render;

import java.util.Arrays;
import java.util.Collections;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Regression tests for independent face geometry and LiquidBlockRenderer culling. */
public class FluidOcclusionShapeAdapterTest {
    @Test
    public void adjacentBoxesCanCoverOneCompleteFace() {
        FluidOcclusionShape shape = new FluidOcclusionShape(Arrays.asList(
                new FluidOcclusionRect(0.0D, 0.5D, 0.0D, 1.0D),
                new FluidOcclusionRect(0.5D, 1.0D, 0.0D, 1.0D)));
        assertTrue(FluidOcclusionShapeAdapter.isFaceFullyCovered(shape));
    }

    @Test
    public void halfHeightShapeOnlyOccludesItsCoveredBand() {
        FluidOcclusionShape shape = FluidOcclusionShape.of(
                new FluidOcclusionRect(0.0D, 1.0D, 0.0D, 0.5D));
        assertTrue(FluidOcclusionShapeAdapter.isFaceRectangleCovered(
                shape, 0.0D, 1.0D, 0.0D, 0.5D));
        assertFalse(FluidOcclusionShapeAdapter.isFaceRectangleCovered(
                shape, 0.0D, 1.0D, 0.0D, 1.0D));
    }

    @Test
    public void disconnectedBoxesDoNotHideTheGap() {
        FluidOcclusionShape shape = new FluidOcclusionShape(Arrays.asList(
                new FluidOcclusionRect(0.0D, 0.4D, 0.0D, 1.0D),
                new FluidOcclusionRect(0.6D, 1.0D, 0.0D, 1.0D)));
        assertFalse(FluidOcclusionShapeAdapter.isFaceFullyCovered(shape));
    }

    @Test
    public void upwardFaceIsNotCulledWhenFluidDoesNotReachCellTop() {
        assertFalse(FluidOcclusionShapeAdapter.isFluidFaceOccluded(
                FluidOcclusionShape.FULL, EnumFacing.UP, 0.875F));
    }

    @Test
    public void upwardFaceIsCulledWhenFullShapeMeetsFullFluidSurface() {
        assertTrue(FluidOcclusionShapeAdapter.isFluidFaceOccluded(
                FluidOcclusionShape.FULL, EnumFacing.UP, 1.0F));
    }

    @Test
    public void downwardFaceUsesTheWholeBottomSlice() {
        assertTrue(FluidOcclusionShapeAdapter.isFluidFaceOccluded(
                FluidOcclusionShape.FULL, EnumFacing.DOWN, 0.25F));
        assertFalse(FluidOcclusionShapeAdapter.isFluidFaceOccluded(
                FluidOcclusionShape.of(new FluidOcclusionRect(
                        0.0D, 0.5D, 0.0D, 1.0D)),
                EnumFacing.DOWN, 1.0F));
    }

    @Test
    public void horizontalFaceOnlyCullsTheFluidHeightBand() {
        FluidOcclusionShape lowerHalf = FluidOcclusionShape.of(
                new FluidOcclusionRect(0.0D, 1.0D, 0.0D, 0.5D));
        assertTrue(FluidOcclusionShapeAdapter.isFluidFaceOccluded(
                lowerHalf, EnumFacing.NORTH, 0.5F));
        assertFalse(FluidOcclusionShapeAdapter.isFluidFaceOccluded(
                lowerHalf, EnumFacing.NORTH, 0.75F));
    }

    @Test
    public void separateLocalBoxesBuildIndependentSideRectangles() {
        FluidOcclusionShape west = FluidOcclusionShapeAdapter.fromLocalBoxes(
                Arrays.asList(new AxisAlignedBB(
                        0.0D, 0.25D, 0.25D, 0.25D, 0.75D, 0.75D)),
                EnumFacing.WEST);
        assertEquals(1, west.getRectangles().size());
        FluidOcclusionRect rectangle = west.getRectangles().get(0);
        assertEquals(0.25D, rectangle.getMinU(), 0.000001D);
        assertEquals(0.75D, rectangle.getMaxU(), 0.000001D);
        assertEquals(0.25D, rectangle.getMinV(), 0.000001D);
        assertEquals(0.75D, rectangle.getMaxV(), 0.000001D);
    }

    @Test
    public void nonTouchingCompositePiecesDoNotCreateFaceOcclusion() {
        FluidOcclusionShape shape = FluidOcclusionShapeAdapter.fromLocalBoxes(
                Collections.singletonList(new AxisAlignedBB(
                        0.2D, 0.2D, 0.2D, 0.8D, 0.8D, 0.8D)),
                EnumFacing.NORTH);
        assertTrue(shape.isEmpty());
    }

    @Test
    public void stairLeavesItsUncoveredSideBandOpenToFluid() {
        // The base occupies the lower half. The upper step is set back from
        // the south face, so the top half of that face remains exposed.
        FluidOcclusionShape south = FluidOcclusionShapeAdapter.fromLocalBoxes(
                Arrays.asList(
                        new AxisAlignedBB(0.0D, 0.0D, 0.0D, 1.0D, 0.5D, 1.0D),
                        new AxisAlignedBB(0.0D, 0.5D, 0.0D, 1.0D, 1.0D, 0.5D)),
                EnumFacing.SOUTH);
        assertTrue(FluidOcclusionShapeAdapter.isFaceRectangleCovered(
                south, 0.0D, 1.0D, 0.0D, 0.5D));
        assertFalse(FluidOcclusionShapeAdapter.isFaceFullyCovered(south));
    }

    @Test
    public void fencePostAndRailDoNotSealTheWholeFluidFace() {
        FluidOcclusionShape south = FluidOcclusionShapeAdapter.fromLocalBoxes(
                Arrays.asList(
                        new AxisAlignedBB(0.375D, 0.0D, 0.0D,
                                0.625D, 1.0D, 1.0D),
                        new AxisAlignedBB(0.0D, 0.375D, 0.875D,
                                1.0D, 0.625D, 1.0D)),
                EnumFacing.SOUTH);
        assertFalse(FluidOcclusionShapeAdapter.isFaceFullyCovered(south));
        assertFalse(FluidOcclusionShapeAdapter.isFaceRectangleCovered(
                south, 0.0D, 1.0D, 0.0D, 1.0D));
    }

    @Test
    public void glassPaneOnlyOccludesItsNarrowCenterStrip() {
        FluidOcclusionShape south = FluidOcclusionShapeAdapter.fromLocalBoxes(
                Collections.singletonList(new AxisAlignedBB(
                        0.4375D, 0.0D, 0.0D,
                        0.5625D, 1.0D, 1.0D)),
                EnumFacing.SOUTH);
        assertFalse(FluidOcclusionShapeAdapter.isFaceFullyCovered(south));
        assertFalse(FluidOcclusionShapeAdapter.isFaceRectangleCovered(
                south, 0.0D, 1.0D, 0.0D, 1.0D));
        assertTrue(FluidOcclusionShapeAdapter.isFaceRectangleCovered(
                south, 0.4375D, 0.5625D, 0.0D, 1.0D));
    }

}
