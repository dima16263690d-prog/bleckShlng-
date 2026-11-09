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
     * Returns the visible top of water in this cell. A top slab leaves only
     * its lower half available; a bottom slab leaves the upper half available.
     */
    public float getFluidSurfaceHeight(BlockPos pos) {
        IBlockState original = delegate.getBlockState(pos);
        FluidStateBridge fluid = ClientFluidStateCache.get(dimension, pos.toLong());
        if (fluid != null && fluid.getKind() == FluidStateBridge.FluidKind.WATER) {
            float height = fluid.getOwnHeight();
            if (original.getBlock() instanceof net.minecraft.block.BlockSlab
                    && !((net.minecraft.block.BlockSlab) original.getBlock()).isDouble()) {
                boolean top = original.getValue(net.minecraft.block.BlockSlab.HALF)
                        == net.minecraft.block.BlockSlab.EnumBlockHalf.TOP;
                // A waterlogged bottom slab contains water in y=0.5..1.0;
                // a top slab contains it in y=0.0..0.5.
                return top ? 0.5F : 1.0F;
            }
            return height;
        }

        IBlockState state = delegate.getBlockState(pos);
        if (state.getMaterial() == net.minecraft.block.material.Material.WATER
                && state.getBlock() instanceof net.minecraft.block.BlockLiquid) {
            int level = state.getValue(net.minecraft.block.BlockLiquid.LEVEL).intValue();
            return level >= 8 ? 1.0F : (float) (8 - level) / 9.0F;
        }
        return 0.0F;
    }

    @Override
    public IBlockState getBlockState(BlockPos pos) {
        IBlockState real = delegate.getBlockState(pos);
        if (real.getMaterial().isLiquid()) {
            return real;
        }
        FluidStateBridge fluid = ClientFluidStateCache.get(dimension, pos.toLong());
        if (fluid != null && fluid.getKind() == FluidStateBridge.FluidKind.WATER) {
            if (fluid.isSource()) {
                return Blocks.WATER.getDefaultState();
            }

            // Falling fluid has full column height but is not a source.
            // Preserve metadata bit 3 so BlockFluidRenderer does not flatten
            // a falling waterlogged column into a source block.
            int level = fluid.isFalling()
                    ? 8
                    : Math.max(1, Math.min(7, 8 - fluid.getAmount()));
            return Blocks.WATER.getDefaultState()
                    .withProperty(net.minecraft.block.BlockLiquid.LEVEL, Integer.valueOf(level));
        }
        return real;
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
