package com.dima16263690d.bleckshlng.client.render;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class FluidSurfaceHeightCalculatorTest {
    @Test
    public void isolatedSourceUsesOriginalHighWeightAgainstEmptyNeighbors() {
        float sourceHeight = 8.0F / 9.0F;

        assertEquals(sourceHeight * 10.0F / 12.0F,
                FluidSurfaceHeightCalculator.calculateAverageHeight(
                        sourceHeight, 0.0F, 0.0F, -1.0F),
                1.0E-6F);
    }

    @Test
    public void fullHeightSideNeighborRaisesCornerToOne() {
        assertEquals(1.0F,
                FluidSurfaceHeightCalculator.calculateAverageHeight(
                        0.5F, 1.0F, 0.0F, 0.0F),
                1.0E-6F);
    }

    @Test
    public void diagonalIsIgnoredWhenBothSideNeighborsAreEmpty() {
        assertEquals(0.5F / 3.0F,
                FluidSurfaceHeightCalculator.calculateAverageHeight(
                        0.5F, 0.0F, 0.0F, 1.0F),
                1.0E-6F);
    }

    @Test
    public void diagonalAtFullHeightRaisesCornerWhenOneSideContainsFluid() {
        assertEquals(1.0F,
                FluidSurfaceHeightCalculator.calculateAverageHeight(
                        0.5F, 0.25F, 0.0F, 1.0F),
                1.0E-6F);
    }

    @Test
    public void heightsAtLeastPointEightReceiveTenfoldWeight() {
        float expected = (0.9F * 10.0F + 0.85F * 10.0F + 0.4F + 0.3F)
                / 22.0F;

        assertEquals(expected,
                FluidSurfaceHeightCalculator.calculateAverageHeight(
                        0.9F, 0.85F, 0.4F, 0.3F),
                1.0E-6F);
    }

    @Test
    public void negativeSolidNeighborHeightsAreIgnored() {
        assertEquals(0.8F,
                FluidSurfaceHeightCalculator.calculateAverageHeight(
                        0.8F, -1.0F, -1.0F, -1.0F),
                1.0E-6F);
    }
}
