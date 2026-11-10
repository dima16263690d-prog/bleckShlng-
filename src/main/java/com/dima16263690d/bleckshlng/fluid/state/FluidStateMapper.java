package com.dima16263690d.bleckshlng.fluid.state;

import net.minecraft.block.BlockLiquid;
import net.minecraft.block.state.IBlockState;
import net.minecraft.block.material.Material;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import com.dima16263690d.bleckshlng.fluid.waterlogging.ChunkFluidData;
import com.dima16263690d.bleckshlng.fluid.waterlogging.IFluidWaterloggable;
import com.dima16263690d.bleckshlng.fluid.network.ClientFluidStateCache;

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

        // The sidecar is authoritative when present: it preserves source,
        // amount and falling state. The WATERLOGGED property is only a legacy
        // presence bit and must not overwrite a flowing FluidState.
        if (world.isRemote) {
            FluidStateBridge cached = ClientFluidStateCache.get(
                    world.provider.getDimension(), pos.toLong());
            if (cached != null) {
                return cached;
            }
        } else {
            ChunkFluidData data = ChunkFluidData.get(world);
            FluidStateBridge stored = data == null ? null : data.getWater(pos);
            if (stored != null) {
                return stored;
            }
        }

        if (state.getBlock() instanceof IFluidWaterloggable) {
            IFluidWaterloggable adapter = (IFluidWaterloggable) state.getBlock();
            if (adapter.isWaterlogged(state)) {
                return FluidStateBridge.source(FluidStateBridge.FluidKind.WATER);
            }
        }
        return null;
    }

    public static boolean isSameFluid(FluidStateBridge first, FluidStateBridge second) {
        return first != null && second != null && first.isSameKind(second);
    }
}
