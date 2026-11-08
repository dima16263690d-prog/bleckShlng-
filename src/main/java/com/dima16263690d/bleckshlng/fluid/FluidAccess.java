package com.dima16263690d.bleckshlng.fluid;

import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

/**
 * Bridges the legacy 1.12.2 world representation (liquid blocks carrying a metadata
 * {@code level}) to the ported 1.21 {@link FluidState} model.
 */
public final class FluidAccess {
    private FluidAccess() {
    }

    public static FluidState getFluidState(IBlockAccess world, BlockPos pos) {
        IBlockState state = world.getBlockState(pos);
        Material material = state.getMaterial();

        if (material == Material.WATER) {
            return FluidState.fromLegacy(Fluids.WATER, getLevel(state));
        }
        if (material == Material.LAVA) {
            return FluidState.fromLegacy(Fluids.LAVA, getLevel(state));
        }
        return Fluids.EMPTY.defaultState();
    }

    private static int getLevel(IBlockState state) {
        if (state.getBlock() instanceof BlockLiquid) {
            return state.getValue(BlockLiquid.LEVEL);
        }
        return 0;
    }

    /**
     * Legacy metadata value (0..15) for the fluid stored in a block state, or {@code -1} when
     * the state does not carry a liquid.
     */
    public static int getLevel(IBlockAccess world, BlockPos pos) {
        IBlockState state = world.getBlockState(pos);
        if (state.getBlock() instanceof BlockLiquid) {
            return state.getValue(BlockLiquid.LEVEL);
        }
        return -1;
    }

    public static IBlockState legacyBlock(Material material, int level) {
        net.minecraft.block.Block block = material == Material.LAVA ? Blocks.LAVA : Blocks.WATER;
        if (level >= 8) {
            block = material == Material.LAVA ? Blocks.FLOWING_LAVA : Blocks.FLOWING_WATER;
        }
        return block.getDefaultState().withProperty(BlockLiquid.LEVEL, level);
    }
}
