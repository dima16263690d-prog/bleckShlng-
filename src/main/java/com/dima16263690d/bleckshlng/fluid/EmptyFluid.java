package com.dima16263690d.bleckshlng.fluid;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/**
 * Port of {@code net.minecraft.world.level.material.EmptyFluid} from Minecraft 1.21.
 */
public class EmptyFluid extends Fluid {
    public EmptyFluid() {
        super("empty");
    }

    @Override
    public Item getBucket() {
        return Items.AIR;
    }

    @Override
    public boolean isSame(Fluid other) {
        return other == this;
    }

    @Override
    public boolean canBeReplacedWith(FluidState state, IBlockAccess world, BlockPos pos, Fluid other, EnumFacing dir) {
        return true;
    }

    @Override
    public Vec3d getFlow(IBlockAccess world, BlockPos pos, FluidState state) {
        return Vec3d.ZERO;
    }

    @Override
    public int getTickDelay(World world) {
        return 0;
    }

    @Override
    public boolean isEmpty() {
        return true;
    }

    @Override
    public float getExplosionResistance() {
        return 0.0F;
    }

    @Override
    public float getHeight(FluidState state, IBlockAccess world, BlockPos pos) {
        return 0.0F;
    }

    @Override
    public float getOwnHeight(FluidState state) {
        return 0.0F;
    }

    @Override
    public IBlockState createLegacyBlock(FluidState state) {
        return Blocks.AIR.getDefaultState();
    }

    @Override
    public boolean isSource(FluidState state) {
        return false;
    }

    @Override
    public int getAmount(FluidState state) {
        return 0;
    }

    @Override
    protected boolean isSourceState() {
        return false;
    }
}
