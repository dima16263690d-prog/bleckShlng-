package com.dima16263690d.bleckshlng.coremod;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Random;

import net.minecraft.block.BlockDynamicLiquid;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.dima16263690d.bleckshlng.fluid.engine.FluidFlowEngine;
import com.dima16263690d.bleckshlng.fluid.state.FluidProperties;
import com.dima16263690d.bleckshlng.fluid.state.FluidStateBridge;
import com.dima16263690d.bleckshlng.fluid.physics.FluidFlowVector;
import com.dima16263690d.bleckshlng.fluid.physics.FluidEntityHandler;

/**
 * Runtime bridge called by the guarded coremod transformers.
 * Returning false/null leaves the original vanilla implementation active.
 */
public final class FluidCoreHooks {
    private static final Logger LOGGER = LogManager.getLogger("BleckShlng/Fluid");
    private static final ThreadLocal<Deque<Boolean>> WORLD_BLOCK_SET_CAPTURE =
            new ThreadLocal<Deque<Boolean>>() {
                @Override
                protected Deque<Boolean> initialValue() {
                    return new ArrayDeque<Boolean>();
                }
            };

    private FluidCoreHooks() {
    }

    /**
     * Records whether this invocation replaces source water with a block
     * supported by the 1.13/1.18 waterlogging rules.
     *
     * A ThreadLocal stack is used instead of an injected bytecode local so
     * existing StackMapTable frames remain untouched. It also supports nested
     * World.setBlockState calls.
     */
    public static void beginWorldBlockSet(World world, BlockPos pos,
                                          IBlockState newState) {
        // Collision boxes are cached on both logical sides. Any block-state
        // replacement invalidates this position before the new shape is read.
        if (world != null && pos != null) {
            com.dima16263690d.bleckshlng.fluid.geometry.FluidShapeCalculator
                    .invalidate(world, pos);
        }

        boolean captured = false;
        if (world != null && !world.isRemote && pos != null && newState != null
                && world.isBlockLoaded(pos)) {
            try {
                if (com.dima16263690d.bleckshlng.fluid.geometry.WaterloggedBlockRegistry
                        .canWaterlog(newState.getBlock())) {
                    IBlockState oldState = world.getBlockState(pos);
                    if (oldState != null && oldState.getMaterial() == Material.WATER) {
                        captured = true;
                    } else if (oldState != null) {
                        // The previous host may already contain water in the
                        // persistent sidecar or in its own WATERLOGGED state.
                        FluidStateBridge existingFluid =
                                com.dima16263690d.bleckshlng.fluid.state.FluidStateMapper
                                        .fromWorld(world, pos, oldState);
                        captured = existingFluid != null
                                && existingFluid.getKind()
                                == FluidStateBridge.FluidKind.WATER;

                        // A native adapter changing WATERLOGGED=true to false
                        // is an intentional pickup/state transition, not a
                        // placement into an existing water source.
                        if (captured && oldState.getBlock() == newState.getBlock()
                                && oldState.getBlock() instanceof
                                com.dima16263690d.bleckshlng.fluid.waterlogging.IFluidWaterloggable) {
                            com.dima16263690d.bleckshlng.fluid.waterlogging.IFluidWaterloggable adapter =
                                    (com.dima16263690d.bleckshlng.fluid.waterlogging.IFluidWaterloggable)
                                            oldState.getBlock();
                            if (adapter.isWaterlogged(oldState)
                                    && !adapter.isWaterlogged(newState)) {
                                captured = false;
                            }
                        }
                    }
                }
            } catch (Throwable error) {
                LOGGER.error("Could not inspect water before placing waterloggable block at {}", pos, error);
            }
        }

        WORLD_BLOCK_SET_CAPTURE.get().push(Boolean.valueOf(captured));
    }

