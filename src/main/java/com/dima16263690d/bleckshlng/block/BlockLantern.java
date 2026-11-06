package com.dima16263690d.bleckshlng.block;

import java.util.List;
import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import com.dima16263690d.bleckshlng.BleckShlng;
import com.dima16263690d.bleckshlng.fluid.network.FluidNetwork;
import com.dima16263690d.bleckshlng.fluid.state.FluidStateBridge;
import com.dima16263690d.bleckshlng.fluid.waterlogging.ChunkFluidData;
import com.dima16263690d.bleckshlng.fluid.waterlogging.FluidTickScheduler;
import com.dima16263690d.bleckshlng.fluid.waterlogging.IFluidWaterloggable;

/** 1.18-style lantern with hanging and waterlogged block-state properties. */
public final class BlockLantern extends Block implements IFluidWaterloggable {
    public static final PropertyBool HANGING = PropertyBool.create("hanging");
    public static final PropertyBool WATERLOGGED = PropertyBool.create("waterlogged");

    private static final AxisAlignedBB BASE = new AxisAlignedBB(
            5.0D / 16.0D, 0.0D, 5.0D / 16.0D,
            11.0D / 16.0D, 7.0D / 16.0D, 11.0D / 16.0D);
    private static final AxisAlignedBB TOP = new AxisAlignedBB(
            6.0D / 16.0D, 7.0D / 16.0D, 6.0D / 16.0D,
            10.0D / 16.0D, 9.0D / 16.0D, 10.0D / 16.0D);
    private static final AxisAlignedBB HANGING_BASE = new AxisAlignedBB(
            5.0D / 16.0D, 1.0D / 16.0D, 5.0D / 16.0D,
            11.0D / 16.0D, 8.0D / 16.0D, 11.0D / 16.0D);
    private static final AxisAlignedBB HANGING_TOP = new AxisAlignedBB(
            6.0D / 16.0D, 8.0D / 16.0D, 6.0D / 16.0D,
            10.0D / 16.0D, 10.0D / 16.0D, 10.0D / 16.0D);

    public BlockLantern() {
        super(Material.IRON);
        setRegistryName(BleckShlng.MODID, "lantern");
        setUnlocalizedName(BleckShlng.MODID + ".lantern");
        setDefaultState(this.blockState.getBaseState()
                .withProperty(HANGING, Boolean.FALSE)
                .withProperty(WATERLOGGED, Boolean.FALSE));
        setHardness(3.5F);
        setResistance(3.5F);
        setSoundType(SoundType.METAL);
        setHarvestLevel("pickaxe", 0);
        setCreativeTab(CreativeTabs.DECORATIONS);
        setLightLevel(1.0F);
        setLightOpacity(0);
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, HANGING, WATERLOGGED);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState()
                .withProperty(HANGING, Boolean.valueOf((meta & 1) != 0))
                .withProperty(WATERLOGGED, Boolean.valueOf((meta & 2) != 0));
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return (state.getValue(HANGING).booleanValue() ? 1 : 0)
                | (state.getValue(WATERLOGGED).booleanValue() ? 2 : 0);
    }

    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing facing,
                                            float hitX, float hitY, float hitZ, int meta,
                                            net.minecraft.entity.EntityLivingBase placer) {
        boolean hanging = facing == EnumFacing.DOWN;
        boolean waterlogged = world.getBlockState(pos).getMaterial() == Material.WATER;
        IBlockState state = getDefaultState()
                .withProperty(HANGING, Boolean.valueOf(hanging))
                .withProperty(WATERLOGGED, Boolean.valueOf(waterlogged));
        return canSurvive(state, world, pos) ? state : null;
    }

    @Override
    public boolean canPlaceBlockAt(World world, BlockPos pos) {
        if (!world.getBlockState(pos).getBlock().material.isReplaceable()) return false;
        return world.isSideSolid(pos.down(), EnumFacing.UP, true)
                || world.isSideSolid(pos.up(), EnumFacing.DOWN, true);
    }

    private boolean canSurvive(IBlockState state, IBlockAccess world, BlockPos pos) {
        if (state.getValue(HANGING).booleanValue()) {
            return world.isSideSolid(pos.up(), EnumFacing.DOWN, true);
        }
        return world.isSideSolid(pos.down(), EnumFacing.UP, true);
    }

    @Override
    public void neighborChanged(IBlockState state, World world, BlockPos pos,
                                Block blockIn, BlockPos fromPos) {
        if (!world.isRemote && !canSurvive(state, world, pos)) {
            dropBlockAsItem(world, pos, state, 0);
            world.setBlockToAir(pos);
            return;
        }
        super.neighborChanged(state, world, pos, blockIn, fromPos);
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return getCombinedBox(state);
    }

    @Override
    public AxisAlignedBB getCollisionBoundingBox(IBlockState state,
                                                  IBlockAccess world, BlockPos pos) {
        return NULL_AABB;
    }

    private static AxisAlignedBB getCombinedBox(IBlockState state) {
        AxisAlignedBB base = state.getValue(HANGING).booleanValue() ? HANGING_BASE : BASE;
        AxisAlignedBB top = state.getValue(HANGING).booleanValue() ? HANGING_TOP : TOP;
        return new AxisAlignedBB(
                Math.min(base.minX, top.minX), Math.min(base.minY, top.minY),
                Math.min(base.minZ, top.minZ), Math.max(base.maxX, top.maxX),
                Math.max(base.maxY, top.maxY), Math.max(base.maxZ, top.maxZ));
    }

    @Override
    public void addCollisionBoxToList(IBlockState state, World world, BlockPos pos,
                                      AxisAlignedBB entityBox, List<AxisAlignedBB> collidingBoxes,
                                      Entity entity, boolean isActualState) {
        boolean hanging = state.getValue(HANGING).booleanValue();
        if (hanging) {
            addCollisionBoxToList(pos, entityBox, collidingBoxes, HANGING_BASE);
            addCollisionBoxToList(pos, entityBox, collidingBoxes, HANGING_TOP);
        } else {
            addCollisionBoxToList(pos, entityBox, collidingBoxes, BASE);
            addCollisionBoxToList(pos, entityBox, collidingBoxes, TOP);
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
            FluidStateBridge fluid = FluidStateBridge.source(FluidStateBridge.FluidKind.WATER);
            ChunkFluidData data = ChunkFluidData.get(world);
            if (data != null) data.putWater(pos, fluid);
            FluidNetwork.syncState(world, pos, fluid);
            FluidTickScheduler.schedule(world, pos, 1);
        }
    }

    @Override
    public void randomDisplayTick(IBlockState state, World world, BlockPos pos, Random random) {
        if (random.nextInt(10) == 0) {
            double x = pos.getX() + 0.5D;
            double y = pos.getY() + (state.getValue(HANGING).booleanValue() ? 0.65D : 0.55D);
            double z = pos.getZ() + 0.5D;
            world.spawnParticle(net.minecraft.util.EnumParticleTypes.SMOKE_NORMAL,
                    x, y, z, 0.0D, 0.01D, 0.0D);
        }
    }
}
