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

    /**
     * Applies LiquidBlockRenderer's center interpolation to a four-corner
     * quad. Layout is [u0,v0,u1,v1,u2,v2,u3,v3]; the input array is mutated.
     */
    public static float[] applyCenterInset(float[] uv, float amount) {
        if (uv == null || uv.length != 8) {
            throw new IllegalArgumentException("A fluid top quad needs four UV pairs");
        }
        float centerU = (uv[0] + uv[2] + uv[4] + uv[6]) * 0.25F;
        float centerV = (uv[1] + uv[3] + uv[5] + uv[7]) * 0.25F;
        for (int i = 0; i < 8; i += 2) {
            uv[i] = inset(uv[i], centerU, amount);
            uv[i + 1] = inset(uv[i + 1], centerV, amount);
        }
        return uv;
    }
}