    /**
     * Completes the capture after World.setBlockState. The original boolean
     * return value is preserved by the transformer.
     */
    public static void onWorldBlockSet(boolean setSucceeded, World world,
                                       BlockPos pos, IBlockState placedState) {
        Deque<Boolean> captures = WORLD_BLOCK_SET_CAPTURE.get();
        if (captures.isEmpty()) {
            WORLD_BLOCK_SET_CAPTURE.remove();
            return;
        }
        boolean capturedWater = captures.pop().booleanValue();
        if (captures.isEmpty()) {
            WORLD_BLOCK_SET_CAPTURE.remove();
        }

        if (!setSucceeded || !capturedWater || world == null || world.isRemote
                || pos == null || placedState == null || !world.isBlockLoaded(pos)) {
            return;
        }

        try {
            IBlockState actualState = world.getBlockState(pos);
            if (actualState == null || actualState.getBlock() != placedState.getBlock()
                    || !com.dima16263690d.bleckshlng.fluid.geometry.WaterloggedBlockRegistry
                    .canWaterlog(actualState.getBlock())) {
                return;
            }

            if (actualState.getBlock() instanceof
                    com.dima16263690d.bleckshlng.fluid.waterlogging.IFluidWaterloggable) {
                com.dima16263690d.bleckshlng.fluid.waterlogging.WaterloggedFluidHandler
                        .tryWaterlog(world, pos, actualState, 0);
                return;
            }

            com.dima16263690d.bleckshlng.fluid.waterlogging.ChunkFluidData data =
                    com.dima16263690d.bleckshlng.fluid.waterlogging.ChunkFluidData.get(world);
            if (data == null) {
                return;
            }

            FluidStateBridge water = FluidStateBridge.source(FluidStateBridge.FluidKind.WATER);
            if (data.putWater(pos, water)) {
                com.dima16263690d.bleckshlng.fluid.network.FluidNetwork
                        .syncState(world, pos, water);
                com.dima16263690d.bleckshlng.fluid.waterlogging.FluidTickScheduler
                        .schedule(world, pos, 5);
                com.dima16263690d.bleckshlng.fluid.geometry.FluidShapeCalculator
                        .invalidate(world, pos);
                world.notifyNeighborsOfStateChange(pos, actualState.getBlock(), false);
            }
        } catch (Throwable error) {
            LOGGER.error("Could not transfer source water into placed block at {}", pos, error);
        }
    }

    /** Removes the pending capture when the intercepted method throws. */
    public static void discardWorldBlockSet() {
        Deque<Boolean> captures = WORLD_BLOCK_SET_CAPTURE.get();
        if (!captures.isEmpty()) {
            captures.pop();
        }
        if (captures.isEmpty()) {
            WORLD_BLOCK_SET_CAPTURE.remove();
        }
    }

    public static boolean tryUpdateDynamicLiquid(World world, BlockPos pos,
                                                 IBlockState state, Random random) {
        // Do not take ownership of client ticks, modded liquids, or unrelated
        // BlockDynamicLiquid subclasses. Their original vanilla/Forge code runs.
        if (world == null || world.isRemote || pos == null || state == null
                || random == null || !(state.getBlock() instanceof BlockDynamicLiquid)
                || (state.getBlock() != Blocks.FLOWING_WATER
                && state.getBlock() != Blocks.FLOWING_LAVA)) {
            return false;
        }

        Material material = state.getMaterial();
        if (material != Material.WATER && material != Material.LAVA) {
            return false;
        }

        try {
            // Lava fire propagation remains owned by vanilla
            // BlockStaticLiquid random ticks. Scheduled flow updates must not
            // synthesize extra fire ticks or make fire frequency depend on
            // the fluid's spread delay.
            FluidFlowEngine.tick(world, pos, state, random);
            return true;
        } catch (Throwable error) {
            LOGGER.error("Fluid engine failed at {}; suppressing duplicate vanilla update",
                    pos, error);

            // The engine may have already changed neighboring blocks before
            // an unexpected adapter/mod exception occurred. Running the
            // vanilla tick in the same call could spread twice or react twice.
            // Retry only if this exact vanilla fluid block still occupies pos.
            try {
                if (world.isBlockLoaded(pos)
                        && world.getBlockState(pos).getBlock() == state.getBlock()) {
                    world.scheduleUpdate(pos, state.getBlock(),
                            FluidProperties.tickRate(world, material));
                }
            } catch (Throwable retryError) {
                LOGGER.error("Could not reschedule failed fluid update at {}", pos, retryError);
            }
            return true;
        }
    }

