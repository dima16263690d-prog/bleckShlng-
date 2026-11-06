package com.dima16263690d.bleckshlng.fluid.waterlogging;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.event.world.ChunkWatchEvent;
import com.dima16263690d.bleckshlng.fluid.network.FluidNetwork;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Server-thread scheduler for fluid states stored inside ordinary blocks.
 * Scheduled positions are only accessed when their chunk is already loaded.
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
        Iterator<Map.Entry<Long, Long>> iterator = tasks.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, Long> task = iterator.next();
            if (task.getValue().longValue() > now) {
                continue;
            }

            BlockPos pos = BlockPos.fromLong(task.getKey().longValue());
            if (!event.world.isBlockLoaded(pos)) {
                task.setValue(Long.valueOf(now + 20L));
                continue;
            }

            iterator.remove();
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
        if (data == null) {
            return;
        }

        BlockPos changed = event.getPos();
        scheduleIfWaterlogged(data, world, changed);
        for (net.minecraft.util.EnumFacing side : net.minecraft.util.EnumFacing.values()) {
            scheduleIfWaterlogged(data, world, changed.offset(side));
        }
    }

    private static void scheduleIfWaterlogged(ChunkFluidData data, World world, BlockPos pos) {
        if (world.isBlockLoaded(pos) && data.getWater(pos) != null) {
            schedule(world, pos, 1);
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        World world = event.getWorld();
        if (world != null && !world.isRemote) {
            ChunkFluidData data = ChunkFluidData.get(world);
            if (data != null && data.remove(event.getPos())) {
                FluidNetwork.removeState(world, event.getPos());
            }

            Map<Long, Long> tasks = PENDING.get(world);
            if (tasks != null) {
                tasks.remove(Long.valueOf(event.getPos().toLong()));
            }
        }
    }
}
