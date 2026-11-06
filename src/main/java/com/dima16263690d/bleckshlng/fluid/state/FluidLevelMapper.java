package com.dima16263690d.bleckshlng.fluid.state;

/**
 * Converts the legacy 1.12.2 liquid metadata format to the source,
 * flowing-level and falling representation used by the Java 1.13.2
 * Update Aquatic fluid backport.
 *
 * Metadata layout used by vanilla BlockLiquid:
 * 0       - source
 * 1..7    - horizontal flow level
 * 8..15   - falling flow (low three bits retain the level)
 */
public final class FluidLevelMapper {
    public static final int SOURCE_METADATA = 0;
    public static final int MAX_AMOUNT = 8;
    public static final int MAX_METADATA = 15;

    private FluidLevelMapper() {
    }

    public static int amountFromMetadata(int metadata) {
        int value = clampMetadata(metadata);
        if (value == SOURCE_METADATA) {
            return MAX_AMOUNT;
        }
        return MAX_AMOUNT - (value & 7);
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

        int safeAmount = clampAmount(amount);
        int level = MAX_AMOUNT - safeAmount;
        return level | (falling ? 8 : 0);
    }

    private static int clampMetadata(int metadata) {
        return Math.max(0, Math.min(MAX_METADATA, metadata));
    }

    private static int clampAmount(int amount) {
        return Math.max(1, Math.min(MAX_AMOUNT, amount));
    }
}
