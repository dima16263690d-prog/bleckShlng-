package com.dima16263690d.bleckshlng.fluid.state;

/**
 * Immutable compatibility representation of a vanilla water/lava state.
 *
 * This is deliberately separate from BlockLiquid metadata: the internal
 * representation stores source, amount and falling state following the 1.18.2
 * FluidState model, while the host block metadata remains unchanged.
 */
public final class FluidStateBridge {
    public enum FluidKind {
        WATER,
        LAVA
    }

    private final FluidKind kind;
    private final int amount;
    private final boolean falling;
    private final boolean source;

    private FluidStateBridge(FluidKind kind, int amount, boolean falling, boolean source) {
        if (kind == null) {
            throw new IllegalArgumentException("Fluid kind cannot be null");
        }
        this.kind = kind;
        this.source = source;
        this.amount = source ? FluidLevelMapper.MAX_AMOUNT
                : Math.max(1, Math.min(FluidLevelMapper.MAX_AMOUNT, amount));
        this.falling = !source && falling;
    }

    public static FluidStateBridge fromLegacyMetadata(FluidKind kind, int metadata) {
        boolean source = FluidLevelMapper.isSourceMetadata(metadata);
        return new FluidStateBridge(kind,
                FluidLevelMapper.amountFromMetadata(metadata),
                FluidLevelMapper.isFallingMetadata(metadata),
                source);
    }

    public static FluidStateBridge source(FluidKind kind) {
        return new FluidStateBridge(kind, FluidLevelMapper.MAX_AMOUNT, false, true);
    }

    public static FluidStateBridge flowing(FluidKind kind, int amount, boolean falling) {
        return new FluidStateBridge(kind, amount, falling, false);
    }

    public FluidKind getKind() {
        return kind;
    }

    public int getAmount() {
        return amount;
    }

    public boolean isFalling() {
        return falling;
    }

    public boolean isSource() {
        return source;
    }

    public float getOwnHeight() {
        return (float) amount / 9.0F;
    }

    public int toLegacyMetadata() {
        return FluidLevelMapper.toMetadata(amount, falling, source);
    }

    public boolean isSameKind(FluidStateBridge other) {
        return other != null && kind == other.kind;
    }

    public FluidStateBridge withAmount(int newAmount) {
        return new FluidStateBridge(kind, newAmount, falling, false);
    }

    public FluidStateBridge asFalling() {
        return new FluidStateBridge(kind, amount, true, false);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof FluidStateBridge)) {
            return false;
        }
        FluidStateBridge other = (FluidStateBridge) object;
        return kind == other.kind
                && amount == other.amount
                && falling == other.falling
                && source == other.source;
    }

    @Override
    public int hashCode() {
        int result = kind.hashCode();
        result = 31 * result + amount;
        result = 31 * result + (falling ? 1 : 0);
        result = 31 * result + (source ? 1 : 0);
        return result;
    }

    @Override
    public String toString() {
        return "FluidStateBridge{" + kind
                + ", amount=" + amount
                + ", falling=" + falling
                + ", source=" + source + '}';
    }
}
