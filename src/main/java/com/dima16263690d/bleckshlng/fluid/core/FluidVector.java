package com.dima16263690d.bleckshlng.fluid.core;

/** Small immutable vector used by the fluid domain model. */
public final class FluidVector {
    public static final FluidVector ZERO = new FluidVector(0.0D, 0.0D, 0.0D);

    private final double x;
    private final double y;
    private final double z;

    public FluidVector(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }

    public FluidVector add(double dx, double dy, double dz) {
        return new FluidVector(x + dx, y + dy, z + dz);
    }

    public FluidVector normalize() {
        double length = Math.sqrt(x * x + y * y + z * z);
        return length < 1.0E-12D
                ? ZERO : new FluidVector(x / length, y / length, z / length);
    }
}
