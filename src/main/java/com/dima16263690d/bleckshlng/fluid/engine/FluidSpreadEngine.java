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
import com.dima16263690d.bleckshlng.fluid.geometry.FluidShapeCalculator;
import com.dima16263690d.bleckshlng.fluid.geometry.FluidOcclusionCache;
import com.dima16263690d.bleckshlng.fluid.geometry.WaterloggedBlockRegistry;
import com.dima16263690d.bleckshlng.fluid.state.FluidLevelMapper;
import com.dima16263690d.bleckshlng.fluid.state.FluidProperties;
import com.dima16263690d.bleckshlng.fluid.state.FluidStateBridge;
import com.dima16263690d.bleckshlng.fluid.state.FluidStateMapper;
import com.dima16263690d.bleckshlng.fluid.waterlogging.WaterloggedFluidHandler;

/**
 * FlowingFluid-compatible state recomputation and spreading.
 *
 * This ports the 1.18.2 order:
 * 1) recompute a non-source state from horizontal neighbors and the block above;
 * 2) try the downward route;
 * 3) spread sideways only when the modern source/hole rules allow it;
 * 4) after a successful fall, also spread sideways when at least three
 *    neighboring source fluids are present.
 *
 * Vanilla 1.12.2 water/lava blocks and their 0..15 metadata remain untouched.
 */
public final class FluidSpreadEngine {
    private static final int NO_FLOW = -1;
    private static final float FALLING_SIDE_AMOUNT = 7.0F;

    private FluidSpreadEngine() { }

    public static void tick(World world, BlockPos pos, IBlockState state, Random random) {
        if (world == null || world.isRemote || pos == null || state == null
                || random == null || !world.isBlockLoaded(pos)) return;

        FluidOcclusionCache.beginFluidUpdate(world);
        Material material = state.getMaterial();
        if ((material != Material.WATER && material != Material.LAVA)
                || !(state.getBlock() instanceof BlockLiquid)) return;

        if (material == Material.LAVA && FluidMixingHandler.reactAt(world, pos, state)) return;

        int level = state.getValue(BlockLiquid.LEVEL).intValue();
        BlockDynamicLiquid flowing = getFlowingBlock(material);

        // A source remains a source. Flowing states are recomputed like
        // FlowingFluid.getNewLiquid() before any new spread is attempted.
        if (level != 0) {
            int newLevel = getNewLegacyLevel(world, pos, material);
            if (newLevel == NO_FLOW) {
                world.setBlockToAir(pos);
                return;
            }

            if (newLevel != level) {
                int delay = FluidProperties.tickRate(world, material);
                if (material == Material.LAVA
                        && isSlowerHorizontalLava(level, newLevel)
                        && random.nextInt(4) != 0) {
                    delay *= 4;
                }

                IBlockState changed = flowing.getDefaultState()
                        .withProperty(BlockLiquid.LEVEL, Integer.valueOf(newLevel));
                world.setBlockState(pos, changed, 2);
                world.scheduleUpdate(pos, flowing, delay);
                world.notifyNeighborsOfStateChange(pos, flowing, false);
                level = newLevel;
            } else {
                placeStaticBlock(world, pos, material, state);
            }
        } else {
            placeStaticBlock(world, pos, material, state);
        }

        spread(world, pos, material, level);
    }

    /**
     * A waterlogged legacy block supplies a source fluid state without being
     * replaced by Blocks.WATER. This is invoked by the separate fluid queue.
     */
    public static void spreadWaterloggedSource(World world, BlockPos pos) {
        if (world == null || world.isRemote || pos == null || !world.isBlockLoaded(pos)) return;
        FluidOcclusionCache.beginFluidUpdate(world);
        spread(world, pos, Material.WATER, 0);
    }

