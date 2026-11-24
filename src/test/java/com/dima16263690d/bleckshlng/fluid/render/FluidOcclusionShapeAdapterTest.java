package com.dima16263690d.bleckshlng.fluid.render;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for the 2D face-shape adapter used instead of 1.18.2 VoxelShape.
 * Partial and composite faces must only hide the fluid area they fully cover.
 */
public class FluidOcclusionShapeAdapterTest {
    @Test
    public void halfFaceDoesNotOccludeTheWholeFluidSide() {
        FluidOcclusionShape lowerHalf = FluidOcclusionShape.of(
                new FluidOcclusionRect(0.0D, 1.0D, 0.0D, 0.5D));

        assertTrue(FluidOcclusionShapeAdapter.isFaceRectangleCovered(
                lowerHalf, 0.0D, 1.0D, 0.0D, 0.5D));
        assertFalse(FluidOcclusionShapeAdapter.isFaceFullyCovered(lowerHalf));
    }

    @Test
    public void adjacentRectanglesCanFormACompleteCompositeFace() {
        FluidOcclusionShape composite = new FluidOcclusionShape(Arrays.asList(
                new FluidOcclusionRect(0.0D, 0.5D, 0.0D, 1.0D),
                new FluidOcclusionRect(0.5D, 1.0D, 0.0D, 1.0D)));

        assertTrue(FluidOcclusionShapeAdapter.isFaceFullyCovered(composite));
    }

    @Test
    public void aNarrowGapPreventsFullFaceOcclusion() {
        FluidOcclusionShape shapeWithGap = new FluidOcclusionShape(Arrays.asList(
                new FluidOcclusionRect(0.0D, 0.49D, 0.0D, 1.0D),
                new FluidOcclusionRect(0.51D, 1.0D, 0.0D, 1.0D)));

        assertFalse(FluidOcclusionShapeAdapter.isFaceFullyCovered(shapeWithGap));
    }

    @Test
    public void emptyShapeNeverOccludes() {
        assertFalse(FluidOcclusionShapeAdapter.isFaceFullyCovered(
                FluidOcclusionShape.EMPTY));
        assertFalse(FluidOcclusionShapeAdapter.isFaceRectangleCovered(
                FluidOcclusionShape.EMPTY, 0.0D, 1.0D, 0.0D, 1.0D));
    }

    @Test
    public void faceCanBeFullyCoveredOnlyBelowItsOwnTop() {
        FluidOcclusionShape halfHeight = FluidOcclusionShape.of(
                new FluidOcclusionRect(0.0D, 1.0D, 0.0D, 0.5D));

        assertTrue(FluidOcclusionShapeAdapter.isFaceRectangleCovered(
                halfHeight, 0.0D, 1.0D, 0.0D, 0.5D));
        assertFalse(FluidOcclusionShapeAdapter.isFaceRectangleCovered(
                halfHeight, 0.0D, 1.0D, 0.0D, 0.75D));
    }
}
