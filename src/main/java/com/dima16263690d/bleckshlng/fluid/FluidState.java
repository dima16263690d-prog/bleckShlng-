package com.dima16263690d.bleckshlng.fluid;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

/**
 * Immutable description of a fluid at a single position.
 *
 * <p>This is the 1.12.2 counterpart of {@code net.minecraft.world.level.material.FluidState}.
 * The 1.21 amount model (1..8, where 8 is a full source) is mapped onto the legacy 0..15
 * metadata {@code level} property: {@code 0} is a source, {@code 1..7} is a flowing amount of
 * {@code 8 - level} and {@code 8..15} is falling.</p>
 */
public final class FluidState {
    public static final int AMOUNT_MAX = 9;
    public static final int AMOUNT_FULL = 8;

    private final Fluid type;
    private final int amount;
    private final boolean falling;

    FluidState(Fluid type, int amount, boolean falling) {
        this.type = type;
        this.amount = amount;
        this.falling = falling;
    }

    public static FluidState of(Fluid type, int amount, boolean falling) {
        return new FluidState(type, amount, falling);
    }

    public Fluid getType() {
        return this.type;
    }

    public int getAmount() {
        return this.amount;
    }

    public boolean isFalling() {
        return this.falling;
    }

    public boolean isEmpty() {
        return this.type.isEmpty();
    }

    public boolean isSource() {
        return this.type.isSource(this);
    }

    public float getOwnHeight() {
        return this.type.getOwnHeight(this);
    }

    public float getHeight(IBlockAccess world, BlockPos pos) {
        return this.type.getHeight(this, world, pos);
    }

    public float getExplosionResistance() {
        return this.type.getExplosionResistance();
    }

    public boolean canBeReplacedWith(IBlockAccess world, BlockPos pos, Fluid other, net.minecraft.util.EnumFacing dir) {
        return this.type.canBeReplacedWith(this, world, pos, other, dir);
    }

    public IBlockState createLegacyBlock() {
        return this.type.createLegacyBlock(this);
    }

    /**
     * Legacy metadata value (0..15) for this state, matching 1.21 {@code FlowingFluid.getLegacyLevel}.
     */
    public int getLegacyLevel() {
        if (this.isSource()) {
            return 0;
        }
        return 8 - Math.min(this.amount, 8) + (this.falling ? 8 : 0);
    }

    /**
     * Builds a fluid state from a legacy metadata value. The owning fluid is used to resolve
     * the source/flowing pair.
     */
    public static FluidState fromLegacy(Fluid owner, int level) {
        if (level <= 0) {
            return owner.getSource().defaultState();
        }
        if (level >= 8) {
            return owner.getFlowing().flowingState(8, true);
        }
        return owner.getFlowing().flowingState(8 - level, false);
    }

    FluidState withAmount(int newAmount) {
        return new FluidState(this.type, newAmount, this.falling);
    }

    FluidState withFalling(boolean newFalling) {
        return new FluidState(this.type, this.amount, newFalling);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof FluidState)) {
            return false;
        }
        FluidState state = (FluidState) other;
        return state.type == this.type && state.amount == this.amount && state.falling == this.falling;
    }

    @Override
    public int hashCode() {
        int result = System.identityHashCode(this.type);
        result = 31 * result + this.amount;
        result = 31 * result + (this.falling ? 1 : 0);
        return result;
    }
}
