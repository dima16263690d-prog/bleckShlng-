package com.dima16263690d.bleckshlng.fluid.engine;

import java.util.Random;
import java.util.Set;

import net.minecraft.block.BlockDynamicLiquid;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import com.dima16263690d.bleckshlng.fluid.geometry.BlockGeometryAdapter;
import com.dima16263690d.bleckshlng.fluid.geometry.FluidOcclusionCache;
import com.dima16263690d.bleckshlng.fluid.geometry.FluidShapeCalculator;
import com.dima16263690d.bleckshlng.fluid.geometry.WaterloggedBlockRegistry;
import com.dima16263690d.bleckshlng.fluid.state.FluidLevelMapper;
import com.dima16263690d.bleckshlng.fluid.state.FluidProperties;
import com.dima16263690d.bleckshlng.fluid.state.FluidStateBridge;
import com.dima16263690d.bleckshlng.fluid.state.FluidStateMapper;
import com.dima16263690d.bleckshlng.fluid.waterlogging.WaterloggedFluidHandler;

/**
 * Liquid update order based on the legacy BlockDynamicLiquid algorithm used
 * before the 1.13 Update Aquatic waterlogging changes.
 *
 * The old metadata-level recomputation and down-before-sideways order are
 * retained. The 1.13-era additions are isolated to fluid capacity,
 * collision-shape pass-through and waterlogged source storage.
 */
public final class FluidSpreadEngine {
    private static final int NO_FLOW = -1;

    private FluidSpreadEngine() {
    }

    public static void tick(World world, BlockPos pos, IBlockState state, Random random) {
        if (world == null || world.isRemote || pos == null || state == null
                || random == null || !world.isBlockLoaded(pos)) {
            return;
        }

        Material material = state.getMaterial();
        if ((material != Material.WATER && material != Material.LAVA)
                || !(state.getBlock() instanceof BlockLiquid)) {
            return;
        }

        FluidOcclusionCache.beginFluidUpdate(world);

        if (material == Material.LAVA && FluidMixingHandler.reactAt(world, pos, state)) {
            return;
        }

        int level = state.getValue(BlockLiquid.LEVEL).intValue();
        BlockDynamicLiquid flowing = getFlowingBlock(material);
        int tickDelay = FluidProperties.tickRate(world, material);

        if (level > 0) {
            int newLevel = getNewLegacyLevel(world, pos, material);

            if (material == Material.LAVA && level < 8 && newLevel >= 0
                    && newLevel < 8 && newLevel > level && random.nextInt(4) != 0) {
                tickDelay *= 4;
            }

            if (newLevel == level) {
                placeStaticBlock(world, pos, material, state);
            } else if (newLevel < 0) {
                world.setBlockToAir(pos);
                level = NO_FLOW;
            } else {
                IBlockState changed = flowing.getDefaultState()
                        .withProperty(BlockLiquid.LEVEL, Integer.valueOf(newLevel));
                world.setBlockState(pos, changed, 2);
                world.scheduleUpdate(pos, flowing, tickDelay);
                world.notifyNeighborsOfStateChange(pos, flowing, false);
                level = newLevel;
            }
        } else {
            // Source blocks remain sources; the legacy engine makes them static.
            placeStaticBlock(world, pos, material, state);
        }

        spread(world, pos, material, level);
    }

    /**
     * A waterlogged block contributes a level-zero source without being
     * replaced by Blocks.WATER. Its delayed update follows the same old
     * down-first, then-horizontal spreading order as a vanilla source.
     */
    public static void spreadWaterloggedSource(World world, BlockPos pos) {
        if (world == null || world.isRemote || pos == null
                || !world.isBlockLoaded(pos)) {
            return;
        }

        FluidOcclusionCache.beginFluidUpdate(world);
        spread(world, pos, Material.WATER, FluidLevelMapper.SOURCE_METADATA);
    }

