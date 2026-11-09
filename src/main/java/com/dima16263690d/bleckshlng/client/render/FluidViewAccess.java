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

    public FluidViewAccess(World world) {
        this((IBlockAccess) world, world.provider.getDimension());
    }

    public FluidViewAccess(IBlockAccess delegate, int dimension) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate cannot be null");
        }
        this.delegate = delegate;
        this.dimension = dimension;
    }

    public IBlockState getOriginalBlockState(BlockPos pos) {
        return delegate.getBlockState(pos);
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
                return top ? 0.5F : Math.max(height, 0.8888889F);
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
            if (fluid.isSource() || fluid.getAmount() >= 8) {
                return Blocks.WATER.getDefaultState();
            }
            int level = Math.max(1, Math.min(7, 8 - fluid.getAmount()));
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
