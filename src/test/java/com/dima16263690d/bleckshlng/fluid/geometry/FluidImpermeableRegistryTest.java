package com.dima16263690d.bleckshlng.fluid.geometry;

import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;

import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Regression tests against the 1.18.2 minecraft:impermeable block tag. */
public class FluidImpermeableRegistryTest {
    @Test
    public void vanillaGlassVariantsAreImpermeable() {
        BlockPos pos = new BlockPos(0, 64, 0);
        assertTrue(FluidImpermeableRegistry.isImpermeable(
                Blocks.GLASS.getDefaultState(), null, pos));
        assertTrue(FluidImpermeableRegistry.isImpermeable(
                Blocks.STAINED_GLASS.getDefaultState(), null, pos));
    }

    @Test
    public void barrierAndStructureVoidAreNotMembersOfTheModernTag() {
        BlockPos pos = new BlockPos(0, 64, 0);
        assertFalse(FluidImpermeableRegistry.isImpermeable(
                Blocks.BARRIER.getDefaultState(), null, pos));
        assertFalse(FluidImpermeableRegistry.isImpermeable(
                Blocks.STRUCTURE_VOID.getDefaultState(), null, pos));
    }

    @Test
    public void modBlocksCanBeRegisteredAndUnregistered() {
        BlockPos pos = new BlockPos(0, 64, 0);
        try {
            FluidImpermeableRegistry.register(Blocks.COBBLESTONE);
            assertTrue(FluidImpermeableRegistry.isImpermeable(
                    Blocks.COBBLESTONE.getDefaultState(), null, pos));
        } finally {
            FluidImpermeableRegistry.unregister(Blocks.COBBLESTONE);
        }
        assertFalse(FluidImpermeableRegistry.isImpermeable(
                Blocks.COBBLESTONE.getDefaultState(), null, pos));
    }
}
