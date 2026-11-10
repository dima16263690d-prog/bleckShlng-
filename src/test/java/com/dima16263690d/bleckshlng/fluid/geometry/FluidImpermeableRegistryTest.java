package com.dima16263690d.bleckshlng.fluid.geometry;

import net.minecraft.util.ResourceLocation;

import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Tests the vanilla tag compatibility without initializing the game registry. */
public class FluidImpermeableRegistryTest {
    @Test
    public void vanillaGlassBlocksAreImpermeable() {
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
    public void modNamespacesAndUnlistedBlocksAreNotBuiltInMembers() {
        assertFalse(FluidImpermeableRegistry.isVanillaTagMember(
                new ResourceLocation("examplemod", "glass")));
        assertFalse(FluidImpermeableRegistry.isVanillaTagMember(
                new ResourceLocation("minecraft", "stone")));
    }
}
