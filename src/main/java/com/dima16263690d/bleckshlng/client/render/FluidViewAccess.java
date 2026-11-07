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
 * IBlockAccess that reports waterlogged sidecar cells as vanilla water.
 *
 * BlockFluidRenderer face-culls against Material.WATER. By presenting
 * waterlogged hosts as water, adjacent ocean faces and waterlogged faces
 * share the same culling rules as 1.13 continuous fluid.
 */
@SideOnly(Side.CLIENT)
public final class FluidViewAccess implements IBlockAccess {
    private final World world;
    private final int dimension;

    public FluidViewAccess(World world) {
        this.world = world;
        this.dimension = world.provider.getDimension();
    }

    @Override
    public IBlockState getBlockState(BlockPos pos) {
        IBlockState real = world.getBlockState(pos);
        if (real.getMaterial().isLiquid()) {
            return real;
        }
        FluidStateBridge fluid = ClientFluidStateCache.get(dimension, pos.toLong());
        if (fluid != null && fluid.getKind() == FluidStateBridge.FluidKind.WATER) {
            // Source-looking water so height matches ocean surface (level 0).
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
        return world.getTileEntity(pos);
    }

    @Override
    public int getCombinedLight(BlockPos pos, int lightValue) {
        return world.getCombinedLight(pos, lightValue);
    }

    @Override
    public boolean isAirBlock(BlockPos pos) {
        return getBlockState(pos).getBlock().isAir(getBlockState(pos), this, pos);
    }

    @Override
    public Biome getBiome(BlockPos pos) {
        return world.getBiome(pos);
    }

    @Override
    public int getStrongPower(BlockPos pos, EnumFacing direction) {
        return world.getStrongPower(pos, direction);
    }

    @Override
    public WorldType getWorldType() {
        return world.getWorldType();
    }

    @Override
    public boolean isSideSolid(BlockPos pos, EnumFacing side, boolean _default) {
        return world.isSideSolid(pos, side, _default);
    }
}
