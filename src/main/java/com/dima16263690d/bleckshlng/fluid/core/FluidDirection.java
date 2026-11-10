package com.dima16263690d.bleckshlng.fluid.core;

/** Direction primitives used by the portable 1.18.2 fluid simulation. */
public enum FluidDirection {
    DOWN(0, -1, 0), UP(0, 1, 0),
    NORTH(0, 0, -1), SOUTH(0, 0, 1),
    WEST(-1, 0, 0), EAST(1, 0, 0);

    public static final FluidDirection[] HORIZONTAL = {
            NORTH, SOUTH, WEST, EAST
    };

    private final int x;
    private final int y;
    private final int z;

    FluidDirection(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return z; }

    public FluidDirection opposite() {
        switch (this) {
            case DOWN: return UP;
            case UP: return DOWN;
            case NORTH: return SOUTH;
            case SOUTH: return NORTH;
            case WEST: return EAST;
            case EAST: return WEST;
            default: throw new IllegalStateException(name());
        }
    }

    public boolean isHorizontal() {
        return y == 0;
    }
}
