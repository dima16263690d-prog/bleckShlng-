package com.dima16263690d.bleckshlng.fluid.waterlogging;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;


/**
 * Server-thread scheduler for fluid states stored inside ordinary blocks.
 * Tasks are only executed while their chunk is already loaded.
 */
@Mod.EventBusSubscriber(modid = "bleckshlng")
public final class FluidTickScheduler {
    private static final Map<World, Map<Long, Long>> PENDING =
            new WeakHashMap<World, Map<Long, Long>>();

    private FluidTickScheduler() {
    }

    public static void schedule(World world, BlockPos pos, int delay) {
        if (world == null || world.isRemote || pos == null) {
            return;
        }

        long due = world.getTotalWorldTime() + Math.max(1, delay);
        ChunkFluidData data = ChunkFluidData.get(world);
        if (data != null) {
            due = data.putScheduledTick(pos, due);
        }
        scheduleAt(world, pos, due);
    }

    /** Restores a saved absolute deadline without converting it to a new delay. */
    private static void scheduleAt(World world, BlockPos pos, long dueTime) {
        if (world == null || world.isRemote || pos == null) {
            return;
        }
        Map<Long, Long> tasks = PENDING.get(world);
        if (tasks == null) {
            tasks = new HashMap<Long, Long>();
            PENDING.put(world, tasks);
        }
        scheduleAt(tasks, pos, dueTime);
    }

    static void scheduleAt(Map<Long, Long> tasks, BlockPos pos, long dueTime) {
        if (tasks == null || pos == null) return;
        Long key = Long.valueOf(pos.toLong());
        Long oldDue = tasks.get(key);
        if (oldDue == null || dueTime < oldDue.longValue()) {
            tasks.put(key, Long.valueOf(dueTime));
        }
    }

    static void restoreChunkDeadlines(
            Map<Long, Long> tasks, ChunkFluidData data, int chunkX, int chunkZ) {
        if (tasks == null || data == null) return;
        for (BlockPos pos : data.getScheduledTickPositionsInChunk(chunkX, chunkZ)) {
            Long due = data.getScheduledTick(pos);
            if (due != null) scheduleAt(tasks, pos, due.longValue());
        }
    }

    static void removeChunkTasks(Map<Long, Long> tasks, int chunkX, int chunkZ) {
        if (tasks == null || tasks.isEmpty()) return;
        Iterator<Long> iterator = tasks.keySet().iterator();
        while (iterator.hasNext()) {
            BlockPos pos = BlockPos.fromLong(iterator.next().longValue());
            if ((pos.getX() >> 4) == chunkX && (pos.getZ() >> 4) == chunkZ) {
                iterator.remove();
            }
        }
    }

    @SubscribeEvent
    public static void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.world.isRemote) {
            return;
        }

        Map<Long, Long> tasks = PENDING.get(event.world);
        if (tasks == null || tasks.isEmpty()) {
            return;
        }

        long now = event.world.getTotalWorldTime();
        List<BlockPos> duePositions = new ArrayList<BlockPos>();
        Iterator<Map.Entry<Long, Long>> iterator = tasks.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, Long> task = iterator.next();
            if (task.getValue().longValue() > now) {
                continue;
            }

            BlockPos pos = BlockPos.fromLong(task.getKey().longValue());
            if (!event.world.isBlockLoaded(pos)) {
                // Keep the persisted deadline unchanged. The chunk-load event
                // will restore this task with its original absolute due time.
                iterator.remove();
                continue;
            }

            iterator.remove();
            ChunkFluidData data = ChunkFluidData.get(event.world);
            if (data != null) {
                data.removeScheduledTick(pos);
            }
            duePositions.add(pos);
        }

        // Run callbacks only after iteration. Fluid updates can schedule more
        // waterlogged positions, and mutating the same HashMap from inside the
        // iterator would otherwise throw ConcurrentModificationException.
        for (BlockPos pos : duePositions) {
            WaterloggedFluidHandler.tick(event.world, pos);
        }
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        World world = event.getWorld();
        if (world == null || world.isRemote) {
            return;
        }

        ChunkFluidData data = ChunkFluidData.get(world);
        if (data == null) {
            return;
        }

        int chunkX = event.getChunk().x;
        int chunkZ = event.getChunk().z;

        // Restore absolute deadlines before the legacy sidecar fallback.
        // Overdue work is kept overdue and runs on the next END tick.
        Map<Long, Long> tasks = PENDING.get(world);
        if (tasks == null) {
            tasks = new HashMap<Long, Long>();
            PENDING.put(world, tasks);
        }
        restoreChunkDeadlines(tasks, data, chunkX, chunkZ);

        // Pre-deadline saves have water states but no explicit due time.
        for (BlockPos pos : data.getPositionsInChunk(chunkX, chunkZ)) {
            if (data.getScheduledTick(pos) == null) {
                schedule(world, pos, 5);
            }
        }
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        World world = event.getWorld();
        if (world == null || world.isRemote) {
            return;
        }

        Map<Long, Long> tasks = PENDING.get(world);
        if (tasks == null || tasks.isEmpty()) {
            return;
        }

        int chunkX = event.getChunk().x;
        int chunkZ = event.getChunk().z;
        removeChunkTasks(tasks, chunkX, chunkZ);
    }

    @SubscribeEvent
    public static void onWorldUnload(WorldEvent.Unload event) {
        World world = event.getWorld();
        if (world != null && !world.isRemote) {
            PENDING.remove(world);
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

        ChunkFluidData data = ChunkFluidData.get(world);
        BlockPos changed = event.getPos();
        scheduleIfWaterlogged(data, world, changed);
        for (EnumFacing side : EnumFacing.values()) {
            scheduleIfWaterlogged(data, world, changed.offset(side));
        }
    }

    private static void scheduleIfWaterlogged(ChunkFluidData data, World world, BlockPos pos) {
        if (!world.isBlockLoaded(pos)) {
            return;
        }

        boolean hasSidecar = data != null && data.getWater(pos) != null;
        if (hasSidecar) {
            schedule(world, pos, 5);
            return;
        }

        // A backported or third-party block may keep WATERLOGGED directly in
        // its block state instead of using the sidecar NBT storage.
        net.minecraft.block.state.IBlockState state = world.getBlockState(pos);
        if (state.getBlock() instanceof IFluidWaterloggable
                && ((IFluidWaterloggable) state.getBlock()).isWaterlogged(state)) {
            schedule(world, pos, 5);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        World world = event.getWorld();
        if (world == null || world.isRemote) {
            return;
        }

        // A waterlogged block releases its water source when it is removed.
        // Do not delete the sidecar or its deadline here: BreakEvent fires
        // before the block is replaced with air. The saved tick releases the
        // source after the block has actually been removed.
        BlockPos pos = event.getPos();
        ChunkFluidData data = ChunkFluidData.get(world);
        boolean hasSidecar = data != null && data.getWater(pos) != null;
        net.minecraft.block.state.IBlockState state = world.getBlockState(pos);
        boolean hasAdapterWater = state.getBlock() instanceof IFluidWaterloggable
                && ((IFluidWaterloggable) state.getBlock()).isWaterlogged(state);
        if (hasSidecar || hasAdapterWater) {
            schedule(world, pos, 1);
        }
    }
}
