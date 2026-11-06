package com.dima16263690d.bleckshlng.block;

import java.util.Random;

import net.minecraft.util.BlockRenderLayer;
import net.minecraft.block.BlockRotatedPillar;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import com.dima16263690d.bleckshlng.BleckShlng;
import com.dima16263690d.bleckshlng.fluid.state.FluidStateBridge;
import com.dima16263690d.bleckshlng.fluid.network.FluidNetwork;
import com.dima16263690d.bleckshlng.fluid.waterlogging.ChunkFluidData;
import com.dima16263690d.bleckshlng.fluid.waterlogging.FluidTickScheduler;
import com.dima16263690d.bleckshlng.fluid.waterlogging.IFluidWaterloggable;

/**
 * 1.18-style chain block backport.
 *
 * Axis and WATERLOGGED fit in the legacy 4-bit metadata without changing any
 * vanilla block IDs. Axis uses the same bit layout as BlockRotatedPillar;
 * bit 0 stores the waterlogged flag.
 */
public final class BlockChain extends BlockRotatedPillar implements IFluidWaterloggable {
    public static final PropertyBool WATERLOGGED = PropertyBool.create("waterlogged");

    private static final AxisAlignedBB Y_AXIS_BOX =
            new AxisAlignedBB(6.5D / 16.0D, 0.0D, 6.5D / 16.0D,
                    9.5D / 16.0D, 1.0D, 9.5D / 16.0D);
    private static final AxisAlignedBB X_AXIS_BOX =
            new AxisAlignedBB(0.0D, 6.5D / 16.0D, 6.5D / 16.0D,
                    1.0D, 9.5D / 16.0D, 9.5D / 16.0D);
    private static final AxisAlignedBB Z_AXIS_BOX =
            new AxisAlignedBB(6.5D / 16.0D, 6.5D / 16.0D, 0.0D,
                    9.5D / 16.0D, 9.5D / 16.0D, 1.0D);

    public BlockChain() {
        super(Material.IRON);
        setRegistryName(BleckShlng.MODID, "chain");
        setUnlocalizedName(BleckShlng.MODID + ".chain");
        setDefaultState(this.blockState.getBaseState()
                .withProperty(AXIS, EnumFacing.Axis.Y)
                .withProperty(WATERLOGGED, Boolean.FALSE));
        setHardness(5.0F);
        setResistance(6.0F);
        setSoundType(SoundType.METAL);
        setHarvestLevel("pickaxe", 0);
        setCreativeTab(CreativeTabs.DECORATIONS);
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, AXIS, WATERLOGGED);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return super.getStateFromMeta(meta & 12)
                .withProperty(WATERLOGGED, Boolean.valueOf((meta & 1) != 0));
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return super.getMetaFromState(state)
                | (state.getValue(WATERLOGGED).booleanValue() ? 1 : 0);
    }

    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing facing,
                                            float hitX, float hitY, float hitZ, int meta,
                                            EntityLivingBase placer) {
        IBlockState state = super.getStateForPlacement(
                world, pos, facing, hitX, hitY, hitZ, meta, placer);
        boolean waterAtPosition = world.getBlockState(pos).getMaterial() == Material.WATER;
        return state.withProperty(WATERLOGGED, Boolean.valueOf(waterAtPosition));
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return getAxisBox(state);
    }

    @Override
    public AxisAlignedBB getCollisionBoundingBox(IBlockState state,
                                                  IBlockAccess world, BlockPos pos) {
        return getAxisBox(state);
    }

    private static AxisAlignedBB getAxisBox(IBlockState state) {
        switch (state.getValue(AXIS)) {
            case X:
                return X_AXIS_BOX;
            case Z:
                return Z_AXIS_BOX;
            case Y:
            default:
                return Y_AXIS_BOX;
        }
    }

    @Override
    public boolean isFullCube(IBlockState state) {
        return false;
    }

    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    @Override
    public BlockRenderLayer getRenderLayer() {
        return BlockRenderLayer.SOLID;
    }

    @Override
    public boolean isWaterlogged(IBlockState state) {
        return state != null && state.getValue(WATERLOGGED).booleanValue();
    }

    @Override
    public IBlockState withWaterlogged(IBlockState state, boolean waterlogged) {
        return state == null ? null
                : state.withProperty(WATERLOGGED, Boolean.valueOf(waterlogged));
    }

    @Override
    public void onBlockAdded(World world, BlockPos pos, IBlockState state) {
        super.onBlockAdded(world, pos, state);
        if (!world.isRemote && isWaterlogged(state)) {
            FluidStateBridge stored = FluidStateBridge.source(
                    FluidStateBridge.FluidKind.WATER);
            ChunkFluidData data = ChunkFluidData.get(world);
            if (data != null) {
                data.putWater(pos, stored);
            }
            FluidNetwork.syncState(world, pos, stored);
            FluidTickScheduler.schedule(world, pos, 1);
        }
    }

    @Override
    public void updateTick(World world, BlockPos pos, IBlockState state, Random random) {
        // Waterlogged ticks are driven by FluidTickScheduler, never by the
        // ordinary block random/scheduled tick queue.
    }
}
