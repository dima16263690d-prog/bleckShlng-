package com.dima16263690d.bleckshlng.fluid.core;

import java.util.Random;

/** Version-independent base type for fluids, following the 1.18.2 contract. */
public abstract class Fluid {
    private final String id;
    private final String family;

    protected Fluid(String id, String family) {
        if (id == null || id.isEmpty() || family == null || family.isEmpty()) {
            throw new IllegalArgumentException("Fluid id and family are required");
        }
        this.id = id;
        this.family = family;
    }

    public final String getId() { return id; }
    public final boolean isSame(Fluid other) {
        return other != null && family.equals(other.family);
    }

    public abstract boolean isSource(FluidState state);
    public abstract int getAmount(FluidState state);
    public abstract int getTickDelay(FluidWorld world);
    public abstract boolean canBeReplacedWith(FluidState state, FluidWorld world,
                                               FluidPos pos, Fluid incoming,
                                               FluidDirection direction);
    public abstract FluidVector getFlow(FluidWorld world, FluidPos pos,
                                        FluidState state);
    public abstract void tick(FluidWorld world, FluidPos pos, FluidState state);

    public float getOwnHeight(FluidState state) {
        return state == null || state.isEmpty()
                ? 0.0F : (float) getAmount(state) / 9.0F;
    }

    public float getHeight(FluidState state, FluidWorld world, FluidPos pos) {
        FluidState above = world.getFluidState(pos.above());
        return above != null && !above.isEmpty() && isSame(above.getType())
                ? 1.0F : getOwnHeight(state);
    }

    public boolean isRandomlyTicking() { return false; }

    public void randomTick(FluidWorld world, FluidPos pos,
                           FluidState state, Random random) { }

    public void animateTick(FluidWorld world, FluidPos pos,
                            FluidState state, Random random) { }

    public FluidParticle getDripParticle() { return null; }

    public float getExplosionResistance() { return 100.0F; }
}
