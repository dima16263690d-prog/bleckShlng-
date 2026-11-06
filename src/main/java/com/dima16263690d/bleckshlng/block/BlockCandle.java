package com.dima16263690d.bleckshlng.block;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.properties.PropertyInteger;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
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

/**
 * Backport of the 1.18 candle block: 1-4 candles, lit state and waterlogging.
 * All four properties fit in legacy metadata without a TileEntity.
 */
public final class BlockCandle extends Block implements IFluidWaterloggable {
    public static final PropertyInteger CANDLES = PropertyInteger.create("candles", 1, 4);
    public static final PropertyBool LIT = PropertyBool.create("lit");
    public static final PropertyBool WATERLOGGED = PropertyBool.create("waterlogged");

    private static final AxisAlignedBB[] BOXES = new AxisAlignedBB[] {
            new AxisAlignedBB(7.0D / 16.0D, 0.0D, 7.0D / 16.0D,
                    9.0D / 16.0D, 6.0D / 16.0D, 9.0D / 16.0D),
            new AxisAlignedBB(5.0D / 16.0D, 0.0D, 6.0D / 16.0D,
                    11.0D / 16.0D, 6.0D / 16.0D, 9.0D / 16.0D),
            new AxisAlignedBB(5.0D / 16.0D, 0.0D, 6.0D / 16.0D,
                    10.0D / 16.0D, 6.0D / 16.0D, 11.0D / 16.0D),
            new AxisAlignedBB(5.0D / 16.0D, 0.0D, 5.0D / 16.0D,
                    11.0D / 16.0D, 6.0D / 16.0D, 10.0D / 16.0D)
    };

