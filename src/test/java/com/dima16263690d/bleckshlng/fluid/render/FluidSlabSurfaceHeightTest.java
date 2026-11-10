package com.dima16263690d.bleckshlng.fluid.render;

import net.minecraft.util.math.AxisAlignedBB;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** Regression tests for the visible fluid cavity inside single slabs. */
public class FluidSlabSurfaceHeightTest {
    @Test
    public void upperSlabCapsWaterSurfaceAtLowerHalf() {
        AxisAlignedBB upperSlab =
                new AxisAlignedBB(0.0D, 0.5D, 0.0D, 1.0D, 1.0D, 1.0D);

        assertEquals(0.5F,
                FluidOcclusionShapeAdapter.clipFluidHeightToShape(
                        8.0F / 9.0F, upperSlab, 0.5D, 0.5D),
                0.000001F);
    }

    @Test
    public void lowerSlabKeepsWaterSurfaceInUpperCavity() {
        AxisAlignedBB lowerSlab =
                new AxisAlignedBB(0.0D, 0.0D, 0.0D, 1.0D, 0.5D, 1.0D);

        assertEquals(8.0F / 9.0F,
                FluidOcclusionShapeAdapter.clipFluidHeightToShape(
                        8.0F / 9.0F, lowerSlab, 0.5D, 0.5D),
                0.000001F);
    }

    @Test
    public void surfaceBelowBottomSlabTopIsHiddenInsideSolidColumn() {
        AxisAlignedBB lowerSlab =
                new AxisAlignedBB(0.0D, 0.0D, 0.0D, 1.0D, 0.5D, 1.0D);

        assertEquals(0.0F,
                FluidOcclusionShapeAdapter.clipFluidHeightToShape(
                        0.4F, lowerSlab, 0.5D, 0.5D),
                0.000001F);
    }
}
