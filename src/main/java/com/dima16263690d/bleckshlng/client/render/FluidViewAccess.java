package com.dima16263690d.bleckshlng.client.render;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.dima16263690d.bleckshlng.fluid.network.ClientFluidStateCache;
import com.dima16263690d.bleckshlng.fluid.render.FluidOcclusionShapeAdapter;
import com.dima16263690d.bleckshlng.fluid.state.FluidStateBridge;

/**
 * Read-only IBlockAccess facade. Host blocks remain visible through
 * getOriginalBlockState(); neighbor queries see sidecar water as a virtual
 * vanilla water state so the mesher can suppress shared water faces.
 */
@SideOnly(Side.CLIENT)
public final class FluidViewAccess implements IBlockAccess {
    private final IBlockAccess delegate;
    private final int dimension;
    private final World world;

    public FluidViewAccess(World world) {
        this((IBlockAccess) world, world.provider.getDimension(), world);
    }

    public FluidViewAccess(IBlockAccess delegate, int dimension) {
        this(delegate, dimension, delegate instanceof World ? (World) delegate : null);
    }

    public FluidViewAccess(IBlockAccess delegate, int dimension, World world) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate cannot be null");
        }
        this.delegate = delegate;
        this.dimension = dimension;
        this.world = world;
    }

    public IBlockState getOriginalBlockState(BlockPos pos) {
        return delegate.getBlockState(pos);
    }

    public int getDimension() {
        return dimension;
    }

    public World getWorld() {
        return world;
    }

    public FluidStateBridge getFluidState(BlockPos pos) {
        return pos == null ? null
                : ClientFluidStateCache.get(dimension, pos.toLong());
    }

    /**
     * Matches FlowingFluid#getHeight from Minecraft 1.18.2.
     *
     * The fluid height is determined only by the fluid state and a matching
     * fluid above. Slabs and stairs do not rewrite this value: their block
     * geometry occludes the hidden part during the normal chunk depth pass.
     */
    public float getFluidSurfaceHeight(BlockPos pos) {
        if (pos == null) {
            return 0.0F;
        }

        FluidStateBridge fluid = getFluidStateAt(pos);
        if (fluid == null) {
            return 0.0F;
        }

        FluidStateBridge above = getFluidStateAt(pos.up());
        boolean sameFluidAbove = above != null && fluid.isSameKind(above);
        return fluid.getHeight(sameFluidAbove);
    }

    private FluidStateBridge getFluidStateAt(BlockPos pos) {
        if (pos == null) {
            return null;
        }
        FluidStateBridge sidecar = ClientFluidStateCache.get(dimension, pos.toLong());
        if (sidecar != null) {
            return sidecar;
        }
        return com.dima16263690d.bleckshlng.fluid.state.FluidStateMapper
                .fromBlockState(delegate.getBlockState(pos));
    }

    @Override
    public IBlockState getBlockState(BlockPos pos) {
        // Host BlockState is never replaced by a fabricated liquid block.
        // FluidState is queried independently through getFluidStateAt()/the
        // fluid adapter. This keeps slabs, stairs and other hosts intact for
        // both collision and render-occlusion calculations.
        return delegate.getBlockState(pos);
    }

    @Override
    public TileEntity getTileEntity(BlockPos pos) {
        return delegate.getTileEntity(pos);
    }

    @Override
    public int getCombinedLight(BlockPos pos, int lightValue) {
        return delegate.getCombinedLight(pos, lightValue);
    }

    @Override
    public boolean isAirBlock(BlockPos pos) {
        IBlockState state = getBlockState(pos);
        return state.getBlock().isAir(state, this, pos);
    }

    @Override
    public Biome getBiome(BlockPos pos) {
        return delegate.getBiome(pos);
    }

    @Override
    public int getStrongPower(BlockPos pos, EnumFacing direction) {
        return delegate.getStrongPower(pos, direction);
    }

    @Override
    public WorldType getWorldType() {
        return delegate.getWorldType();
    }

    @Override
    public boolean isSideSolid(BlockPos pos, EnumFacing side, boolean defaultValue) {
        return delegate.isSideSolid(pos, side, defaultValue);
    }
}
