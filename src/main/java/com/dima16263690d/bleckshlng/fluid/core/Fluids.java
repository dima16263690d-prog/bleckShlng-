package com.dima16263690d.bleckshlng.fluid.core;

/**
 * Built-in fluid types. Each family has independent source and flowing
 * identities, matching the 1.18.2 Fluids registry.
 */
public final class Fluids {
    public static final WaterFluid FLOWING_WATER =
            new WaterFluid("flowing_water", false);
    public static final WaterFluid WATER =
            new WaterFluid("water", true);
    public static final LavaFluid FLOWING_LAVA =
            new LavaFluid("flowing_lava", false);
    public static final LavaFluid LAVA =
            new LavaFluid("lava", true);

    static {
        WATER.bindVariants(WATER, FLOWING_WATER);
        FLOWING_WATER.bindVariants(WATER, FLOWING_WATER);
        LAVA.bindVariants(LAVA, FLOWING_LAVA);
        FLOWING_LAVA.bindVariants(LAVA, FLOWING_LAVA);
    }

    private Fluids() { }

    public static FluidState waterSource() {
        return WATER.sourceState();
    }

    public static FluidState lavaSource() {
        return LAVA.sourceState();
    }
}
