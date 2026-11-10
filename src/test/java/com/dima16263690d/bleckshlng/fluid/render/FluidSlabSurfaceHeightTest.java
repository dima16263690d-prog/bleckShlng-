package com.dima16263690d.bleckshlng.fluid.render;

import net.minecraft.util.math.AxisAlignedBB;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class FluidSlabSurfaceHeightTest {
    @Test
    public void upperSlabCavityEndsAtUnderside() {
        AxisAlignedBB upper =
                new AxisAlignedBB(0.0D, 0.5D, 0.0D, 1.0D, 1.0D, 1.0D);
        assertEquals(0.5F,
                FluidOcclusionShapeAdapter.clipFluidHeightToShape(
                        8.0F / 9.0F, upper, 0.5D, 0.5D),
                0.000001F);
    }

    @Test
    public void lowerSlabLeavesUpperWaterSurfaceUnchanged() {
        AxisAlignedBB lower =
                new AxisAlignedBB(0.0D, 0.0D, 0.0D, 1.0D, 0.5D, 1.0D);
        assertEquals(8.0F / 9.0F,
                FluidOcclusionShapeAdapter.clipFluidHeightToShape(
                        8.0F / 9.0F, lower, 0.5D, 0.5D),
                0.000001F);
    }

    @Test
    public void lowWaterBelowUpperSlabRemainsUnchanged() {
        AxisAlignedBB upper =
                new AxisAlignedBB(0.0D, 0.5D, 0.0D, 1.0D, 1.0D, 1.0D);
        assertEquals(0.4F,
                FluidOcclusionShapeAdapter.clipFluidHeightToShape(
                        0.4F, upper, 0.5D, 0.5D),
                0.000001F);
    }
}
