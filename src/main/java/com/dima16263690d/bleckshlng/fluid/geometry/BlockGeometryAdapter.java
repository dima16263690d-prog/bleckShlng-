package com.dima16263690d.bleckshlng.fluid.geometry;

import net.minecraft.block.Block;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.block.material.Material;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import com.dima16263690d.bleckshlng.fluid.state.FluidStateBridge;
import com.dima16263690d.bleckshlng.fluid.state.FluidHeightCalculator;
import com.dima16263690d.bleckshlng.fluid.state.FluidStateMapper;
import com.dima16263690d.bleckshlng.fluid.waterlogging.ChunkFluidData;
import com.dima16263690d.bleckshlng.fluid.waterlogging.IFluidWaterloggable;

/** Adapts legacy material, replaceability and collision geometry to fluid rules. */
public final class BlockGeometryAdapter {
    private BlockGeometryAdapter() { }

    public static boolean isBlocked(World world, BlockPos pos, Material fluidMaterial) {
        if (world == null || pos == null || !world.isBlockLoaded(pos)) return true;
        IBlockState state = world.getBlockState(pos);
        Material material = state.getMaterial();
        Block block = state.getBlock();
        if (material == fluidMaterial || material == Material.AIR) return false;
        if (fluidMaterial == Material.WATER && WaterloggedBlockRegistry.canWaterlog(block)) return false;
        if (isNeverFluidContainer(block, material)) return true;
        return material.blocksMovement() && FluidShapeCalculator.fillsBlock(world, pos, state);
    }

    public static boolean canFluidEnter(World world, BlockPos pos, IBlockState state, Material fluidMaterial) {
        return canFluidEnter(world, pos, state, fluidMaterial, EnumFacing.DOWN);
    }

    public static boolean canFluidEnter(World world, BlockPos pos, IBlockState state,
                                        Material fluidMaterial, EnumFacing direction) {
        if (world == null || pos == null || state == null || direction == null) return false;
        Material targetMaterial = state.getMaterial();
        Block targetBlock = state.getBlock();
        if (targetMaterial == fluidMaterial || isNeverFluidContainer(targetBlock, targetMaterial)) return false;

        // Lava displaces water only while flowing down; the lava handler turns it to stone.
        if (fluidMaterial == Material.LAVA && targetMaterial == Material.WATER) {
            return direction == EnumFacing.DOWN && targetBlock instanceof BlockLiquid;
        }

        if (fluidMaterial == Material.WATER && WaterloggedBlockRegistry.canWaterlog(targetBlock)) {
            if (targetBlock instanceof IFluidWaterloggable) {
                return !((IFluidWaterloggable) targetBlock).isWaterlogged(state);
            }

            // Legacy vanilla shapes store water in the sidecar. Match
            // SimpleWaterloggedBlock.canPlaceLiquid(): a filled block cannot
            // accept a second fluid state.
            ChunkFluidData data = ChunkFluidData.get(world);
            return data != null && data.getWater(pos) == null;
        }
        if (targetMaterial == Material.AIR) return true;
        if (!targetMaterial.blocksMovement()) return true;
        return targetBlock.isReplaceable(world, pos);
    }

    private static boolean isNeverFluidContainer(Block block, Material material) {
        // Signs and ladders are waterloggable in Java Edition 1.13.2.
        // Doors, sugar cane, portals and structure void remain excluded.
        return block instanceof BlockDoor
                || block == Blocks.REEDS || material == Material.PORTAL
                || material == Material.STRUCTURE_VOID;
    }
}
