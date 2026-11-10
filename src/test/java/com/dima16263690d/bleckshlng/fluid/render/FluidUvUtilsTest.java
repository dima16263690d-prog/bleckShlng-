package com.dima16263690d.bleckshlng.fluid.render;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Regression tests for the exact production still/flow top-quad UV transform. */
public class FluidUvUtilsTest {
    @Test
    public void insetAmountMatchesTheReferenceSpriteScale() {
        float inset = FluidUvUtils.getInset(
                16, 16, 0.0F, 1.0F / 16.0F, 0.0F, 1.0F / 16.0F);
        assertEquals(4.0F / 256.0F, inset, 0.000001F);
    }

    @Test
    public void stillAndFlowingQuadsUseTheSameCenterInsetTransform() {
        float inset = 0.25F;
        float[] still = FluidUvUtils.applyCenterInset(
                new float[] {0.0F, 0.0F, 0.0F, 1.0F,
                        1.0F, 1.0F, 1.0F, 0.0F}, inset);
        float[] flow = FluidUvUtils.applyCenterInset(
                new float[] {0.1F, 0.2F, 0.2F, 0.9F,
                        0.9F, 0.8F, 0.8F, 0.1F}, inset);

        assertEquals(0.125F, still[0], 0.000001F);
        assertEquals(0.2F, flow[0], 0.000001F);
        assertEquals(0.5F, average(still, 0), 0.000001F);
        assertEquals(0.5F, average(still, 1), 0.000001F);
        assertEquals(0.5F, average(flow, 0), 0.000001F);
        assertEquals(0.5F, average(flow, 1), 0.000001F);
    }

    @Test
    public void insetMovesEachCoordinateTowardItsQuadCenter() {
        float[] uv = FluidUvUtils.applyCenterInset(
                new float[] {0.0F, 0.0F, 0.0F, 1.0F,
                        1.0F, 1.0F, 1.0F, 0.0F}, 0.75F);
        assertTrue(uv[0] > 0.0F);
        assertTrue(uv[4] < 1.0F);
    }

    private static float average(float[] uv, int offset) {
        return (uv[offset] + uv[offset + 2]
                + uv[offset + 4] + uv[offset + 6]) * 0.25F;
    }
}
