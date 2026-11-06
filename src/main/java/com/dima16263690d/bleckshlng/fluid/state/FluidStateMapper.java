package com.dima16263690d.bleckshlng.fluid.state;

import net.minecraft.block.BlockLiquid;
import net.minecraft.block.state.IBlockState;
import net.minecraft.block.material.Material;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import com.dima16263690d.bleckshlng.fluid.waterlogging.ChunkFluidData;

/**
 * Reads a fluid state from either the legacy BlockLiquid metadata or the
 * persistent waterlogged-state sidecar.
 */
public final class FluidStateMapper {
    private FluidStateMapper() {
    }

    public static FluidStateBridge fromBlockState(IBlockState state) {
        if (state == null || !(state.getBlock() instanceof BlockLiquid)) {
            return null;
        }

        Material material = state.getMaterial();
        FluidStateBridge.FluidKind kind;
        if (material == Material.WATER) {
            kind = FluidStateBridge.FluidKind.WATER;
        } else if (material == Material.LAVA) {
            kind = FluidStateBridge.FluidKind.LAVA;
        } else {
            return null;
        }

        return FluidStateBridge.fromLegacyMetadata(kind,
                state.getValue(BlockLiquid.LEVEL).intValue());
    }

    public static FluidStateBridge fromWorld(World world, BlockPos pos,
                                             IBlockState state) {
        FluidStateBridge blockState = fromBlockState(state);
        if (blockState != null) {
            return blockState;
        }

        if (world == null || pos == null || state == null) {
            return null;
        }

        ChunkFluidData data = ChunkFluidData.get(world);
        return data == null ? null : data.getWater(pos);
    }

    public static boolean isSameFluid(FluidStateBridge first, FluidStateBridge second) {
        return first != null && second != null && first.isSameKind(second);
    }
}
