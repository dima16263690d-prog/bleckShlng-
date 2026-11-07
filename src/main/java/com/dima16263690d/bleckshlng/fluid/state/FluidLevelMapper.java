package com.dima16263690d.bleckshlng.fluid.state;

/**
 * Converts the legacy 1.12.2 liquid metadata format to the source,
 * flowing-level and falling representation used by the modern 1.18.2
 * FluidState model, encoded through the legacy 1.12.2 metadata layout.
 *
 * Metadata layout used by vanilla BlockLiquid:
 * 0       - source
 * 1..7    - horizontal flow level
 * 8..15   - legacy falling flow; modern FlowingFluid normalizes it to amount 8
 */
public final class FluidLevelMapper {
    public static final int SOURCE_METADATA = 0;
    public static final int MAX_AMOUNT = 8;
    public static final int MAX_METADATA = 15;

    private FluidLevelMapper() {
    }

    public static int amountFromMetadata(int metadata) {
        int value = clampMetadata(metadata);
        if (value == SOURCE_METADATA || (value & 8) != 0) {
            // In the modern fluid model a falling state is always full-height.
            // The low bits in 1.12 metadata only describe the fluid above it.
            return MAX_AMOUNT;
        }
        return MAX_AMOUNT - value;
    }

    public static boolean isFallingMetadata(int metadata) {
        int value = clampMetadata(metadata);
        return value != SOURCE_METADATA && (value & 8) != 0;
    }

    public static boolean isSourceMetadata(int metadata) {
        return clampMetadata(metadata) == SOURCE_METADATA;
    }

    public static int toMetadata(int amount, boolean falling, boolean source) {
        if (source) {
            return SOURCE_METADATA;
        }

        if (falling) {
            return 8;
        }
        int safeAmount = clampAmount(amount);
        return MAX_AMOUNT - safeAmount;
    }

    private static int clampMetadata(int metadata) {
        return Math.max(0, Math.min(MAX_METADATA, metadata));
    }

    private static int clampAmount(int amount) {
        return Math.max(1, Math.min(MAX_AMOUNT, amount));
    }
}
