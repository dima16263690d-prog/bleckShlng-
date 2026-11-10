package com.dima16263690d.bleckshlng.fluid.compatibility;

import com.dima16263690d.bleckshlng.fluid.core.Fluids;
import com.dima16263690d.bleckshlng.fluid.state.FluidStateBridge;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Mirrors 1.18.2 SlabBlock#getFluidState and SimpleWaterloggedBlock#placeLiquid.
 */
public class WaterloggedFluidStateAdapterTest {
    @Test
    public void flowingWaterStoredInASlabBecomesASource() {
        FluidStateBridge stored = Minecraft112FluidWorldAdapter.toWaterloggedFluidState(
                Fluids.WATER.flowingState(7, false));

        assertTrue(stored.isSource());
        assertEquals(8, stored.getAmount());
        assertFalse(stored.isFalling());
    }

    @Test
    public void fallingWaterStoredInAWaterloggableBlockBecomesASource() {
        FluidStateBridge stored = Minecraft112FluidWorldAdapter.toWaterloggedFluidState(
                Fluids.WATER.flowingState(8, true));

        assertTrue(stored.isSource());
        assertEquals(8, stored.getAmount());
        assertFalse(stored.isFalling());
    }

    @Test
    public void waterSourceRemainsASourceWhenStored() {
        FluidStateBridge stored = Minecraft112FluidWorldAdapter.toWaterloggedFluidState(
                Fluids.waterSource());

        assertTrue(stored.isSource());
        assertEquals(8, stored.getAmount());
    }

    @Test
    public void lavaCannotBeStoredAsWaterloggedWater() {
        assertNull(Minecraft112FluidWorldAdapter.toWaterloggedFluidState(
                Fluids.lavaSource()));
    }

    @Test
    public void emptyFluidCannotBeStoredInAWaterloggedHost() {
        assertNull(Minecraft112FluidWorldAdapter.toWaterloggedFluidState(
                com.dima16263690d.bleckshlng.fluid.core.FluidState.EMPTY));
    }
}