    public BlockCandle() {
        super(Material.CIRCUITS);
        setRegistryName(BleckShlng.MODID, "candle");
        setUnlocalizedName(BleckShlng.MODID + ".candle");
        setDefaultState(this.blockState.getBaseState()
                .withProperty(CANDLES, Integer.valueOf(1))
                .withProperty(LIT, Boolean.FALSE)
                .withProperty(WATERLOGGED, Boolean.FALSE));
        setHardness(0.1F);
        setSoundType(SoundType.PLANT);
        setCreativeTab(CreativeTabs.DECORATIONS);
        setLightOpacity(0);
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, CANDLES, LIT, WATERLOGGED);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        int count = (meta & 3) + 1;
        boolean lit = (meta & 4) != 0;
        boolean waterlogged = (meta & 8) != 0;
        return getDefaultState().withProperty(CANDLES, Integer.valueOf(count))
                .withProperty(LIT, Boolean.valueOf(lit && !waterlogged))
                .withProperty(WATERLOGGED, Boolean.valueOf(waterlogged));
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        int count = state.getValue(CANDLES).intValue() - 1;
        int meta = count & 3;
        if (state.getValue(LIT).booleanValue()) meta |= 4;
        if (state.getValue(WATERLOGGED).booleanValue()) meta |= 8;
        return meta;
    }

    @Override
    public int getLightValue(IBlockState state) {
        return state.getValue(LIT).booleanValue()
                ? state.getValue(CANDLES).intValue() * 3 : 0;
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return BOXES[state.getValue(CANDLES).intValue() - 1];
    }

    @Override
    public AxisAlignedBB getCollisionBoundingBox(IBlockState state,
                                                  IBlockAccess world, BlockPos pos) {
        return BOXES[state.getValue(CANDLES).intValue() - 1];
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
    public boolean canPlaceBlockAt(World world, BlockPos pos) {
        return super.canPlaceBlockAt(world, pos)
                && world.isSideSolid(pos.down(), EnumFacing.UP, true);
    }

    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing facing,
                                            float hitX, float hitY, float hitZ, int meta,
                                            net.minecraft.entity.EntityLivingBase placer) {
        IBlockState state = getStateFromMeta(meta);
        if (world.getBlockState(pos).getMaterial() == Material.WATER) {
            state = state.withProperty(WATERLOGGED, Boolean.TRUE)
                    .withProperty(LIT, Boolean.FALSE);
        }
        return state;
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state,
                                    EntityPlayer player, EnumHand hand, EnumFacing facing,
                                    float hitX, float hitY, float hitZ) {
        ItemStack held = player.getHeldItem(hand);

        if (held.isEmpty() && state.getValue(LIT).booleanValue()) {
            if (!world.isRemote) {
                extinguish(world, pos, state);
            }
            return true;
        }

        if (held.getItem() == Item.getItemFromBlock(this)
                && state.getValue(CANDLES).intValue() < 4
                && !player.isSneaking()) {
            if (!world.isRemote) {
                world.setBlockState(pos, state.withProperty(CANDLES,
                        Integer.valueOf(state.getValue(CANDLES).intValue() + 1)), 3);
                if (!player.capabilities.isCreativeMode) {
                    held.shrink(1);
                }
            }
            return true;
        }

        if (!state.getValue(LIT).booleanValue()
                && !state.getValue(WATERLOGGED).booleanValue()
                && (held.getItem() == Items.FLINT_AND_STEEL
                || held.getItem() == Items.FIRE_CHARGE)) {
            if (!world.isRemote) {
                world.setBlockState(pos, state.withProperty(LIT, Boolean.TRUE), 3);
                if (held.getItem() == Items.FLINT_AND_STEEL) {
                    held.damageItem(1, player);
                } else if (!player.capabilities.isCreativeMode) {
                    held.shrink(1);
                }
                world.playSound(null, pos, SoundEvents.ITEM_FLINTANDSTEEL_USE,
                        SoundCategory.BLOCKS, 1.0F, world.rand.nextFloat() * 0.4F + 0.8F);
            }
            return true;
        }
        return false;
    }

    private static void extinguish(World world, BlockPos pos, IBlockState state) {
        world.setBlockState(pos, state.withProperty(LIT, Boolean.FALSE), 3);
        for (int i = 0; i < state.getValue(CANDLES).intValue(); i++) {
            world.spawnParticle(EnumParticleTypes.SMOKE_NORMAL,
                    pos.getX() + 0.5D, pos.getY() + 0.45D, pos.getZ() + 0.5D,
                    0.0D, 0.08D, 0.0D);
        }
        world.playSound(null, pos, SoundEvents.BLOCK_FIRE_EXTINGUISH,
                SoundCategory.BLOCKS, 1.0F, 1.0F);
    }

    @Override
    public void randomDisplayTick(IBlockState state, World world, BlockPos pos, Random random) {
        if (!state.getValue(LIT).booleanValue()) return;
        int count = state.getValue(CANDLES).intValue();
        for (int i = 0; i < count; i++) {
            double x = pos.getX() + 0.5D + (i % 2 == 0 ? -0.11D : 0.11D);
            double z = pos.getZ() + 0.5D + (i < 2 ? -0.11D : 0.11D);
            double y = pos.getY() + 0.39D;
            world.spawnParticle(EnumParticleTypes.FLAME, x, y, z, 0.0D, 0.0D, 0.0D);
            if (random.nextInt(3) == 0) {
                world.spawnParticle(EnumParticleTypes.SMOKE_NORMAL, x, y + 0.03D, z,
                        0.0D, 0.01D, 0.0D);
            }
        }
        if (random.nextInt(40) == 0) {
            world.playSound(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                    SoundEvents.BLOCK_FIRE_AMBIENT, SoundCategory.BLOCKS,
                    0.25F, random.nextFloat() * 0.4F + 0.8F, false);
        }
    }

    @Override
    public boolean isWaterlogged(IBlockState state) {
        return state != null && state.getValue(WATERLOGGED).booleanValue();
    }

    @Override
    public IBlockState withWaterlogged(IBlockState state, boolean waterlogged) {
        if (state == null) return null;
        IBlockState changed = state.withProperty(WATERLOGGED, Boolean.valueOf(waterlogged));
        return waterlogged ? changed.withProperty(LIT, Boolean.FALSE) : changed;
    }

    @Override
    public void onWaterlogged(World world, BlockPos pos,
                              IBlockState previousState, IBlockState waterloggedState) {
        if (previousState.getValue(LIT).booleanValue()) {
            for (int i = 0; i < previousState.getValue(CANDLES).intValue(); i++) {
                world.spawnParticle(EnumParticleTypes.SMOKE_NORMAL,
                        pos.getX() + 0.5D, pos.getY() + 0.45D, pos.getZ() + 0.5D,
                        0.0D, 0.08D, 0.0D);
            }
            world.playSound(null, pos, SoundEvents.BLOCK_FIRE_EXTINGUISH,
                    SoundCategory.BLOCKS, 1.0F, 1.0F);
        }
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
}
