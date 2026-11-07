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
import com.dima16263690d.bleckshlng.fluid.state.FluidProperties;
import com.dima16263690d.bleckshlng.fluid.state.FluidStateBridge;
import com.dima16263690d.bleckshlng.fluid.state.FluidStateMapper;
import com.dima16263690d.bleckshlng.fluid.waterlogging.WaterloggedFluidHandler;

/**
 * Java Edition 1.13.2 liquid behavior adapted to the existing 1.12.2
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
                    && newLevel < 8 && newLevel > level && random.nextInt(4) != 0) {
                tickDelay *= 4;
            }

            if (newLevel == level) {
                placeStaticBlock(world, pos, material, state);
            } else if (newLevel == NO_FLOW) {
                // A fluid that has lost all incoming neighbors disappears and
                // must not perform one final spread from its old position.
                world.setBlockToAir(pos);
                return;
            } else if (material == Material.WATER
                    && newLevel == FluidLevelMapper.SOURCE_METADATA) {
                // Update Aquatic 18w16a: create the source immediately instead
                // of placing a temporary flowing block that becomes a source
                // on a later liquid tick.
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

        int maximumNeighborAmount = 0;
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

            int neighborAmount = FluidLevelMapper.amountFromMetadata(neighborLevel);
            maximumNeighborAmount = Math.max(maximumNeighborAmount, neighborAmount);
        }

        // FlowingFluid chooses the strongest horizontal neighbour, then
        // applies this fluid's drop-off to calculate the target's own state.
        // The custom lava tail (amount 2 -> 1) remains in FluidProperties.
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
                boolean solidSupport = belowState.getMaterial().isSolid();
                boolean closedBottomFace = FluidShapeCalculator.isFaceFullyCovered(
                        world, pos, world.getBlockState(pos), EnumFacing.DOWN);
                boolean waterSourceBelow = getDepth(
                        world, belowPos, Material.WATER)
                        == FluidLevelMapper.SOURCE_METADATA
                        && FluidShapeCalculator.canPassThroughWall(
                        world, belowPos, EnumFacing.UP);

                // Keep the Update Aquatic/Forge support rules, including a
                // fully closed lower face and a connected source underneath.
                if (solidSupport || closedBottomFace || waterSourceBelow) {
                    newLevel = FluidLevelMapper.SOURCE_METADATA;
                }
            }
        }

        // FlowingFluid applies the falling override after source conversion.
        // A matching fluid above always produces a full falling state.
        BlockPos abovePos = pos.up();
        if (world.isBlockLoaded(abovePos)
                && FluidShapeCalculator.canPassThroughWall(world, pos, EnumFacing.UP)) {
            int aboveLevel = getDepth(world, abovePos, material);
            if (aboveLevel >= 0) {
                newLevel = 8;
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
        boolean openDownFace = FluidShapeCalculator.canPassThroughWall(
                world, pos, EnumFacing.DOWN);

        // Lava must recognize FluidState held inside a host block as water.
        // The host remains intact; reactDownward only converts ordinary
        // vanilla water to stone and emits the extinguish effect for both.
        if (material == Material.LAVA && openDownFace) {
            FluidStateBridge belowFluid = FluidStateMapper.fromWorld(
                    world, belowPos, belowState);
            if (belowFluid != null
                    && belowFluid.getKind() == FluidStateBridge.FluidKind.WATER) {
                FluidMixingHandler.reactDownward(world, belowPos, belowState);
                return;
            }
        }

        boolean canEnterBelow = openDownFace && canFlowInto(
                world, belowPos, belowState, material, EnumFacing.DOWN);

        if (canEnterBelow) {
            // FlowingFluid.getNewLiquid() yields full-strength falling water
            // whenever matching fluid occupies the block directly above.
            int downwardLevel = 8;
            tryFlowInto(world, belowPos, belowState, material,
                    downwardLevel, EnumFacing.DOWN);

            // Update Aquatic / FlowingFluid: a successful downward flow does
            // not always end the update. A block with at least three adjacent
            // source neighbors also spreads sideways in the same tick.
            if (sourceNeighborCount(world, pos, material) >= 3) {
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
            if (getDepth(world, neighborPos, material)
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

        // FlowingFluid only opens the side-spread search when the source has
        // enough amount left (falling flow uses a fixed amount of seven).
        int spreadAmount = level >= 8 ? 7
                : FluidProperties.nextHorizontalAmount(world, material,
                FluidLevelMapper.amountFromMetadata(level));
        if (spreadAmount <= 0) {
            return;
        }

        for (EnumFacing direction : directions) {
            BlockPos targetPos = pos.offset(direction);
            if (!world.isBlockLoaded(targetPos)
                    || !FluidShapeCalculator.canPassThroughWall(world, pos, direction)) {
                continue;
            }

            IBlockState targetState = world.getBlockState(targetPos);
            // FlowingFluid.getSpread() calculates the incoming FluidState for
            // each candidate independently; another source or a fluid above
            // can therefore change this target's level or make it a source.
            int targetLevel = getNewLegacyLevel(world, targetPos, material);
            if (targetLevel == NO_FLOW) {
                continue;
            }
            tryFlowInto(world, targetPos, targetState, material,
                    targetLevel, direction);
        }
    }

    /**
     * Adapts FlowingFluid.canSpreadTo/canHoldFluid to the 1.12.2 block and
     * metadata model, including waterlogging capacity and lava replacement.
     */
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

        // FlowingFluid.canHoldFluid delegates to LiquidBlockContainer.
        // Any non-empty water flow may fill an empty waterloggable host; the
        // host then exposes a full source state, as SimpleWaterloggedBlock does.
        if (material == Material.WATER
                && WaterloggedBlockRegistry.canWaterlog(state.getBlock())) {
            return BlockGeometryAdapter.canFluidEnter(
                    world, pos, state, material, direction);
        }

        // LavaFluid.canBeReplacedWith accepts water when the lava surface is
        // at least 4/9 of a block high. The reaction is handled before placing.
        if (material == Material.WATER && targetMaterial == Material.LAVA) {
            FluidStateBridge lava = FluidStateMapper.fromBlockState(state);
            return lava != null && lava.getOwnHeight() >= 4.0F / 9.0F;
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
