package com.dima16263690d.bleckshlng.block;

import java.util.Random;

import net.minecraft.block.BlockContainer;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.tileentity.TileEntity;
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
 * Functional campfire backport with four persistent cooking slots.
 * 1.12.2 lacks RecipeType.CAMPFIRE_COOKING, so its TE only accepts vanilla
 * raw-food inputs and uses their existing furnace output as the recipe result.
 */
public final class BlockCampfire extends BlockContainer implements IFluidWaterloggable {
    public static final PropertyBool LIT = PropertyBool.create("lit");
    public static final PropertyBool SIGNAL_FIRE = PropertyBool.create("signal_fire");
    public static final PropertyBool WATERLOGGED = PropertyBool.create("waterlogged");
    public static final PropertyDirection FACING =
            PropertyDirection.create("facing", EnumFacing.Plane.HORIZONTAL);

    private static final AxisAlignedBB CAMPFIRE_BOX =
            new AxisAlignedBB(0.0D, 0.0D, 0.0D, 1.0D, 7.0D / 16.0D, 1.0D);

    public BlockCampfire() {
        super(Material.WOOD);
        setRegistryName(BleckShlng.MODID, "campfire");
        setUnlocalizedName(BleckShlng.MODID + ".campfire");
        setDefaultState(this.blockState.getBaseState()
                .withProperty(LIT, Boolean.TRUE)
                .withProperty(SIGNAL_FIRE, Boolean.FALSE)
                .withProperty(WATERLOGGED, Boolean.FALSE)
                .withProperty(FACING, EnumFacing.NORTH));
        setHardness(2.0F);
        setResistance(2.0F);
        setSoundType(SoundType.WOOD);
        setCreativeTab(CreativeTabs.DECORATIONS);
        setLightOpacity(0);
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, LIT, SIGNAL_FIRE, WATERLOGGED, FACING);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        boolean waterlogged = (meta & 8) != 0;
        return getDefaultState()
                .withProperty(FACING, EnumFacing.getHorizontal(meta & 3))
                .withProperty(LIT, Boolean.valueOf((meta & 4) != 0 && !waterlogged))
                .withProperty(WATERLOGGED, Boolean.valueOf(waterlogged));
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING).getHorizontalIndex()
                | (state.getValue(LIT).booleanValue() ? 4 : 0)
                | (state.getValue(WATERLOGGED).booleanValue() ? 8 : 0);
    }

    @Override
    public IBlockState getActualState(IBlockState state, IBlockAccess world, BlockPos pos) {
        boolean signal = world.getBlockState(pos.down()).getBlock() == Blocks.HAY_BLOCK;
        return state.withProperty(SIGNAL_FIRE, Boolean.valueOf(signal));
    }

    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing facing,
                                            float hitX, float hitY, float hitZ, int meta,
                                            EntityLivingBase placer) {
        boolean waterlogged = world.getBlockState(pos).getMaterial() == Material.WATER;
        return getDefaultState()
                .withProperty(FACING, placer == null
                        ? EnumFacing.NORTH : placer.getHorizontalFacing().getOpposite())
                .withProperty(LIT, Boolean.valueOf(!waterlogged))
                .withProperty(WATERLOGGED, Boolean.valueOf(waterlogged))
                .withProperty(SIGNAL_FIRE, Boolean.valueOf(
                        world.getBlockState(pos.down()).getBlock() == Blocks.HAY_BLOCK));
    }

    @Override
    public boolean canPlaceBlockAt(World world, BlockPos pos) {
        return world.getBlockState(pos).getMaterial().isReplaceable()
                && world.isSideSolid(pos.down(), EnumFacing.UP, true);
    }

    @Override
    public void neighborChanged(IBlockState state, World world, BlockPos pos,
                                net.minecraft.block.Block blockIn, BlockPos fromPos) {
        if (!world.isRemote && !world.isSideSolid(pos.down(), EnumFacing.UP, true)) {
            world.destroyBlock(pos, true);
            return;
        }
        super.neighborChanged(state, world, pos, blockIn, fromPos);
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return CAMPFIRE_BOX;
    }

    @Override
    public AxisAlignedBB getCollisionBoundingBox(IBlockState state,
                                                  IBlockAccess world, BlockPos pos) {
        return CAMPFIRE_BOX;
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
    public int getLightValue(IBlockState state) {
        return state.getValue(LIT).booleanValue()
                && !state.getValue(WATERLOGGED).booleanValue() ? 15 : 0;
    }

    @Override
    public TileEntityCampfire createNewTileEntity(World world, int meta) {
        return new TileEntityCampfire();
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state,
                                    EntityPlayer player, EnumHand hand, EnumFacing facing,
                                    float hitX, float hitY, float hitZ) {
        ItemStack held = player.getHeldItem(hand);
        TileEntity tile = world.getTileEntity(pos);
        if (!(tile instanceof TileEntityCampfire)) {
            return false;
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

        if (!held.isEmpty()) {
            TileEntityCampfire campfire = (TileEntityCampfire) tile;
            if (campfire.addFood(held)) {
                if (!world.isRemote && !player.capabilities.isCreativeMode) {
                    held.shrink(1);
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public void onEntityWalk(World world, BlockPos pos, Entity entity) {
        IBlockState state = world.getBlockState(pos);
        if (!world.isRemote && state.getBlock() == this
                && state.getValue(LIT).booleanValue()
                && !state.getValue(WATERLOGGED).booleanValue()
                && entity instanceof EntityLivingBase && !entity.isImmuneToFire()) {
            entity.attackEntityFrom(DamageSource.IN_FIRE, 1.0F);
        }
    }

    @Override
    public void randomDisplayTick(IBlockState state, World world, BlockPos pos, Random random) {
        if (!state.getValue(LIT).booleanValue()
                || state.getValue(WATERLOGGED).booleanValue()) {
            return;
        }

        if (random.nextInt(10) == 0) {
            world.playSound(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                    SoundEvents.BLOCK_FIRE_AMBIENT, SoundCategory.BLOCKS,
                    0.5F + random.nextFloat() * 0.5F,
                    random.nextFloat() * 0.7F + 0.6F, false);
        }

        if (random.nextInt(5) == 0) {
            double x = pos.getX() + 0.5D;
            double y = pos.getY() + 0.45D;
            double z = pos.getZ() + 0.5D;
            world.spawnParticle(EnumParticleTypes.FLAME, x, y, z,
                    random.nextFloat() / 2.0D, 0.00005D, random.nextFloat() / 2.0D);
            world.spawnParticle(state.getValue(SIGNAL_FIRE)
                            ? EnumParticleTypes.SMOKE_LARGE : EnumParticleTypes.SMOKE_NORMAL,
                    x, y + (state.getValue(SIGNAL_FIRE) ? 0.8D : 0.0D), z,
                    0.0D, state.getValue(SIGNAL_FIRE) ? 0.07D : 0.02D, 0.0D);
        }
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        TileEntity tile = world.getTileEntity(pos);
        if (tile instanceof TileEntityCampfire) {
            TileEntityCampfire campfire = (TileEntityCampfire) tile;
            for (int slot = 0; slot < campfire.getSizeInventory(); slot++) {
                ItemStack stack = campfire.getStackInSlot(slot);
                if (!stack.isEmpty()) {
                    EntityItem dropped = new EntityItem(world,
                            pos.getX() + 0.5D, pos.getY() + 0.3D, pos.getZ() + 0.5D,
                            stack.copy());
                    world.spawnEntity(dropped);
                }
            }
        }
        super.breakBlock(world, pos, state);
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
            world.playSound(null, pos, SoundEvents.BLOCK_FIRE_EXTINGUISH,
                    SoundCategory.BLOCKS, 1.0F, 1.0F);
            for (int i = 0; i < 20; i++) {
                world.spawnParticle(EnumParticleTypes.SMOKE_LARGE,
                        pos.getX() + 0.5D + (world.rand.nextDouble() - 0.5D) * 0.5D,
                        pos.getY() + 0.7D,
                        pos.getZ() + 0.5D + (world.rand.nextDouble() - 0.5D) * 0.5D,
                        0.0D, 0.05D, 0.0D);
            }
        }
        TileEntity tile = world.getTileEntity(pos);
        if (tile instanceof TileEntityCampfire) {
            tile.markDirty();
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
