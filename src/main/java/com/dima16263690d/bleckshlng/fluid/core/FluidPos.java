package com.dima16263690d.bleckshlng.fluid.core;

/** Immutable block coordinate independent of the Minecraft version. */
public final class FluidPos {
    private final int x;
    private final int y;
    private final int z;

    public FluidPos(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return z; }

    public FluidPos offset(FluidDirection direction) {
        return new FluidPos(x + direction.getX(),
                y + direction.getY(), z + direction.getZ());
    }

    public FluidPos below() { return offset(FluidDirection.DOWN); }
    public FluidPos above() { return offset(FluidDirection.UP); }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof FluidPos)) return false;
        FluidPos other = (FluidPos) object;
        return x == other.x && y == other.y && z == other.z;
    }

    @Override
    public int hashCode() {
        int result = x;
        result = 31 * result + y;
        result = 31 * result + z;
        return result;
    }

    @Override
    public String toString() {
        return "FluidPos{" + x + ',' + y + ',' + z + '}';
    }
}
