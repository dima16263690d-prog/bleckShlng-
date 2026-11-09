package com.dima16263690d.bleckshlng.fluid.geometry;

import net.minecraft.block.Block;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.BlockLadder;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.BlockSign;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.block.material.Material;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
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
        if (targetMaterial == fluidMaterial) return false;

        // LiquidBlockContainer is checked before the general exclusions in
        // FlowingFluid.canHoldFluid. Signs and ladders accept water, but their
        // adapters correctly reject lava.
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
        if (isNeverFluidContainer(targetBlock, targetMaterial)) {
            return false;
        }

        // LavaFluid.canBeReplacedWith (1.18.2) requires a water
        // column at least 4/9 high. The rule is not limited to downward
        // flow; downward contact is converted to stone by spreadTo().
        if (fluidMaterial == Material.LAVA && targetMaterial == Material.WATER) {
            // WaterFluid#canBeReplacedWith in 1.18.2 only checks direction
            // and fluid tag. The 4/9 height threshold belongs to the opposite
            // case (water replacing lava in LavaFluid#canBeReplacedWith).
            return direction == EnumFacing.DOWN
                    && targetBlock instanceof BlockLiquid;
        }

        if (targetMaterial == Material.AIR) return true;
        if (!targetMaterial.blocksMovement()) return true;
        return targetBlock.isReplaceable(world, pos);
    }

    /**
     * FlowingFluid#canHoldFluid from 1.18.2.
     *
     * This is intentionally separate from canFluidEnter(): water-hole and
     * slope-route checks ask whether the destination can contain a fluid,
     * not whether the current fluid state may be replaced by this direction.
     */
    public static boolean canHoldFluid(World world, BlockPos pos,
                                       IBlockState state, Material fluidMaterial) {
        if (world == null || pos == null || state == null || fluidMaterial == null
                || !world.isBlockLoaded(pos)) {
            return false;
        }

        Block targetBlock = state.getBlock();
        Material targetMaterial = state.getMaterial();

        if (fluidMaterial == Material.WATER
                && WaterloggedBlockRegistry.canWaterlog(targetBlock)) {
            return canFluidEnter(world, pos, state, fluidMaterial, EnumFacing.DOWN);
        }

        if (isNeverFluidContainer(targetBlock, targetMaterial)) {
            return false;
        }

        // Match FlowingFluid.canHoldFluid: generic non-container blocks are
        // accepted only when their material does not block motion. Do not use
        // Block#isReplaceable here; replaceability belongs to canSpreadTo.
        return !targetMaterial.blocksMovement();
    }

    private static boolean isNeverFluidContainer(Block block, Material material) {
        // FlowingFluid excludes doors, signs, ladders, sugar cane, portals
        // and structure void from its generic replaceable-block path. Signs
        // and ladders have already had their water-container rule checked.
        return block instanceof BlockDoor
                || block instanceof BlockSign
                || block instanceof BlockLadder
                || block == Blocks.REEDS || block == Blocks.WATERLILY
                || material == Material.PORTAL
                || material == Material.STRUCTURE_VOID;
    }
}
