package com.dima16263690d.bleckshlng.fluid.waterlogging;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraft.block.state.IBlockState;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.dima16263690d.bleckshlng.fluid.state.FluidStateMapper;

/**
 * Persistent server-thread scheduler for native and waterlogged fluid states.
 *
 * Due times live in ChunkFluidData (WorldSavedData), not in a transient map.
 * Unloaded positions retain their due time and execute as soon as the chunk
 * becomes available again.
 */
@Mod.EventBusSubscriber(modid = "bleckshlng")
public final class FluidTickScheduler {
    private static final Logger LOGGER =
            LogManager.getLogger("BleckShlng/FluidTickScheduler");

    private FluidTickScheduler() {
    }

    public static void schedule(World world, BlockPos pos, int delay) {
        if (world == null || world.isRemote || pos == null) {
            return;
        }

        ChunkFluidData data = ChunkFluidData.get(world);
        if (data == null) {
            return;
        }

        long due = world.getTotalWorldTime() + Math.max(1, delay);
        data.scheduleFluidTick(pos, due);
    }

    public static void cancel(World world, BlockPos pos) {
        ChunkFluidData data = ChunkFluidData.getExisting(world);
        if (data != null) {
            data.removeScheduledFluidTick(pos);
        }
    }

    @SubscribeEvent
    public static void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.world.isRemote) {
            return;
        }

        ChunkFluidData data = ChunkFluidData.getExisting(event.world);
        if (data == null) {
            return;
        }

        long now = event.world.getTotalWorldTime();
        List<BlockPos> ready = new ArrayList<BlockPos>();
        for (BlockPos pos : data.getDueScheduledFluidTicks(now)) {
            // Never force-load a chunk for a scheduled fluid update. Keep the
            // entry persisted; the next world tick after chunk load will retry.
            if (!event.world.isBlockLoaded(pos)) {
                continue;
            }
            if (data.removeScheduledFluidTick(pos)) {
                ready.add(pos);
            }
        }

        // Execute after the due-list pass: fluid updates can schedule new
        // positions and must not mutate the map being inspected above.
        for (BlockPos pos : ready) {
            try {
                WaterloggedFluidHandler.tick(event.world, pos);
            } catch (Throwable error) {
                LOGGER.error("Fluid tick failed at {}; the update will be retried",
                        pos, error);
                try {
                    IBlockState state = event.world.getBlockState(pos);
                    if (FluidStateMapper.fromWorld(event.world, pos, state) != null) {
                        schedule(event.world, pos,
                                WaterloggedFluidHandler.INITIAL_TICK_DELAY);
                    }
                } catch (Throwable retryError) {
                    LOGGER.error("Could not restore failed fluid tick at {}", pos, retryError);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        World world = event.getWorld();
        if (world == null || world.isRemote) {
            return;
        }

        ChunkFluidData data = ChunkFluidData.getExisting(world);
        if (data == null) {
            return;
        }

        // Migration for old sidecar saves created before fluid ticks were
        // persisted. Existing due times are never overwritten.
        for (BlockPos pos : data.getPositionsInChunk(
                event.getChunk().x, event.getChunk().z)) {
            if (data.getScheduledFluidTick(pos) == null) {
                schedule(world, pos, WaterloggedFluidHandler.INITIAL_TICK_DELAY);
            }
        }
    }

    /**
     * Fallback for player placement into an existing water block. The coremod
     * World#setBlockState hook handles all world mutations; this Forge event
     * also covers normal ItemBlock placement if another transformer changes
     * the World method before our hook is applied.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockPlace(BlockEvent.PlaceEvent event) {
        World world = event.getWorld();
        if (world == null || world.isRemote) {
            return;
        }

        BlockPos pos = event.getPos();
        if (pos == null || !world.isBlockLoaded(pos)) {
            return;
        }

        net.minecraft.block.state.IBlockState placedState = world.getBlockState(pos);
        if (!com.dima16263690d.bleckshlng.fluid.geometry.WaterloggedBlockRegistry
                .canWaterlog(placedState.getBlock())) {
            return;
        }

        net.minecraftforge.common.util.BlockSnapshot snapshot = event.getBlockSnapshot();
        if (snapshot == null) {
            return;
        }

        net.minecraft.block.state.IBlockState replacedState = snapshot.getReplacedBlock();
        if (replacedState != null
                && replacedState.getMaterial() == net.minecraft.block.material.Material.WATER) {
            WaterloggedFluidHandler.tryWaterlog(world, pos, placedState, 0);
        }
    }

    @SubscribeEvent
    public static void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
        World world = event.getWorld();
        if (world == null || world.isRemote) {
            return;
        }

        if (world.provider.doesWaterVaporize()) {
            return;
        }
        ChunkFluidData data = ChunkFluidData.getExisting(world);
        BlockPos changed = event.getPos();
        scheduleIfWaterlogged(data, world, changed);
        for (EnumFacing side : EnumFacing.values()) {
            scheduleIfWaterlogged(data, world, changed.offset(side));
        }
    }

    private static void scheduleIfWaterlogged(ChunkFluidData data, World world,
                                              BlockPos pos) {
        if (!world.isBlockLoaded(pos)) {
            return;
        }

        boolean hasSidecar = data != null && data.getWater(pos) != null;
        if (hasSidecar) {
            schedule(world, pos, WaterloggedFluidHandler.INITIAL_TICK_DELAY);
            return;
        }

        // A backported or third-party block may keep WATERLOGGED directly in
        // its block state instead of using the sidecar NBT storage.
        net.minecraft.block.state.IBlockState state = world.getBlockState(pos);
        if (state.getBlock() instanceof IFluidWaterloggable
                && ((IFluidWaterloggable) state.getBlock()).isWaterlogged(state)) {
            schedule(world, pos, WaterloggedFluidHandler.INITIAL_TICK_DELAY);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        World world = event.getWorld();
        if (world == null || world.isRemote) {
            return;
        }

        // A waterlogged block releases its water source when it is removed.
        // Keep the sidecar until the delayed tick observes the replacement.
        BlockPos pos = event.getPos();
        ChunkFluidData data = ChunkFluidData.getExisting(world);
        boolean hasSidecar = data != null && data.getWater(pos) != null;
        net.minecraft.block.state.IBlockState state = world.getBlockState(pos);
        boolean hasAdapterWater = state.getBlock() instanceof IFluidWaterloggable
                && ((IFluidWaterloggable) state.getBlock()).isWaterlogged(state);
        if (hasSidecar || hasAdapterWater) {
            schedule(world, pos, 1);
        }
    }
}