    /**
     * Extends BlockFarmland's vanilla 9x2x9 water search to water stored
     * inside legacy blocks. Vanilla water is still handled by its own check.
     */
    public static boolean applyWaterloggedFarmlandWater(boolean vanillaResult,
                                                        World world, BlockPos farmlandPos) {
        if (vanillaResult || world == null || world.isRemote || farmlandPos == null) {
            return vanillaResult;
        }

        try {
            for (int x = -4; x <= 4; x++) {
                for (int y = 0; y <= 1; y++) {
                    for (int z = -4; z <= 4; z++) {
                        BlockPos waterPos = farmlandPos.add(x, y, z);
                        if (!world.isBlockLoaded(waterPos)) {
                            continue;
                        }

                        IBlockState waterBlock = world.getBlockState(waterPos);
                        com.dima16263690d.bleckshlng.fluid.state.FluidStateBridge fluid =
                                com.dima16263690d.bleckshlng.fluid.state.FluidStateMapper
                                        .fromWorld(world, waterPos, waterBlock);
                        if (fluid != null
                                && fluid.getKind()
                                == com.dima16263690d.bleckshlng.fluid.state.FluidStateBridge.FluidKind.WATER) {
                            return true;
                        }
                    }
                }
            }
            return false;
        } catch (Throwable error) {
            LOGGER.error("Waterlogged farmland hydration check failed at {}", farmlandPos, error);
            return vanillaResult;
        }
    }

    /**
     * BlockReed.canPlaceBlockAt normally requires water beside its soil.
     * In 1.13.2 a waterlogged block is also a water source for this check.
     */
    public static boolean applyWaterloggedReedWater(boolean vanillaResult,
                                                    World world, BlockPos reedPos) {
        if (vanillaResult || world == null || world.isRemote || reedPos == null) {
            return vanillaResult;
        }

        try {
            BlockPos soilPos = reedPos.down();
            if (!world.isBlockLoaded(soilPos)) {
                return false;
            }

            net.minecraft.block.Block soil = world.getBlockState(soilPos).getBlock();
            if (soil != Blocks.GRASS && soil != Blocks.DIRT
                    && soil != Blocks.SAND && soil != Blocks.REEDS) {
                return false;
            }

            for (net.minecraft.util.EnumFacing side : net.minecraft.util.EnumFacing.Plane.HORIZONTAL) {
                BlockPos waterPos = soilPos.offset(side);
                if (!world.isBlockLoaded(waterPos)) {
                    continue;
                }
                IBlockState waterBlock = world.getBlockState(waterPos);
                com.dima16263690d.bleckshlng.fluid.state.FluidStateBridge fluid =
                        com.dima16263690d.bleckshlng.fluid.state.FluidStateMapper
                                .fromWorld(world, waterPos, waterBlock);
                if (fluid != null
                        && fluid.getKind()
                        == com.dima16263690d.bleckshlng.fluid.state.FluidStateBridge.FluidKind.WATER) {
                    return true;
                }
            }
            return false;
        } catch (Throwable error) {
            LOGGER.error("Waterlogged sugar cane placement check failed at {}", reedPos, error);
            return vanillaResult;
        }
    }

    /**
     * A lily pad can also stand on a waterlogged source, as on a regular
     * source-water block in the 1.13 water model.
     */
    public static boolean applyWaterloggedLilyPadStay(boolean vanillaResult,
                                                      World world, BlockPos lilyPos,
                                                      IBlockState lilyState) {
        if (vanillaResult || world == null || world.isRemote || lilyPos == null) {
            return vanillaResult;
        }

        try {
            BlockPos waterPos = lilyPos.down();
            if (!world.isBlockLoaded(waterPos)) {
                return false;
            }
            IBlockState waterBlock = world.getBlockState(waterPos);
            com.dima16263690d.bleckshlng.fluid.state.FluidStateBridge fluid =
                    com.dima16263690d.bleckshlng.fluid.state.FluidStateMapper
                            .fromWorld(world, waterPos, waterBlock);
            return fluid != null
                    && fluid.getKind()
                    == com.dima16263690d.bleckshlng.fluid.state.FluidStateBridge.FluidKind.WATER
                    && fluid.isSource();
        } catch (Throwable error) {
            LOGGER.error("Waterlogged lily pad support check failed at {}", lilyPos, error);
            return vanillaResult;
        }
    }

    /**
     * Applies the 1.13.2 sponge rule around waterlogged hosts.
     * Ordinary water blocks are absorbed using the bounded vanilla search;
     * water stored inside a waterlogged block is deliberately left intact.
     */
    public static boolean tryAbsorbSpongeWater(World world, BlockPos spongePos) {
        try {
            return com.dima16263690d.bleckshlng.fluid.waterlogging.WaterloggedSpongeHandler
                    .absorb(world, spongePos);
        } catch (Throwable error) {
            LOGGER.error("Waterlogged sponge absorption failed at {}", spongePos, error);
            return false;
        }
    }