    /**
     * Port of FlowingFluid.getNewLiquid(), returned as legacy metadata.
     * -1 means Fluids.EMPTY; 0 is a source; 8..15 is falling.
     */
    public static int getNewLegacyLevel(World world, BlockPos pos, Material material) {
        if (world == null || pos == null || !world.isBlockLoaded(pos)) return NO_FLOW;

        int maxAmount = 0;
        int sourceCount = 0;

        for (EnumFacing direction : EnumFacing.Plane.HORIZONTAL) {
            BlockPos neighborPos = pos.offset(direction);
            if (!world.isBlockLoaded(neighborPos)
                    || !FluidShapeCalculator.canPassThroughWall(world, pos, direction)) continue;

            IBlockState neighbor = world.getBlockState(neighborPos);
            int neighborLevel = getDepth(world, neighborPos, material);
            if (neighborLevel < 0) continue;

            if (neighborLevel == 0) sourceCount++;
            // FluidState.getAmount() is independent of the FALLING flag.
            // For legacy levels 8..15, the lower three bits still encode amount.
            int amount = FluidLevelMapper.amountFromMetadata(neighborLevel);
            if (amount > maxAmount) maxAmount = amount;
        }

        // Water may become a source when two source neighbors are present and
        // the block below is solid or already contains a water source.
        if (material == Material.WATER && sourceCount >= 2) {
            BlockPos belowPos = pos.down();
            if (world.isBlockLoaded(belowPos)) {
                IBlockState below = world.getBlockState(belowPos);
                if (below.getMaterial().isSolid()
                        || getDepth(world, belowPos, Material.WATER) == 0) {
                    return 0;
                }
            }
        }

        // A matching fluid directly above creates the falling state.
        BlockPos abovePos = pos.up();
        if (world.isBlockLoaded(abovePos)
                && getDepth(world, abovePos, material) >= 0
                && FluidShapeCalculator.canPassThroughWall(world, pos, EnumFacing.UP)) {
            // FlowingFluid.getNewLiquid() creates a full falling state
            // whenever matching fluid is directly above.
            return FluidLevelMapper.toMetadata(8, true, false);
        }

        int amount = maxAmount - FluidProperties.horizontalDecay(world, material);
        if (amount <= 0) return NO_FLOW;
        return FluidLevelMapper.toMetadata(amount, false, false);
    }

    private static void spread(World world, BlockPos pos, Material material, int level) {
        BlockPos belowPos = pos.down();
        if (!world.isBlockLoaded(belowPos)) return;

        IBlockState belowState = world.getBlockState(belowPos);
        boolean canPassDown = FluidShapeCalculator.canPassThroughWall(
                world, pos, EnumFacing.DOWN);
        boolean canEnterBelow = canPassDown && BlockGeometryAdapter.canFluidEnter(
                world, belowPos, belowState, material, EnumFacing.DOWN);

        if (canEnterBelow) {
            int belowLevel = getNewLegacyLevel(world, belowPos, material);
            if (belowLevel != NO_FLOW) {
                if (material == Material.LAVA && belowState.getMaterial() == Material.WATER) {
                    FluidMixingHandler.reactDownward(world, belowPos, belowState);
                    return;
                }

                tryFlowInto(world, belowPos, belowState, material, belowLevel, EnumFacing.DOWN);

                // Modern behavior: a source surrounded by three other sources
                // can also replenish the horizontal sides while flowing down.
                if (sourceNeighborCount(world, pos, material) >= 3) {
                    spreadToSides(world, pos, material, level);
                }
                return;
            }
        }

        // Flowing fluid does not spill sideways when a valid downward hole
        // exists. Sources may always spread sideways.
        if (level == 0 || !isWaterHole(world, pos, material)) {
            spreadToSides(world, pos, material, level);
        }
    }

