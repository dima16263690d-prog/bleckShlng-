package com.dima16263690d.bleckshlng.fluid.engine;

import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Water/lava reactions adapted to the legacy block registry.
 */
public final class FluidMixingHandler {
    private FluidMixingHandler() {
    }

    /**
     * Handles side/upper water contact with lava, matching the 1.12.2
     * source rules: source lava becomes obsidian; low-level flowing lava
     * becomes cobblestone.
     */
    public static boolean reactAt(World world, BlockPos pos, IBlockState state) {
        if (world == null || world.isRemote || state.getMaterial() != Material.LAVA) {
            return false;
        }

        boolean touchingWater = false;
        for (EnumFacing side : EnumFacing.values()) {
            if (side == EnumFacing.DOWN) {
                continue;
            }

            BlockPos neighbor = pos.offset(side);
            if (world.isBlockLoaded(neighbor)
                    && world.getBlockState(neighbor).getMaterial() == Material.WATER) {
                touchingWater = true;
                break;
            }
        }

        if (!touchingWater) {
            return false;
        }

        int level = state.getBlock() instanceof BlockLiquid
                ? state.getValue(BlockLiquid.LEVEL).intValue() : 0;

        if (level == 0) {
            world.setBlockState(pos, Blocks.OBSIDIAN.getDefaultState(), 3);
            playExtinguishEffects(world, pos);
            return true;
        }

        if (level <= 4) {
            world.setBlockState(pos, Blocks.COBBLESTONE.getDefaultState(), 3);
            playExtinguishEffects(world, pos);
            return true;
        }

        return false;
    }

    /**
     * Water entering a lava block reacts with that lava before replacing it.
     * Source lava becomes obsidian; flowing lava at level 4 or stronger
     * (legacy metadata 1..4) becomes cobblestone.
     */
    public static boolean reactWaterIntoLava(World world, BlockPos pos,
                                             IBlockState lavaState) {
        if (world == null || world.isRemote || pos == null || lavaState == null
                || lavaState.getMaterial() != Material.LAVA
                || !(lavaState.getBlock() instanceof BlockLiquid)) {
            return false;
        }

        int level = lavaState.getValue(BlockLiquid.LEVEL).intValue();
        if (level == 0) {
            world.setBlockState(pos, Blocks.OBSIDIAN.getDefaultState(), 3);
            playExtinguishEffects(world, pos);
            return true;
        }
        if (level <= 4) {
            world.setBlockState(pos, Blocks.COBBLESTONE.getDefaultState(), 3);
            playExtinguishEffects(world, pos);
            return true;
        }
        return false;
    }

    /**
     * Vanilla 1.12.2 turns lava flowing directly down into water into stone.
     */
    public static boolean reactDownward(World world, BlockPos pos, IBlockState targetState) {
        if (targetState.getMaterial() != Material.WATER) {
            return false;
        }

        world.setBlockState(pos, Blocks.STONE.getDefaultState(), 3);
        playExtinguishEffects(world, pos);
        return true;
    }

    public static void playExtinguishEffects(World world, BlockPos pos) {
        world.playSound((EntityPlayer) null, pos, SoundEvents.BLOCK_LAVA_EXTINGUISH,
                SoundCategory.BLOCKS, 0.5F,
                2.6F + (world.rand.nextFloat() - world.rand.nextFloat()) * 0.8F);

        for (int i = 0; i < 8; i++) {
            world.spawnParticle(EnumParticleTypes.SMOKE_LARGE,
                    pos.getX() + world.rand.nextDouble(),
                    pos.getY() + 1.2D,
                    pos.getZ() + world.rand.nextDouble(),
                    0.0D, 0.0D, 0.0D);
        }
    }
}
