package com.dima16263690d.bleckshlng.fluid.render;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Regression tests for face-rectangle union and fluid culling. */
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
}
