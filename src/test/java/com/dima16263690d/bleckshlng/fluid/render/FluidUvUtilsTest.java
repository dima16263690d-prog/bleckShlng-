package com.dima16263690d.bleckshlng.fluid.render;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Regression tests for still/flow top UV inset parity. */
public class FluidUvUtilsTest {
    @Test
    public void insetMovesTopCoordinatesTowardOneSharedCenter() {
        float inset = FluidUvUtils.getInset(
                16, 16, 0.0F, 1.0F / 16.0F, 0.0F, 1.0F / 16.0F);
        assertEquals(4.0F / 256.0F, inset, 0.000001F);
        assertEquals(0.125F, FluidUvUtils.inset(0.0F, 0.5F, 0.75F), 0.000001F);
        assertEquals(0.875F, FluidUvUtils.inset(1.0F, 0.5F, 0.75F), 0.000001F);
    }

    @Test
    public void stillAndFlowingFacesUseTheSameInsetPolicy() {
        float inset = FluidUvUtils.getInset(
                16, 16, 0.0F, 1.0F / 16.0F, 0.0F, 1.0F / 16.0F);
        float still = FluidUvUtils.inset(0.0F, 0.5F, inset);
        float flowing = FluidUvUtils.inset(0.0F, 0.5F, inset);
        assertTrue(still > 0.0F);
        assertEquals(still, flowing, 0.000001F);
    }
}
