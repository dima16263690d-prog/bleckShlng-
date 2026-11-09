package com.dima16263690d.bleckshlng.fluid.core;

import java.util.Random;

/** Modern water rules: 5-tick flow, 1-level drop-off and source conversion. */
public final class WaterFluid extends FlowingFluid {
    WaterFluid(String id, boolean sourceVariant) {
        super(id, "water", sourceVariant);
    }

    @Override
    public int getTickDelay(FluidWorld world) {
        return 5;
    }

    @Override
    protected boolean canConvertToSource() {
        return true;
    }

    @Override
    public int getSlopeFindDistance(FluidWorld world) {
        return 4;
    }

    @Override
    protected int getDropOff(FluidWorld world) {
        return 1;
    }

    @Override
    public boolean canBeReplacedWith(FluidState state, FluidWorld world,
                                     FluidPos pos, Fluid incoming,
                                     FluidDirection direction) {
        return direction == FluidDirection.DOWN && !isSame(incoming);
    }

    @Override
    public void animateTick(FluidWorld world, FluidPos pos,
                            FluidState state, Random random) {
        if (world == null || pos == null || state == null || random == null) {
            return;
        }
        if (!state.isSource() && !state.isFalling()) {
            if (random.nextInt(64) == 0) {
                world.playSound(FluidSound.WATER_AMBIENT,
                        pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                        random.nextFloat() * 0.25F + 0.75F,
                        random.nextFloat() + 0.5F);
            }
        } else if (random.nextInt(10) == 0) {
            world.spawnParticle(FluidParticle.UNDERWATER,
                    pos.getX() + random.nextDouble(),
                    pos.getY() + random.nextDouble(),
                    pos.getZ() + random.nextDouble(),
                    0.0D, 0.0D, 0.0D);
        }
    }

    @Override
    public FluidSound getPickupSound() {
        return FluidSound.BUCKET_FILL_WATER;
    }

    @Override
    public FluidParticle getDripParticle() {
        return FluidParticle.DRIPPING_WATER;
    }

    @Override
    public float getExplosionResistance() {
        return 100.0F;
    }
}