    private static void spreadToSides(World world, BlockPos pos,
                                      Material material, int legacyLevel) {
        int amount = FluidLevelMapper.amountFromMetadata(legacyLevel);
        int nextAmount = FluidLevelMapper.isFallingMetadata(legacyLevel)
                ? (int) FALLING_SIDE_AMOUNT
                : amount - FluidProperties.horizontalDecay(world, material);
        if (nextAmount <= 0) return;

        Set<EnumFacing> directions = FlowDirectionSearch.getBestDirections(
                world, pos, material, FluidProperties.slopeFindDistance(world, material));

        for (EnumFacing direction : directions) {
            BlockPos targetPos = pos.offset(direction);
            if (!world.isBlockLoaded(targetPos)
                    || !FluidShapeCalculator.canPassThroughWall(world, pos, direction)) continue;

            IBlockState targetState = world.getBlockState(targetPos);
            if (!BlockGeometryAdapter.canFluidEnter(
                    world, targetPos, targetState, material, direction)) continue;

            int targetLevel = getNewLegacyLevel(world, targetPos, material);
            if (targetLevel == NO_FLOW) {
                targetLevel = FluidLevelMapper.toMetadata(nextAmount, false, false);
            }

            tryFlowInto(world, targetPos, targetState, material, targetLevel, direction);
        }
    }

    private static boolean isWaterHole(World world, BlockPos pos, Material material) {
        if (!world.isBlockLoaded(pos)
                || !FluidShapeCalculator.canPassThroughWall(world, pos, EnumFacing.DOWN)) return false;

        BlockPos belowPos = pos.down();
        if (!world.isBlockLoaded(belowPos)) return false;

        IBlockState below = world.getBlockState(belowPos);
        if (getDepth(world, belowPos, material) >= 0) return true;

        return BlockGeometryAdapter.canFluidEnter(
                world, belowPos, below, material, EnumFacing.DOWN);
    }

    private static int sourceNeighborCount(World world, BlockPos pos, Material material) {
        int count = 0;
        for (EnumFacing direction : EnumFacing.Plane.HORIZONTAL) {
            BlockPos neighbor = pos.offset(direction);
            if (!world.isBlockLoaded(neighbor)) continue;
            if (getDepth(world, neighbor, material) == 0) count++;
        }
        return count;
    }

    private static int getDepth(World world, BlockPos pos, Material material) {
        if (world == null || pos == null || !world.isBlockLoaded(pos)) return -1;
        IBlockState state = world.getBlockState(pos);
        FluidStateBridge fluid = FluidStateMapper.fromWorld(world, pos, state);
        if (fluid == null) return -1;
        FluidStateBridge.FluidKind kind = material == Material.WATER
                ? FluidStateBridge.FluidKind.WATER : FluidStateBridge.FluidKind.LAVA;
        if (fluid.getKind() != kind) return -1;
        return fluid.toLegacyMetadata();
    }

    private static boolean isSlowerHorizontalLava(int oldLevel, int newLevel) {
        if (oldLevel < 0 || newLevel < 0
                || FluidLevelMapper.isFallingMetadata(oldLevel)
                || FluidLevelMapper.isFallingMetadata(newLevel)) {
            return false;
        }
        // LavaFluid.getSpreadDelay() multiplies delay by four when the
        // recomputed state has a greater height than the current one.
        return FluidLevelMapper.amountFromMetadata(newLevel)
                > FluidLevelMapper.amountFromMetadata(oldLevel);
    }

    private static void tryFlowInto(World world, BlockPos pos, IBlockState state,
                                    Material material, int level, EnumFacing direction) {
        if (!BlockGeometryAdapter.canFluidEnter(world, pos, state, material, direction)) return;

        if (material == Material.WATER
                && WaterloggedBlockRegistry.canWaterlog(state.getBlock())) {
            WaterloggedFluidHandler.tryWaterlog(world, pos, state, level);
            return;
        }

        if (material == Material.WATER && state.getMaterial() == Material.LAVA) {
            if (FluidMixingHandler.reactWaterIntoLava(world, pos, state)) {
                return;
            }
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
        return material == Material.WATER ? Blocks.FLOWING_WATER : Blocks.FLOWING_LAVA;
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