    /**
     * EntityFishHook checks Material.WATER and BlockLiquid.LEVEL directly
     * instead of using Entity's generic water checks. Expose a waterlogged
     * source as a temporary vanilla water state for the bobber's local test.
     * The world block itself is never changed.
     */
    public static IBlockState getFishHookFluidState(World world, BlockPos pos,
                                                    IBlockState originalState) {
        if (world == null || pos == null || originalState == null) {
            return originalState;
        }

        try {
            FluidStateBridge fluid = com.dima16263690d.bleckshlng.fluid.state.FluidStateMapper
                    .fromWorld(world, pos, originalState);
            if (fluid == null
                    || fluid.getKind() != FluidStateBridge.FluidKind.WATER) {
                return originalState;
            }

            int metadata = fluid.toLegacyMetadata();
            return Blocks.WATER.getDefaultState()
                    .withProperty(BlockLiquid.LEVEL, Integer.valueOf(metadata));
        } catch (Throwable error) {
            LOGGER.error("Fishing bobber waterlogged-state check failed at {}", pos, error);
            return originalState;
        }
    }

    /**
     * BlockBush.canPlaceBlockAt checks canSustainBush before BlockLilyPad's
     * canBlockStay override is reached. Keep this exception restricted to
     * lily pads so other plants retain their vanilla placement rules.
     */
    public static boolean applyWaterloggedLilyPadPlacement(
            boolean vanillaResult, net.minecraft.block.BlockBush block,
            World world, BlockPos lilyPos) {
        if (vanillaResult) {
            return true;
        }
        if (!(block instanceof net.minecraft.block.BlockLilyPad)
                || world == null || world.isRemote || lilyPos == null) {
            return false;
        }

        try {
            BlockPos waterPos = lilyPos.down();
            if (!world.isBlockLoaded(waterPos)) {
                return false;
            }
            IBlockState waterBlock = world.getBlockState(waterPos);
            FluidStateBridge fluid = com.dima16263690d.bleckshlng.fluid.state.FluidStateMapper
                    .fromWorld(world, waterPos, waterBlock);
            return fluid != null
                    && fluid.getKind() == FluidStateBridge.FluidKind.WATER
                    && fluid.isSource();
        } catch (Throwable error) {
            LOGGER.error("Waterlogged lily pad placement check failed at {}", lilyPos, error);
            return false;
        }
    }

    /**
     * Returns null when the block is not one of the vanilla liquids handled
     * here. The transformer then executes the original BlockLiquid method.
     */
    public static boolean applyWaterloggedMovement(boolean vanillaResult, Entity entity) {
        try {
            // Do not skip the sidecar scan when the entity is also touching
            // ordinary water. Both fluid contributions must be applied.
            boolean waterloggedResult =
                    FluidEntityHandler.updateWaterloggedMovement(entity);
            return vanillaResult || waterloggedResult;
        } catch (Throwable error) {
            LOGGER.error("Waterlogged entity movement check failed; keeping vanilla result", error);
            return vanillaResult;
        }
    }

    public static boolean applyWaterloggedInWater(boolean vanillaResult, Entity entity) {
        if (vanillaResult) {
            return true;
        }
        try {
            return FluidEntityHandler.isInWaterloggedFluid(entity);
        } catch (Throwable error) {
            LOGGER.error("Waterlogged isInWater check failed; keeping vanilla result", error);
            return false;
        }
    }

    public static boolean applyWaterloggedMaterialCheck(boolean vanillaResult,
                                                        Entity entity, Material material) {
        if (vanillaResult || material != Material.WATER) {
            return vanillaResult;
        }
        try {
            return FluidEntityHandler.isEyeInWaterloggedFluid(entity);
        } catch (Throwable error) {
            LOGGER.error("Waterlogged eye-fluid check failed; keeping vanilla result", error);
            return false;
        }
    }

    public static Vec3d tryModifyAcceleration(BlockLiquid block, World world,
                                               BlockPos pos, Entity entity,
                                               Vec3d originalMotion) {
        if (block == null || world == null || pos == null || originalMotion == null) {
            return null;
        }

        if (block != Blocks.WATER && block != Blocks.FLOWING_WATER
                && block != Blocks.LAVA && block != Blocks.FLOWING_LAVA) {
            return null;
        }

        IBlockState state = world.getBlockState(pos);
        if (state.getBlock() != block) {
            return null;
        }

        try {
            return originalMotion.add(FluidFlowVector.calculate(world, pos, state));
        } catch (Throwable error) {
            LOGGER.error("Fluid acceleration calculation failed at {}; using vanilla current",
                    pos, error);
            return null;
        }
    }
}
