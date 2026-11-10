package com.dima16263690d.bleckshlng.fluid.api;

/**
 * Which faces of a 1x1 fluid cell should be rendered.
 * Faces shared with the same fluid are false (culled) so volumes merge.
 */
public final class FluidFaceMask {
    public static final FluidFaceMask NONE =
            new FluidFaceMask(false, false, false, false, false, false);

    public final boolean up;
    public final boolean down;
    public final boolean north;
    public final boolean south;
    public final boolean west;
    public final boolean east;

    public FluidFaceMask(boolean up, boolean down,
                         boolean north, boolean south,
                         boolean west, boolean east) {
        this.up = up;
        this.down = down;
        this.north = north;
        this.south = south;
        this.west = west;
        this.east = east;
    }

    public boolean any() {
        return up || down || north || south || west || east;
    }
}
