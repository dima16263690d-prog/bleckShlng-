package com.dima16263690d.bleckshlng.fluid.render;

/** A two-dimensional rectangle on one block face, in local 0..1 coordinates. */
public final class FluidOcclusionRect {
    private final double minU;
    private final double maxU;
    private final double minV;
    private final double maxV;

    public FluidOcclusionRect(double minU, double maxU, double minV, double maxV) {
        this.minU = clamp(minU);
        this.maxU = clamp(maxU);
        this.minV = clamp(minV);
        this.maxV = clamp(maxV);
        if (this.maxU < this.minU || this.maxV < this.minV) {
            throw new IllegalArgumentException("Occlusion rectangle bounds are inverted");
        }
    }

    public double getMinU() { return minU; }
    public double getMaxU() { return maxU; }
    public double getMinV() { return minV; }
    public double getMaxV() { return maxV; }

    private static double clamp(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }
}
