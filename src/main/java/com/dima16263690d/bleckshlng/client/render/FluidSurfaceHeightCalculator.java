package com.dima16263690d.bleckshlng.client.render;

/**
 * Exact height-weighting rules from Minecraft 1.18.2
 * LiquidBlockRenderer#calculateAverageHeight and #addWeightedHeight.
 *
 * Collision geometry is intentionally not applied here. The renderer
 * calculates fluid heights from FluidState and relies on block face
 * occlusion/depth testing to hide the parts covered by a waterlogged host.
 */
final class FluidSurfaceHeightCalculator {
    private FluidSurfaceHeightCalculator() {
    }

    static float calculateAverageHeight(float own, float firstSide,
                                        float secondSide, float diagonal) {
        if (firstSide >= 1.0F || secondSide >= 1.0F) {
            return 1.0F;
        }

        float[] weighted = new float[2];
        if (firstSide > 0.0F || secondSide > 0.0F) {
            if (diagonal >= 1.0F) {
                return 1.0F;
            }
            addWeightedHeight(weighted, diagonal);
        }

        addWeightedHeight(weighted, own);
        addWeightedHeight(weighted, firstSide);
        addWeightedHeight(weighted, secondSide);
        return weighted[1] == 0.0F ? own : weighted[0] / weighted[1];
    }

    private static void addWeightedHeight(float[] weighted, float height) {
        if (height >= 0.8F) {
            weighted[0] += height * 10.0F;
            weighted[1] += 10.0F;
        } else if (height >= 0.0F) {
            weighted[0] += height;
            weighted[1] += 1.0F;
        }
    }
}
