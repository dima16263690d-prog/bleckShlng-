package com.dima16263690d.bleckshlng.fluid;

import java.util.Random;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.Item;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/**
 * Port of {@code net.minecraft.world.level.material.WaterFluid} from Minecraft 1.21.
 * Source and flowing variants are nested subclasses, mirroring the vanilla hierarchy.
 */
public abstract class WaterFluid extends FlowingFluid {
    protected WaterFluid(String name) {
        super(name);
    }

    @Override
    public Item getBucket() {
        return Items.WATER_BUCKET;
    }

    @Override
    public boolean isSame(Fluid other) {
        return other == Fluids.WATER || other == Fluids.FLOWING_WATER;
    }

    @Override
    public boolean canBeReplacedWith(FluidState state, IBlockAccess world, BlockPos pos, Fluid other, EnumFacing dir) {
        return dir == EnumFacing.DOWN && !other.isSame(Fluids.WATER);
    }

    @Override
    public int getTickDelay(World world) {
        return 5;
    }

    @Override
    protected int getDropOff(World world) {
        return 1;
    }

    @Override
    protected int getSlopeFindDistance(World world) {
        return 4;
    }

    @Override
    protected boolean canConvertToSource(World world) {
        return true;
    }

    @Override
    protected Fluid getFlowingFluid() {
        return Fluids.FLOWING_WATER;
    }

    @Override
    protected Fluid getSourceFluid() {
        return Fluids.WATER;
    }

    @Override
    protected void beforeDestroyingBlock(World world, BlockPos pos, IBlockState state) {
        state.getBlock().dropBlockAsItem(world, pos, state, 0);
    }

    @Override
    public IBlockState createLegacyBlock(FluidState state) {
        int level = state.getLegacyLevel();
        net.minecraft.block.Block block = level == 0 ? Blocks.WATER : Blocks.FLOWING_WATER;
        return block.getDefaultState().withProperty(BlockLiquid.LEVEL, level);
    }

    @Override
    public float getExplosionResistance() {
        return 100.0F;
    }

    @Override
    public void animateTick(World world, BlockPos pos, FluidState state, Random rand) {
        if (!state.isSource() && !state.isFalling()) {
            if (rand.nextInt(64) == 0) {
                world.playSound(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                        SoundEvents.BLOCK_WATER_AMBIENT, SoundCategory.BLOCKS,
                        rand.nextFloat() * 0.25F + 0.75F, rand.nextFloat() + 0.5F, false);
            }
        } else if (rand.nextInt(10) == 0) {
            world.spawnParticle(net.minecraft.util.EnumParticleTypes.SUSPENDED,
                    pos.getX() + rand.nextDouble(), pos.getY() + rand.nextDouble(), pos.getZ() + rand.nextDouble(),
                    0.0D, 0.0D, 0.0D);
        }
    }

    public static class Flowing extends WaterFluid {
        public Flowing() {
            super("flowing_water");
        }

        @Override
        protected boolean isSourceState() {
            return false;
        }

        @Override
        public int getAmount(FluidState state) {
            return state.getAmount();
        }

        @Override
        public boolean isSource(FluidState state) {
            return false;
        }
    }

    public static class Source extends WaterFluid {
        public Source() {
            super("water");
        }

        @Override
        protected boolean isSourceState() {
            return true;
        }

        @Override
        public int getAmount(FluidState state) {
            return FluidState.AMOUNT_FULL;
        }

        @Override
        public boolean isSource(FluidState state) {
            return true;
        }
    }
}
