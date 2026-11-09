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
import net.minecraftforge.event.ForgeEventFactory;

import com.dima16263690d.bleckshlng.fluid.geometry.BlockGeometryAdapter;
import com.dima16263690d.bleckshlng.fluid.geometry.FluidOcclusionCache;
import com.dima16263690d.bleckshlng.fluid.geometry.FluidShapeCalculator;
import com.dima16263690d.bleckshlng.fluid.geometry.WaterloggedBlockRegistry;
import com.dima16263690d.bleckshlng.fluid.state.FluidLevelMapper;
import com.dima16263690d.bleckshlng.fluid.state.FluidHeightCalculator;
import com.dima16263690d.bleckshlng.fluid.state.FluidProperties;
import com.dima16263690d.bleckshlng.fluid.state.FluidStateBridge;
import com.dima16263690d.bleckshlng.fluid.state.FluidStateMapper;
import com.dima16263690d.bleckshlng.fluid.waterlogging.WaterloggedFluidHandler;

/**
 * Java Edition 1.18.2 FlowingFluid behavior adapted to the existing 1.12.2
 * BlockDynamicLiquid metadata and scheduled-tick system.
 *
 * The legacy level encoding is retained for old-world compatibility, while
 * Update Aquatic rules govern source conversion, waterlogging capacity,
 * collision-shape pass-through and reactions with waterlogged sources.
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
                    && newLevel < 8) {
                float currentHeight = FluidLevelMapper.amountFromMetadata(level) / 9.0F;
                float nextHeight = FluidLevelMapper.amountFromMetadata(newLevel) / 9.0F;
                if (nextHeight > currentHeight && random.nextInt(4) != 0) {
                    tickDelay *= 4;
                }
            }

            if (newLevel == level) {
                placeStaticBlock(world, pos, material, state);
            } else if (newLevel == NO_FLOW) {
                world.setBlockToAir(pos);
                return;
            } else if (material == Material.WATER
                    && newLevel == FluidLevelMapper.SOURCE_METADATA) {
                world.setBlockState(pos, Blocks.WATER.getDefaultState(), 2);
                FluidShapeCalculator.invalidate(world, pos);
                level = FluidLevelMapper.SOURCE_METADATA;
            } else {
                IBlockState changed = flowing.getDefaultState()
                        .withProperty(BlockLiquid.LEVEL, Integer.valueOf(newLevel));
                world.setBlockState(pos, changed, 2);
                world.scheduleUpdate(pos, flowing, tickDelay);
                world.notifyNeighborsOfStateChange(pos, flowing, false);
                level = newLevel;
            }
        } else {
            placeStaticBlock(world, pos, material, state);
        }

        spread(world, pos, material, level);
    }

    public static void spreadWaterloggedSource(World world, BlockPos pos) {
        if (world == null || world.isRemote || pos == null
                || !world.isBlockLoaded(pos)) {
            return;
        }

        FluidOcclusionCache.beginFluidUpdate(world);
        spread(world, pos, Material.WATER, FluidLevelMapper.SOURCE_METADATA);
    }

    /**
     * Whether fluid of {@code material} can leave {@code from} toward
     * {@code direction}. Uses BlockFaceShape (Fluidlogged-API / 1.13 rules)
     * so half-slabs open their empty half to water.
     */
    static boolean canSpreadThrough(World world, BlockPos from,
                                    EnumFacing direction, Material material) {
        if (world == null || from == null || direction == null || material == null) {
            return false;
        }
        // Primary rule: open faces on both sides of the shared plane.
        if (FluidShapeCalculator.canPassThroughWall(world, from, direction)) {
            return true;
        }
        // Waterloggable hosts still accept water even if a partial solid face
        // reports SOLID on an unrelated edge case (modded slabs, etc.).
        if (material != Material.WATER) {
            return false;
        }
        BlockPos to = from.offset(direction);
        if (!world.isBlockLoaded(to)) {
            return false;
        }
        IBlockState toState = world.getBlockState(to);
        if (WaterloggedBlockRegistry.canWaterlog(toState.getBlock())
                && FluidShapeCalculator.isFaceOpenToFluid(
                        world, to, toState, direction.getOpposite())
                && canFlowInto(world, to, toState, material, direction)) {
            return true;
        }
        return false;
    }

    public static int getNewLegacyLevel(World world, BlockPos pos, Material material) {
        if (world == null || pos == null || !world.isBlockLoaded(pos)) {
            return NO_FLOW;
        }

        int maximumNeighborAmount = 0;
        int adjacentSourceBlocks = 0;

        for (EnumFacing direction : EnumFacing.Plane.HORIZONTAL) {
            BlockPos neighborPos = pos.offset(direction);
            if (!world.isBlockLoaded(neighborPos)
                    || !canSpreadThrough(world, pos, direction, material)) {
                continue;
            }

            int neighborLevel = getDepth(world, neighborPos, material);
            if (neighborLevel < 0) {
                continue;
            }

            if (neighborLevel == FluidLevelMapper.SOURCE_METADATA) {
                adjacentSourceBlocks++;
            }

            int neighborAmount = FluidLevelMapper.amountFromMetadata(neighborLevel);
            maximumNeighborAmount = Math.max(maximumNeighborAmount, neighborAmount);
        }

        int newAmount = maximumNeighborAmount > 0
                ? FluidProperties.nextHorizontalAmount(
                        world, material, maximumNeighborAmount)
                : 0;
        int newLevel = newAmount > 0
                ? FluidLevelMapper.toMetadata(newAmount, false, false)
                : NO_FLOW;

        if (material == Material.WATER && adjacentSourceBlocks >= 2
                && ForgeEventFactory.canCreateFluidSource(world, pos,
                Blocks.FLOWING_WATER.getDefaultState(), true)) {
            BlockPos belowPos = pos.down();
            if (world.isBlockLoaded(belowPos)) {
                IBlockState belowState = world.getBlockState(belowPos);
                // FlowingFluid requires a sturdy support face, not
                // merely a solid material (e.g. a fence is solid-ish but
                // does not provide a full top support surface).
                boolean solidSupport = belowState.isSideSolid(
                        world, belowPos, EnumFacing.UP);
                boolean waterSourceBelow = getDepth(
                        world, belowPos, Material.WATER)
                        == FluidLevelMapper.SOURCE_METADATA;

                if (solidSupport || waterSourceBelow) {
                    return FluidLevelMapper.SOURCE_METADATA;
                }
            }
        }

        BlockPos abovePos = pos.up();
        if (world.isBlockLoaded(abovePos)
                && canSpreadThrough(world, pos, EnumFacing.UP, material)) {
            int aboveLevel = getDepth(world, abovePos, material);
            if (aboveLevel >= 0) {
                newLevel = 8;
            }
        }

        return newLevel;
    }

    private static void spread(World world, BlockPos pos, Material material, int level) {
        BlockPos belowPos = pos.down();
        if (!world.isBlockLoaded(belowPos)) {
            return;
        }

        IBlockState belowState = world.getBlockState(belowPos);
        boolean openDownFace = canSpreadThrough(world, pos, EnumFacing.DOWN, material);

        if (material == Material.LAVA && openDownFace
                && canFlowInto(world, belowPos, belowState, material, EnumFacing.DOWN)) {
            FluidStateBridge belowFluid = FluidStateMapper.fromWorld(
                    world, belowPos, belowState);
            if (belowFluid != null
                    && belowFluid.getKind() == FluidStateBridge.FluidKind.WATER) {
                // LavaFluid first applies canBeReplacedWith (water height >= 4/9)
                // and only then performs its downward stone reaction.
                FluidMixingHandler.reactDownward(world, belowPos, belowState);
                return;
            }
        }

        boolean canEnterBelow = openDownFace && canFlowInto(
                world, belowPos, belowState, material, EnumFacing.DOWN);

        if (canEnterBelow) {
            int downwardLevel = 8;
            tryFlowInto(world, belowPos, belowState, material,
                    downwardLevel, EnumFacing.DOWN);

            // Match FlowingFluid: a successful downward spread does not
            // trigger an independent waterlogged-only horizontal pass.
            // A source may spread sideways only when it has at least three
            // adjacent source fluids.
            if (level == FluidLevelMapper.SOURCE_METADATA
                    && sourceNeighborCount(world, pos, material) >= 3) {
                spreadHorizontally(world, pos, material, level);
            }
            return;
        }

        if (level < 0 || (level != FluidLevelMapper.SOURCE_METADATA
                && !isBlocked(world, belowPos, belowState, material))) {
            return;
        }

        spreadHorizontally(world, pos, material, level);
    }

    private static int sourceNeighborCount(World world, BlockPos pos,
                                             Material material) {
        int count = 0;
        for (EnumFacing direction : EnumFacing.Plane.HORIZONTAL) {
            BlockPos neighborPos = pos.offset(direction);
            if (world.isBlockLoaded(neighborPos)
                    && canSpreadThrough(world, pos, direction, material)
                    && getDepth(world, neighborPos, material)
                    == FluidLevelMapper.SOURCE_METADATA) {
                count++;
            }
        }
        return count;
    }

    private static void spreadHorizontally(World world, BlockPos pos,
                                           Material material, int level) {
        Set<EnumFacing> directions = FlowDirectionSearch.getBestDirections(
                world, pos, material,
                FluidProperties.slopeFindDistance(world, material));

        int spreadAmount = level >= 8 ? 7
                : FluidProperties.nextHorizontalAmount(world, material,
                FluidLevelMapper.amountFromMetadata(level));
        if (spreadAmount <= 0) {
            return;
        }

        for (EnumFacing direction : EnumFacing.Plane.HORIZONTAL) {
            BlockPos targetPos = pos.offset(direction);
            if (!world.isBlockLoaded(targetPos)
                    || !canSpreadThrough(world, pos, direction, material)) {
                continue;
            }

            IBlockState targetState = world.getBlockState(targetPos);

            // Every destination, including waterloggable hosts, must
            // first pass the same shortest-slope direction selection.
            if (!directions.contains(direction)) {
                continue;
            }

            if (material == Material.WATER
                    && WaterloggedBlockRegistry.canWaterlog(targetState.getBlock())
                    && canFlowInto(world, targetPos, targetState, material, direction)) {
                tryFlowInto(world, targetPos, targetState, material,
                        FluidLevelMapper.SOURCE_METADATA, direction);
                continue;
            }

            int targetLevel = getNewLegacyLevel(world, targetPos, material);
            if (targetLevel == NO_FLOW) {
                continue;
            }
            tryFlowInto(world, targetPos, targetState, material,
                    targetLevel, direction);
        }
    }

    static boolean canFlowInto(World world, BlockPos pos, IBlockState state,
                                       Material material, EnumFacing direction) {
        if (world == null || pos == null || state == null
                || !world.isBlockLoaded(pos)) {
            return false;
        }

        Material targetMaterial = state.getMaterial();
        if (targetMaterial == material) {
            return false;
        }

        if (material == Material.WATER
                && WaterloggedBlockRegistry.canWaterlog(state.getBlock())) {
            return BlockGeometryAdapter.canFluidEnter(
                    world, pos, state, material, direction);
        }

        if (material == Material.WATER && targetMaterial == Material.LAVA) {
            FluidStateBridge lava = FluidStateMapper.fromBlockState(state);
            return lava != null && FluidHeightCalculator.getHeight(
                    world, pos, lava) >= 4.0F / 9.0F;
        }

        return BlockGeometryAdapter.canFluidEnter(
                world, pos, state, material, direction);
    }

    private static boolean isBlocked(World world, BlockPos pos, IBlockState state,
                                     Material material) {
        if (world == null || pos == null || state == null
                || !world.isBlockLoaded(pos)) {
            return true;
        }

        if (!canSpreadThrough(world, pos, EnumFacing.UP, material)) {
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

        if (material == Material.WATER && state.getMaterial() == Material.LAVA) {
            FluidMixingHandler.reactWaterIntoLava(world, pos, state);
            return;
        }

        if (material == Material.WATER
                && WaterloggedBlockRegistry.canWaterlog(state.getBlock())) {
            WaterloggedFluidHandler.tryWaterlog(
                    world, pos, state, FluidLevelMapper.SOURCE_METADATA);
            return;
        }

        if (material == Material.LAVA && state.getMaterial() == Material.WATER
                && direction == EnumFacing.DOWN) {
            FluidMixingHandler.reactDownward(world, pos, state);
            return;
        }

        int placedLevel = level;
        if (material == Material.WATER
                && getNewLegacyLevel(world, pos, material)
                == FluidLevelMapper.SOURCE_METADATA) {
            placedLevel = FluidLevelMapper.SOURCE_METADATA;
        }

        if (state.getMaterial() != Material.AIR) {
            if (material == Material.LAVA) {
                FluidMixingHandler.playExtinguishEffects(world, pos);
            } else {
                state.getBlock().dropBlockAsItem(world, pos, state, 0);
            }
        }

        IBlockState flowingState = placedLevel == FluidLevelMapper.SOURCE_METADATA
                && material == Material.WATER
                ? Blocks.WATER.getDefaultState()
                : getFlowingBlock(material).getDefaultState()
                        .withProperty(BlockLiquid.LEVEL, Integer.valueOf(placedLevel));
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
