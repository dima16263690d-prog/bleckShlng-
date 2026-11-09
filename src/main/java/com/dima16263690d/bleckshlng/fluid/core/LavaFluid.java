package com.dima16263690d.bleckshlng.fluid.core;

import java.util.Random;

/** Modern lava rules, including dimension-specific flow and random fire ticks. */
public final class LavaFluid extends FlowingFluid {
    private static final float MIN_LEVEL_CUTOFF = 4.0F / 9.0F;

    LavaFluid(String id, boolean sourceVariant) {
        super(id, "lava", sourceVariant);
    }

    @Override
    public int getTickDelay(FluidWorld world) {
        return world != null && world.isUltraWarm() ? 10 : 30;
    }

    @Override
    protected boolean canConvertToSource() {
        return false;
    }

    @Override
    protected int getSlopeFindDistance(FluidWorld world) {
        return world != null && world.isUltraWarm() ? 4 : 2;
    }

    @Override
    protected int getDropOff(FluidWorld world) {
        return world != null && world.isUltraWarm() ? 1 : 2;
    }

    @Override
    public boolean canBeReplacedWith(FluidState state, FluidWorld world,
                                     FluidPos pos, Fluid incoming,
                                     FluidDirection direction) {
        return state != null && world != null && pos != null
                && state.getHeight(world, pos) >= MIN_LEVEL_CUTOFF
                && incoming != null && incoming.isSame(Fluids.WATER);
    }

    @Override
    protected int getSpreadDelay(FluidWorld world, FluidPos pos,
                                 FluidState current, FluidState next) {
        int delay = getTickDelay(world);
        if (current == null || next == null || current.isEmpty() || next.isEmpty()
                || current.isFalling() || next.isFalling()
                || next.getHeight(world, pos) <= current.getHeight(world, pos)
                || world.getRandom().nextInt(4) == 0) {
            return delay;
        }
        return delay * 4;
    }

    @Override
    public boolean isRandomlyTicking() {
        return true;
    }

    @Override
    public void randomTick(FluidWorld world, FluidPos pos,
                           FluidState state, Random random) {
        if (world == null || pos == null || random == null
                || !world.isFireTickEnabled()) {
            return;
        }

        int attempts = random.nextInt(3);
        if (attempts > 0) {
            FluidPos target = pos;
            for (int i = 0; i < attempts; i++) {
                target = new FluidPos(
                        target.getX() + random.nextInt(3) - 1,
                        target.getY() + 1,
                        target.getZ() + random.nextInt(3) - 1);
                if (!world.isLoaded(target)) {
                    return;
                }
                if (world.isAir(target)) {
                    if (world.hasFlammableNeighbor(target)) {
                        world.setFire(target);
                        return;
                    }
                } else if (world.blocksMotion(target)) {
                    return;
                }
            }
        } else {
            for (int i = 0; i < 3; i++) {
                FluidPos target = new FluidPos(
                        pos.getX() + random.nextInt(3) - 1,
                        pos.getY(),
                        pos.getZ() + random.nextInt(3) - 1).above();
                if (!world.isLoaded(target)) {
                    return;
                }
                FluidPos support = target.below();
                if (world.isAir(target) && world.isFlammable(support)) {
                    world.setFire(target);
                }
            }
        }
    }

    @Override
    public void animateTick(FluidWorld world, FluidPos pos,
                            FluidState state, Random random) {
        if (world == null || pos == null || random == null) {
            return;
        }

        FluidPos above = pos.above();
        if (world.isLoaded(above) && world.isAir(above)
                && !world.isSolidRender(above)) {
            if (random.nextInt(100) == 0) {
                double x = pos.getX() + random.nextDouble();
                double y = pos.getY() + 1.0D;
                double z = pos.getZ() + random.nextDouble();
                world.spawnParticle(FluidParticle.LAVA, x, y, z,
                        0.0D, 0.0D, 0.0D);
                world.playSound(FluidSound.LAVA_POP, x, y, z,
                        0.2F + random.nextFloat() * 0.2F,
                        0.9F + random.nextFloat() * 0.15F);
            }
            if (random.nextInt(200) == 0) {
                world.playSound(FluidSound.LAVA_AMBIENT,
                        pos.getX(), pos.getY(), pos.getZ(),
                        0.2F + random.nextFloat() * 0.2F,
                        0.9F + random.nextFloat() * 0.15F);
            }
        }
    }

    @Override
    public FluidSound getPickupSound() {
        return FluidSound.BUCKET_FILL_LAVA;
    }

    @Override
    public FluidParticle getDripParticle() {
        return FluidParticle.DRIPPING_LAVA;
    }

    @Override
    public float getExplosionResistance() {
        return 100.0F;
    }
}
