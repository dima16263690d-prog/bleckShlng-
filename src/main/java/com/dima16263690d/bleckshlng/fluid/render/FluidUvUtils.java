package com.dima16263690d.bleckshlng.fluid.render;

/** Texture-atlas UV inset used by Minecraft 1.18.2 LiquidBlockRenderer. */
public final class FluidUvUtils {
    private static final float EPSILON = 1.0E-6F;

    private FluidUvUtils() { }

    public static float getInset(int iconWidth, int iconHeight,
                                 float minU, float maxU,
                                 float minV, float maxV) {
        float spriteWidth = (float) iconWidth
                / Math.max(EPSILON, maxU - minU);
        float spriteHeight = (float) iconHeight
                / Math.max(EPSILON, maxV - minV);
        return 4.0F / Math.max(spriteWidth, spriteHeight);
    }

    public static float inset(float coordinate, float center, float amount) {
        return center + (coordinate - center) * (1.0F - amount);
    }
}
