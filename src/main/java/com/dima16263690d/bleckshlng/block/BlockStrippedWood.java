package com.dima16263690d.bleckshlng.block;

import com.dima16263690d.bleckshlng.BleckShlng;
import net.minecraft.block.BlockRotatedPillar;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/**
 * Shared block implementation for stripped logs and stripped wood.
 */
public final class BlockStrippedWood extends BlockRotatedPillar {
    public BlockStrippedWood(String name) {
        super(Material.WOOD);

        setRegistryName(BleckShlng.MODID, name);
        setUnlocalizedName(BleckShlng.MODID + "." + name);
        setDefaultState(this.blockState.getBaseState().withProperty(AXIS, EnumFacing.Axis.Y));
        setHardness(2.0F);
        setResistance(2.0F);
        setSoundType(SoundType.WOOD);
        setHarvestLevel("axe", 0);
        setCreativeTab(CreativeTabs.BUILDING_BLOCKS);
    }

    @Override
    public boolean canSustainLeaves(IBlockState state, IBlockAccess world, BlockPos pos) {
        return true;
    }

    @Override
    public boolean isWood(IBlockAccess world, BlockPos pos) {
        return true;
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        // Match vanilla log behaviour so leaves can decay after their last
        // supporting stripped log is removed.
        if (!world.isRemote && world.isAreaLoaded(pos, 4)) {
            for (int x = -4; x <= 4; x++) {
                for (int y = -4; y <= 4; y++) {
                    for (int z = -4; z <= 4; z++) {
                        BlockPos leafPos = pos.add(x, y, z);
                        IBlockState leafState = world.getBlockState(leafPos);
                        net.minecraft.block.Block leafBlock = leafState.getBlock();

                        if (leafBlock.isLeaves(leafState, world, leafPos)) {
                            leafBlock.beginLeavesDecay(leafState, world, leafPos);
                        }
                    }
                }
            }
        }

        super.breakBlock(world, pos, state);
    }
}
