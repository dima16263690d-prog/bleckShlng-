package com.dima16263690d.bleckshlng.fluid.state;

import net.minecraft.block.material.Material;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** Regression tests for the 1.18.2 fluid timing and decay constants. */
public class FluidPropertiesTest {
    @Test
    public void overworldLavaUsesOriginalTickAndSpreadSettings() {
        assertEquals(30, FluidProperties.tickRate(null, Material.LAVA));
        assertEquals(2, FluidProperties.slopeFindDistance(null, Material.LAVA));
        assertEquals(2, FluidProperties.horizontalDecay(null, Material.LAVA));
        assertEquals(0, FluidProperties.nextHorizontalAmount(
                null, Material.LAVA, 2));
        assertEquals(4, FluidProperties.nextHorizontalAmount(
                null, Material.LAVA, 6));
    }

    @Test
    public void waterKeepsOriginalTickAndSpreadSettings() {
        assertEquals(5, FluidProperties.tickRate(null, Material.WATER));
        assertEquals(4, FluidProperties.slopeFindDistance(null, Material.WATER));
        assertEquals(1, FluidProperties.horizontalDecay(null, Material.WATER));
        assertEquals(1, FluidProperties.nextHorizontalAmount(
                null, Material.WATER, 2));
    }
}
