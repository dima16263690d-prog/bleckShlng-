package com.dima16263690d.bleckshlng.fluid.core;

/**
 * Immutable modern fluid state. It is deliberately independent of BlockState:
 * a block may host fluid without becoming a liquid block.
 */
public final class FluidState {
    public static final int MAX_AMOUNT = 8;
    public static final FluidState EMPTY = new FluidState(null, 0, false, false);

    private final Fluid fluid;
    private final int amount;
    private final boolean falling;
    private final boolean source;

    private FluidState(Fluid fluid, int amount, boolean falling, boolean source) {
        if (fluid == null) {
            this.fluid = null;
            this.amount = 0;
            this.falling = false;
            this.source = false;
            return;
        }
        if (amount < 1 || amount > MAX_AMOUNT) {
            throw new IllegalArgumentException("Fluid amount must be between 1 and 8");
        }
        this.fluid = fluid;
        this.amount = source ? MAX_AMOUNT : amount;
        this.falling = !source && falling;
        this.source = source;
    }

    public static FluidState source(Fluid fluid) {
        return new FluidState(fluid, MAX_AMOUNT, false, true);
    }

    public static FluidState flowing(Fluid fluid, int amount, boolean falling) {
        return new FluidState(fluid, amount, falling, false);
    }

    public Fluid getType() { return fluid; }

    public FluidKind getKind() {
        if (isEmpty()) return null;
        return fluid.isSame(Fluids.WATER) ? FluidKind.WATER : FluidKind.LAVA;
    }

    public boolean isSameKind(FluidKind kind) {
        return kind != null && getKind() == kind;
    }

    public boolean isSameKind(FluidState other) {
        return other != null && !isEmpty() && !other.isEmpty()
                && fluid.isSame(other.fluid);
    }
    public int getAmount() { return amount; }
    public boolean isFalling() { return falling; }
    public boolean isSource() { return source; }
    public boolean isEmpty() { return fluid == null; }

    public float getOwnHeight() {
        return isEmpty() ? 0.0F : (float) amount / 9.0F;
    }

    public float getHeight(FluidWorld world, FluidPos pos) {
        if (isEmpty()) return 0.0F;
        FluidState above = world.getFluidState(pos.above());
        return above != null && !above.isEmpty()
                && fluid.isSame(above.getType()) ? 1.0F : getOwnHeight();
    }

    public boolean isSame(FluidState other) {
        return other != null && !isEmpty() && !other.isEmpty()
                && fluid.isSame(other.fluid);
    }

    public FluidState withAmount(int newAmount) {
        return flowing(fluid, newAmount, falling);
    }

    public FluidState withFalling(boolean value) {
        return source ? this : flowing(fluid, amount, value);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof FluidState)) return false;
        FluidState other = (FluidState) object;
        return fluid == other.fluid && amount == other.amount
                && falling == other.falling && source == other.source;
    }

    @Override
    public int hashCode() {
        int result = fluid == null ? 0 : System.identityHashCode(fluid);
        result = 31 * result + amount;
        result = 31 * result + (falling ? 1 : 0);
        result = 31 * result + (source ? 1 : 0);
        return result;
    }

    @Override
    public String toString() {
        return isEmpty() ? "FluidState{empty}" : "FluidState{" + fluid.getId()
                + ", amount=" + amount + ", falling=" + falling
                + ", source=" + source + '}';
    }
}