    /**
     * Recomputes the 1.12/1.13 metadata level in the original order:
     * horizontal neighbors, falling fluid above, then water source creation.
     * Sidecar waterlogged blocks are read as fluid metadata zero.
     */
    public static int getNewLegacyLevel(World world, BlockPos pos, Material material) {
        if (world == null || pos == null || !world.isBlockLoaded(pos)) {
            return NO_FLOW;
        }

        int minimumNeighborLevel = -100;
        int adjacentSourceBlocks = 0;

        for (EnumFacing direction : EnumFacing.Plane.HORIZONTAL) {
            BlockPos neighborPos = pos.offset(direction);
            if (!world.isBlockLoaded(neighborPos)
                    || !FluidShapeCalculator.canPassThroughWall(world, pos, direction)) {
                continue;
            }

            int neighborLevel = getDepth(world, neighborPos, material);
            if (neighborLevel < 0) {
                continue;
            }

            if (neighborLevel == FluidLevelMapper.SOURCE_METADATA) {
                adjacentSourceBlocks++;
            }

            // Legacy falling metadata (8..15) is treated as the highest
            // horizontal level when it feeds a neighboring block.
            if (neighborLevel >= 8) {
                neighborLevel = 0;
            }

            if (minimumNeighborLevel < 0
                    || neighborLevel < minimumNeighborLevel) {
                minimumNeighborLevel = neighborLevel;
            }
        }

        int decay = FluidProperties.horizontalDecay(world, material);
        int newLevel = minimumNeighborLevel + decay;
        if (newLevel >= 8 || minimumNeighborLevel < 0) {
            newLevel = NO_FLOW;
        }

        BlockPos abovePos = pos.up();
        if (world.isBlockLoaded(abovePos)
                && FluidShapeCalculator.canPassThroughWall(world, pos, EnumFacing.UP)) {
            int aboveLevel = getDepth(world, abovePos, material);
            if (aboveLevel >= 0) {
                newLevel = aboveLevel >= 8 ? aboveLevel : aboveLevel + 8;
            }
        }

        if (material == Material.WATER && adjacentSourceBlocks >= 2) {
            BlockPos belowPos = pos.down();
            if (world.isBlockLoaded(belowPos)) {
                IBlockState belowState = world.getBlockState(belowPos);
                if (belowState.getMaterial().isSolid()
                        || getDepth(world, belowPos, Material.WATER)
                        == FluidLevelMapper.SOURCE_METADATA) {
                    newLevel = FluidLevelMapper.SOURCE_METADATA;
                }
            }
        }

        return newLevel;
    }

    /**
     * Vanilla legacy order: try downward flow first. Only when downward flow
     * is blocked may a non-source flow spread sideways.
     */
    private static void spread(World world, BlockPos pos, Material material, int level) {
        BlockPos belowPos = pos.down();
        if (!world.isBlockLoaded(belowPos)) {
            return;
        }

        IBlockState belowState = world.getBlockState(belowPos);
        boolean canEnterBelow = canFlowInto(world, belowPos, belowState,
                material, EnumFacing.DOWN);

        if (canEnterBelow) {
            if (material == Material.LAVA
                    && belowState.getMaterial() == Material.WATER) {
                FluidMixingHandler.reactDownward(world, belowPos, belowState);
                return;
            }

            // The old engine adds the falling bit (8) to horizontal metadata.
            // Already-falling fluid keeps its falling level when it drops again.
            int downwardLevel = level >= 8 ? level : level + 8;
            tryFlowInto(world, belowPos, belowState, material,
                    downwardLevel, EnumFacing.DOWN);
            return;
        }

        if (level < 0 || (level != FluidLevelMapper.SOURCE_METADATA
                && !isBlocked(world, belowPos, belowState, material))) {
            return;
        }

        Set<EnumFacing> directions = FlowDirectionSearch.getBestDirections(
                world, pos, material, FluidProperties.slopeFindDistance(world, material));

        int horizontalLevel;
        if (level >= 8) {
            horizontalLevel = 1;
        } else {
            int amount = FluidLevelMapper.amountFromMetadata(level);
            int nextAmount = FluidProperties.nextHorizontalAmount(
                    world, material, amount);
            if (nextAmount <= 0) {
                return;
            }
            horizontalLevel = FluidLevelMapper.toMetadata(nextAmount, false, false);
        }

        if (horizontalLevel >= 8) {
            return;
        }

        for (EnumFacing direction : directions) {
            BlockPos targetPos = pos.offset(direction);
            if (!world.isBlockLoaded(targetPos)
                    || !FluidShapeCalculator.canPassThroughWall(world, pos, direction)) {
                continue;
            }

            IBlockState targetState = world.getBlockState(targetPos);
            tryFlowInto(world, targetPos, targetState, material,
                    horizontalLevel, direction);
        }
    }

