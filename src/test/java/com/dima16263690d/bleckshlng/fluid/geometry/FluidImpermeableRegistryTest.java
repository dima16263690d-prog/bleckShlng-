package com.dima16263690d.bleckshlng.fluid.geometry;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;

import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Regression tests against the 1.18.2 minecraft:impermeable block tag. */
public class FluidImpermeableRegistryTest {
    @Test
    public void vanillaGlassVariantsAreImpermeable() {
        assertTrue(FluidImpermeableRegistry.isVanillaTagMember(
                new ResourceLocation("minecraft", "glass")));
        assertTrue(FluidImpermeableRegistry.isVanillaTagMember(
                new ResourceLocation("minecraft", "stained_glass")));
    }

    @Test
    public void barrierAndStructureVoidAreNotMembersOfTheModernTag() {
        assertFalse(FluidImpermeableRegistry.isVanillaTagMember(
                new ResourceLocation("minecraft", "barrier")));
        assertFalse(FluidImpermeableRegistry.isVanillaTagMember(
                new ResourceLocation("minecraft", "structure_void")));
    }

    @Test
    public void registeredBlocksCanBeAddedAndRemoved() {
        Block custom = new Block(Material.ROCK) { };
        BlockPos pos = new BlockPos(0, 64, 0);
        try {
            FluidImpermeableRegistry.register(custom);
            assertTrue(FluidImpermeableRegistry.isImpermeable(
                    custom.getDefaultState(), null, pos));
        } finally {
            FluidImpermeableRegistry.unregister(custom);
        }
        assertFalse(FluidImpermeableRegistry.isImpermeable(
                custom.getDefaultState(), null, pos));
    }
}
