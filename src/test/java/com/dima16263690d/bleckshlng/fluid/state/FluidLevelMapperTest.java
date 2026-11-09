package com.dima16263690d.bleckshlng.fluid.state;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Regression tests for the legacy BlockLiquid metadata bridge.
 *
 * These tests run without launching Minecraft and cover all 16 metadata
 * encodings used by 1.12.2 water and lava blocks.
 */
public class FluidLevelMapperTest {
    @Test
    public void nonFallingLegacyMetadataValuesRoundTrip() {
        for (int metadata = 0; metadata <= 7; metadata++) {
            FluidStateBridge water = FluidStateBridge.fromLegacyMetadata(
                    FluidStateBridge.FluidKind.WATER, metadata);
            FluidStateBridge lava = FluidStateBridge.fromLegacyMetadata(
                    FluidStateBridge.FluidKind.LAVA, metadata);

            assertEquals("water metadata " + metadata,
                    metadata, water.toLegacyMetadata());
            assertEquals("lava metadata " + metadata,
                    metadata, lava.toLegacyMetadata());
        }
    }

    @Test
    public void sourceMetadataIsFullAndNotFalling() {
        for (FluidStateBridge.FluidKind kind : FluidStateBridge.FluidKind.values()) {
            FluidStateBridge source = FluidStateBridge.fromLegacyMetadata(kind, 0);
            assertTrue(source.isSource());
            assertFalse(source.isFalling());
            assertEquals(FluidLevelMapper.MAX_AMOUNT, source.getAmount());
        }
    }

    @Test
    public void horizontalLevelsDecreaseFromEightToOne() {
        for (int metadata = 1; metadata <= 7; metadata++) {
            FluidStateBridge state = FluidStateBridge.fromLegacyMetadata(
                    FluidStateBridge.FluidKind.WATER, metadata);
            assertFalse(state.isSource());
            assertFalse(state.isFalling());
            assertEquals(8 - metadata, state.getAmount());
        }
    }

    @Test
    public void allLegacyFallingLevelsNormalizeToFullStrength() {
        for (int metadata = 8; metadata <= 15; metadata++) {
            FluidStateBridge state = FluidStateBridge.fromLegacyMetadata(
                    FluidStateBridge.FluidKind.WATER, metadata);
            assertFalse(state.isSource());
            assertTrue(state.isFalling());
            assertEquals(FluidLevelMapper.MAX_AMOUNT, state.getAmount());
            assertEquals(8, state.toLegacyMetadata());
        }
    }

    @Test
    public void downwardFlowUsesFullStrengthFallingMetadata() {
        assertEquals(8, FluidLevelMapper.toMetadata(8, true, false));
        assertEquals(8, FluidStateBridge.flowing(
                FluidStateBridge.FluidKind.WATER, 8, true).toLegacyMetadata());
    }

    @Test
    public void fallingFluidHeightMatchesModernFluidState() {
        FluidStateBridge falling = FluidStateBridge.fromLegacyMetadata(
                FluidStateBridge.FluidKind.WATER, 8);

        assertEquals(8.0F / 9.0F, falling.getHeight(false), 0.000001F);
        assertEquals(1.0F, falling.getHeight(true), 0.000001F);
    }

    @Test
    public void sourceFluidHeightOnlyReachesCellTopWhenSameFluidIsAbove() {
        FluidStateBridge source = FluidStateBridge.source(
                FluidStateBridge.FluidKind.LAVA);

        assertEquals(8.0F / 9.0F, source.getHeight(false), 0.000001F);
        assertEquals(1.0F, source.getHeight(true), 0.000001F);
    }

    @Test
    public void metadataAndAmountsAreClampedToSupportedRanges() {
        assertEquals(8, FluidLevelMapper.amountFromMetadata(-20));
        assertEquals(8, FluidLevelMapper.amountFromMetadata(50));
        assertEquals(7, FluidLevelMapper.toMetadata(0, false, false));
        assertEquals(8, FluidLevelMapper.toMetadata(50, true, false));
    }
}
