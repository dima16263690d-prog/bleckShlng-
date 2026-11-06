package com.dima16263690d.bleckshlng.fluid.geometry;

import net.minecraft.block.Block;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.block.material.Material;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Adapts 1.12.2 block shape/material checks to the fluid engine.
 * It deliberately does not replace or mutate the target block.
 */
public final class BlockGeometryAdapter {
    private BlockGeometryAdapter() {
    }

    public static boolean isBlocked(World world, BlockPos pos, Material fluidMaterial) {
        if (world == null || pos == null || !world.isBlockLoaded(pos)) {
            return true;
        }

        IBlockState state = world.getBlockState(pos);
        Material material = state.getMaterial();
        Block block = state.getBlock();

        if (material == fluidMaterial || material == Material.AIR) {
            return false;
        }

        if (fluidMaterial == Material.WATER && WaterloggedBlockRegistry.canWaterlog(block)) {
            return false;
        }

        if (block instanceof BlockDoor
                || block == Blocks.STANDING_SIGN
                || block == Blocks.WALL_SIGN
                || block == Blocks.LADDER
                || block == Blocks.REEDS) {
            return true;
        }

        if (material == Material.PORTAL || material == Material.STRUCTURE_VOID) {
            return true;
        }

        if (!material.blocksMovement()) {
            return false;
        }

        /*
         * Face shape is more precise than material alone. A block with a
         * non-solid upper face is not treated as a full-height wall for
         * horizontal slope searching. Placement still checks replaceability
         * or the waterlogging registry, so this cannot overwrite that block.
         */
        BlockFaceShape upperFace = state.getBlockFaceShape(world, pos, EnumFacing.UP);
        return state.isFullBlock() || upperFace == BlockFaceShape.SOLID;
    }

    public static boolean canFluidEnter(World world, BlockPos pos, IBlockState state,
                                        Material fluidMaterial) {
        if (world == null || pos == null || state == null) {
            return false;
        }

        Material targetMaterial = state.getMaterial();

        if (targetMaterial == fluidMaterial || targetMaterial == Material.LAVA) {
            return false;
        }

        if (fluidMaterial == Material.WATER
                && WaterloggedBlockRegistry.canWaterlog(state.getBlock())) {
            return true;
        }

        if (fluidMaterial == Material.LAVA && targetMaterial == Material.WATER) {
            return true;
        }

        return state.getBlock().isReplaceable(world, pos)
                && !isBlocked(world, pos, fluidMaterial);
    }
}
