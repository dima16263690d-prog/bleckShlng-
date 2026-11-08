package com.dima16263690d.bleckshlng.fluid;

/**
 * Registry holder for the ported fluids. Mirrors {@code net.minecraft.world.level.material.Fluids}.
 */
public final class Fluids {
    public static final EmptyFluid EMPTY = new EmptyFluid();
    public static final WaterFluid FLOWING_WATER = new WaterFluid.Flowing();
    public static final WaterFluid WATER = new WaterFluid.Source();
    public static final LavaFluid FLOWING_LAVA = new LavaFluid.Flowing();
    public static final LavaFluid LAVA = new LavaFluid.Source();

    static {
        FLOWING_WATER.bind(WATER, FLOWING_WATER);
        WATER.bind(WATER, FLOWING_WATER);
        FLOWING_LAVA.bind(LAVA, FLOWING_LAVA);
        LAVA.bind(LAVA, FLOWING_LAVA);
        EMPTY.bind(EMPTY, EMPTY);
    }

    private Fluids() {
    }
}
