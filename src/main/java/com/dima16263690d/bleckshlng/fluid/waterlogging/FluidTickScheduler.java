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
import net.minecraftforge.event.world.ChunkWatchEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import com.dima16263690d.bleckshlng.fluid.network.FluidNetwork;

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

        Map<Long, Long> tasks = PENDING.get(world);
        if (tasks == null) {
            tasks = new HashMap<Long, Long>();
            PENDING.put(world, tasks);
        }

        long due = world.getTotalWorldTime() + Math.max(1, delay);
        Long key = Long.valueOf(pos.toLong());
        Long oldDue = tasks.get(key);
        if (oldDue == null || due < oldDue.longValue()) {
            tasks.put(key, Long.valueOf(due));
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
                // A chunk unload removes its tasks; this is a defensive fallback
                // for a task that became unloaded during the current tick.
                task.setValue(Long.valueOf(now + 20L));
                continue;
            }

            iterator.remove();
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

        for (BlockPos pos : data.getPositionsInChunk(event.getChunk().x, event.getChunk().z)) {
            schedule(world, pos, 1);
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
        Iterator<Long> iterator = tasks.keySet().iterator();
        while (iterator.hasNext()) {
            BlockPos pos = BlockPos.fromLong(iterator.next().longValue());
            if ((pos.getX() >> 4) == chunkX && (pos.getZ() >> 4) == chunkZ) {
                iterator.remove();
            }
        }
    }

    @SubscribeEvent
    public static void onWorldUnload(WorldEvent.Unload event) {
        World world = event.getWorld();
        if (world != null && !world.isRemote) {
            PENDING.remove(world);
        }
    }

    @SubscribeEvent
    public static void onChunkWatch(ChunkWatchEvent.Watch event) {
        net.minecraft.entity.player.EntityPlayerMP player = event.getPlayer();
        net.minecraft.world.chunk.Chunk chunk = event.getChunkInstance();
        World world = chunk == null ? null : chunk.getWorld();
        if (chunk != null && world != null && player != null && !world.isRemote
                && player.world.provider.getDimension() == world.provider.getDimension()) {
            FluidNetwork.syncChunkToPlayer(world, chunk, player);
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
            schedule(world, pos, 1);
            return;
        }

        // A backported or third-party block may keep WATERLOGGED directly in
        // its block state instead of using the sidecar NBT storage.
        net.minecraft.block.state.IBlockState state = world.getBlockState(pos);
        if (state.getBlock() instanceof IFluidWaterloggable
                && ((IFluidWaterloggable) state.getBlock()).isWaterlogged(state)) {
            schedule(world, pos, 1);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        World world = event.getWorld();
        if (world == null || world.isRemote) {
            return;
        }

        // LOWEST priority with receiveCanceled=false avoids deleting the fluid
        // sidecar when another mod cancels the player's block-break event.
        BlockPos pos = event.getPos();
        ChunkFluidData data = ChunkFluidData.get(world);
        boolean removedSidecar = data != null && data.remove(pos);
        net.minecraft.block.state.IBlockState state = world.getBlockState(pos);
        boolean removedAdapterState = state.getBlock() instanceof IFluidWaterloggable
                && ((IFluidWaterloggable) state.getBlock()).isWaterlogged(state);
        if (removedSidecar || removedAdapterState) {
            FluidNetwork.removeState(world, pos);
        }

        Map<Long, Long> tasks = PENDING.get(world);
        if (tasks != null) {
            tasks.remove(Long.valueOf(event.getPos().toLong()));
        }
    }
}
