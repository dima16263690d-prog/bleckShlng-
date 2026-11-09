package com.dima16263690d.bleckshlng.fluid.core;

import java.util.Random;

/**
 * Platform boundary for the fluid engine. Implementations translate these
 * operations to a game version; the simulation never reads legacy block
 * metadata or calls Minecraft classes directly.
 */
public interface FluidWorld {
    boolean isLoaded(FluidPos pos);
    FluidState getFluidState(FluidPos pos);

    boolean blocksMotion(FluidPos pos);
    boolean isSolidMaterial(FluidPos pos);
    boolean isAir(FluidPos pos);
    boolean isIce(FluidPos pos);
    boolean isFaceSturdy(FluidPos pos, FluidDirection face);

    /** Collision-shape face merge used by flow physics (not render occlusion). */
    boolean canPassThroughWall(FluidPos from, FluidDirection direction);
    boolean canHoldFluid(FluidPos target, Fluid fluid);

    /** Platform-specific replacement gate (for Forge fluid containers, etc.). */
    boolean canReplaceFluid(FluidPos target, Fluid incoming, FluidDirection direction);
    boolean canCreateFluidSource(FluidPos target, Fluid fluid);

    void setFluidState(FluidPos pos, FluidState state);
    void removeFluid(FluidPos pos);

    /** Places a state into a destination and owns legacy block/container integration. */
    void spreadFluidTo(FluidPos target, FluidState state, FluidDirection direction);

    void scheduleFluidTick(FluidPos pos, Fluid fluid, int delay);
    void notifyFluidNeighbors(FluidPos pos, Fluid fluid);

    boolean isUltraWarm();
    boolean isFireTickEnabled();
    boolean hasFlammableNeighbor(FluidPos pos);
    boolean isFlammable(FluidPos pos);
    void setFire(FluidPos pos);

    Random getRandom();
    void playSound(FluidSound sound, double x, double y, double z,
                   float volume, float pitch);
    void spawnParticle(FluidParticle particle, double x, double y, double z,
                       double velocityX, double velocityY, double velocityZ);
    void levelEvent(int eventId, FluidPos pos, int data);
}