    /**
     * Matches legacy BlockDynamicLiquid.canFlowInto while adding the
     * LiquidBlockContainer-style water capacity check for 1.13 waterlogging.
     */
    private static boolean canFlowInto(World world, BlockPos pos, IBlockState state,
                                       Material material, EnumFacing direction) {
        if (world == null || pos == null || state == null
                || !world.isBlockLoaded(pos)) {
            return false;
        }

        Material targetMaterial = state.getMaterial();
        if (targetMaterial == material) {
            return false;
        }

        // Legacy water never replaced lava. Contact is handled by the lava
        // block's own neighbor mixing check, as in the 1.12/1.13 engine.
        if (material == Material.WATER && targetMaterial == Material.LAVA) {
            return false;
        }

        return BlockGeometryAdapter.canFluidEnter(
                world, pos, state, material, direction);
    }

    /**
     * Determines whether a block below supports lateral flow. A matching
     * fluid (including a sidecar waterlogged source) is not a solid barrier.
     */
    private static boolean isBlocked(World world, BlockPos pos, IBlockState state,
                                     Material material) {
        if (world == null || pos == null || state == null
                || !world.isBlockLoaded(pos)) {
            return true;
        }

        if (!FluidShapeCalculator.canPassThroughWall(world, pos, EnumFacing.UP)) {
            return true;
        }

        FluidStateBridge fluid = FluidStateMapper.fromWorld(world, pos, state);
        if (fluid != null) {
            FluidStateBridge.FluidKind expected = material == Material.WATER
                    ? FluidStateBridge.FluidKind.WATER
                    : FluidStateBridge.FluidKind.LAVA;
            if (fluid.getKind() == expected) {
                return false;
            }
        }

        if (material == Material.WATER
                && WaterloggedBlockRegistry.canWaterlog(state.getBlock())) {
            return !canFlowInto(world, pos, state, material, EnumFacing.DOWN);
        }

        return !canFlowInto(world, pos, state, material, EnumFacing.DOWN);
    }

    private static int getDepth(World world, BlockPos pos, Material material) {
        if (world == null || pos == null || !world.isBlockLoaded(pos)) {
            return NO_FLOW;
        }

        IBlockState state = world.getBlockState(pos);
        FluidStateBridge fluid = FluidStateMapper.fromWorld(world, pos, state);
        if (fluid == null) {
            return NO_FLOW;
        }

        FluidStateBridge.FluidKind expected = material == Material.WATER
                ? FluidStateBridge.FluidKind.WATER
                : FluidStateBridge.FluidKind.LAVA;
        if (fluid.getKind() != expected) {
            return NO_FLOW;
        }
        return fluid.toLegacyMetadata();
    }

    private static void tryFlowInto(World world, BlockPos pos, IBlockState state,
                                    Material material, int level, EnumFacing direction) {
        if (!canFlowInto(world, pos, state, material, direction)) {
            return;
        }

        if (material == Material.WATER
                && WaterloggedBlockRegistry.canWaterlog(state.getBlock())) {
            WaterloggedFluidHandler.tryWaterlog(world, pos, state, level);
            return;
        }

        if (material == Material.LAVA && state.getMaterial() == Material.WATER
                && direction == EnumFacing.DOWN) {
            FluidMixingHandler.reactDownward(world, pos, state);
            return;
        }

        if (state.getMaterial() != Material.AIR) {
            if (material == Material.LAVA) {
                FluidMixingHandler.playExtinguishEffects(world, pos);
            } else {
                state.getBlock().dropBlockAsItem(world, pos, state, 0);
            }
        }

        IBlockState flowingState = getFlowingBlock(material).getDefaultState()
                .withProperty(BlockLiquid.LEVEL, Integer.valueOf(level));
        world.setBlockState(pos, flowingState, 3);
        FluidShapeCalculator.invalidate(world, pos);

        if (material == Material.LAVA) {
            FluidMixingHandler.reactAt(world, pos, world.getBlockState(pos));
        }
    }

    private static BlockDynamicLiquid getFlowingBlock(Material material) {
        return material == Material.WATER
                ? Blocks.FLOWING_WATER : Blocks.FLOWING_LAVA;
    }

    private static void placeStaticBlock(World world, BlockPos pos,
                                         Material material, IBlockState state) {
        IBlockState staticState = (material == Material.WATER
                ? Blocks.WATER : Blocks.LAVA).getDefaultState()
                .withProperty(BlockLiquid.LEVEL, state.getValue(BlockLiquid.LEVEL));
        world.setBlockState(pos, staticState, 2);
        FluidShapeCalculator.invalidate(world, pos);
    }
}
