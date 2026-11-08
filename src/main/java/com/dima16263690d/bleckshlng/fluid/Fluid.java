package com.dima16263690d.bleckshlng.fluid;

import java.util.Random;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.Item;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/**
 * Base class of the ported 1.21 fluid model. A fluid owns a source state and a flowing state;
 * the two concrete subclasses of a fluid ({@code Source} and {@code Flowing}) differ only in
 * how they answer {@link #isSource(FluidState)} and {@link #getAmount(FluidState)}.
 *
 * <p>Port of {@code net.minecraft.world.level.material.Fluid} from Minecraft 1.21.</p>
 */
public abstract class Fluid {
    private final String name;
    private Fluid source;
    private Fluid flowing;
    private FluidState defaultState;
    private final FluidState[] flowingCache = new FluidState[10];
    private final FluidState[] fallingCache = new FluidState[10];

    protected Fluid(String name) {
        this.name = name;
    }

    void bind(Fluid source, Fluid flowing) {
        this.source = source;
        this.flowing = flowing;
        this.defaultState = new FluidState(this, this.defaultAmount(), false);
    }

    public String getName() {
        return this.name;
    }

    public Fluid getSource() {
        return this.source;
    }

    public Fluid getFlowing() {
        return this.flowing;
    }

    public FluidState defaultState() {
        return this.defaultState;
    }

    public FluidState flowingState(int amount, boolean falling) {
        FluidState[] cache = falling ? this.fallingCache : this.flowingCache;
        int index = Math.max(0, Math.min(9, amount));
        FluidState cached = cache[index];
        if (cached == null) {
            cached = new FluidState(this, index, falling);
            cache[index] = cached;
        }
        return cached;
    }

    protected int defaultAmount() {
        return this.isSourceState() ? FluidState.AMOUNT_FULL : 1;
    }

    protected abstract boolean isSourceState();

    public abstract Item getBucket();

    public abstract boolean isSame(Fluid other);

    public boolean isEmpty() {
        return false;
    }

    public abstract float getExplosionResistance();

    public abstract float getHeight(FluidState state, IBlockAccess world, BlockPos pos);

    public abstract float getOwnHeight(FluidState state);

    public abstract int getAmount(FluidState state);

    public abstract boolean isSource(FluidState state);

    public abstract int getTickDelay(World world);

    public abstract IBlockState createLegacyBlock(FluidState state);

    public abstract Vec3d getFlow(IBlockAccess world, BlockPos pos, FluidState state);

    public abstract boolean canBeReplacedWith(FluidState state, IBlockAccess world, BlockPos pos, Fluid other, EnumFacing dir);

    public void animateTick(World world, BlockPos pos, FluidState state, Random rand) {
    }

    public void tick(World world, BlockPos pos, IBlockState blockState, FluidState state) {
    }

    public void randomTick(World world, BlockPos pos, FluidState state, Random rand) {
    }

    public boolean isRandomlyTicking() {
        return false;
    }
}
