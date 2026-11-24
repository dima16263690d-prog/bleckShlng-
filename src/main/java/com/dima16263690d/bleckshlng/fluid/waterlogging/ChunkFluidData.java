package com.dima16263690d.bleckshlng.fluid.waterlogging;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraft.util.math.BlockPos;

import com.dima16263690d.bleckshlng.fluid.state.FluidStateBridge;
import com.dima16263690d.bleckshlng.fluid.state.FluidLevelMapper;

/**
 * Persistent per-dimension storage for water held by legacy blocks.
 *
 * 1.12.2 block metadata cannot be expanded safely: this data leaves the
 * original block registry and metadata untouched. It is stored in the
 * dimension's WorldSavedData file and does not force chunks to load.
 */
public class ChunkFluidData extends WorldSavedData {
    private static final String DATA_NAME = "bleckshlng_fluid_states";
    private final Map<Long, FluidStateBridge> waterStates =
            new HashMap<Long, FluidStateBridge>();
    private final Map<Long, Set<Long>> chunkIndex =
            new HashMap<Long, Set<Long>>();
    /** Due world times for native-fluid and waterlogged-fluid updates. */
    private final Map<Long, Long> scheduledFluidTicks =
            new HashMap<Long, Long>();
    /** Ordered secondary index for retrieving due ticks without sorting all entries. */
    private final TreeMap<Long, TreeSet<Long>> scheduledFluidTicksByTime =
            new TreeMap<Long, TreeSet<Long>>();

    public ChunkFluidData() {
        super(DATA_NAME);
    }

    public ChunkFluidData(String name) {
        super(name);
    }

    public static ChunkFluidData getExisting(World world) {
        if (world == null || world.isRemote) {
            return null;
        }
        MapStorage storage = world.getPerWorldStorage();
        if (storage == null) {
            return null;
        }
        return (ChunkFluidData) storage.getOrLoadData(
                ChunkFluidData.class, DATA_NAME);
    }

    public static ChunkFluidData get(World world) {
        ChunkFluidData data = getExisting(world);
        if (data != null || world == null || world.isRemote) {
            return data;
        }

        MapStorage storage = world.getPerWorldStorage();
        if (storage == null) {
            return null;
        }
        data = new ChunkFluidData();
        storage.setData(DATA_NAME, data);
        return data;
    }

    public FluidStateBridge getWater(BlockPos pos) {
        return pos == null ? null : waterStates.get(Long.valueOf(pos.toLong()));
    }

    /**
     * Persists the earliest scheduled update for this position.
     * A later neighbor notification must not postpone an already pending tick.
     */
    public boolean scheduleFluidTick(BlockPos pos, long dueWorldTime) {
        if (pos == null) {
            return false;
        }

        Long key = Long.valueOf(pos.toLong());
        Long oldDue = scheduledFluidTicks.get(key);
        if (oldDue != null && oldDue.longValue() <= dueWorldTime) {
            return false;
        }

        if (oldDue != null) {
            unindexScheduledTick(pos.toLong(), oldDue.longValue());
        }
        scheduledFluidTicks.put(key, Long.valueOf(dueWorldTime));
        indexScheduledTick(pos.toLong(), dueWorldTime);
        markDirty();
        return true;
    }

    public Long getScheduledFluidTick(BlockPos pos) {
        return pos == null ? null
                : scheduledFluidTicks.get(Long.valueOf(pos.toLong()));
    }

    public boolean removeScheduledFluidTick(BlockPos pos) {
        if (pos == null) {
            return false;
        }
        Long removed = scheduledFluidTicks.remove(Long.valueOf(pos.toLong()));
        if (removed != null) {
            unindexScheduledTick(pos.toLong(), removed.longValue());
            markDirty();
            return true;
        }
        return false;
    }

    /**
     * Returns due positions in deterministic due-time/position order.
     * Unloaded chunks are filtered by FluidTickScheduler without deleting
     * their persisted entries.
     */
    public List<BlockPos> getDueScheduledFluidTicks(long now) {
        List<BlockPos> result = new ArrayList<BlockPos>();
        for (Map.Entry<Long, TreeSet<Long>> dueEntry
                : scheduledFluidTicksByTime.headMap(Long.valueOf(now), true).entrySet()) {
            for (Long packedPosition : dueEntry.getValue()) {
                result.add(BlockPos.fromLong(packedPosition.longValue()));
            }
        }
        return result;
    }

    private void indexScheduledTick(long packedPosition, long dueWorldTime) {
        Long dueKey = Long.valueOf(dueWorldTime);
        TreeSet<Long> positions = scheduledFluidTicksByTime.get(dueKey);
        if (positions == null) {
            positions = new TreeSet<Long>();
            scheduledFluidTicksByTime.put(dueKey, positions);
        }
        positions.add(Long.valueOf(packedPosition));
    }

    private void unindexScheduledTick(long packedPosition, long dueWorldTime) {
        Long dueKey = Long.valueOf(dueWorldTime);
        TreeSet<Long> positions = scheduledFluidTicksByTime.get(dueKey);
        if (positions == null) {
            return;
        }
        positions.remove(Long.valueOf(packedPosition));
        if (positions.isEmpty()) {
            scheduledFluidTicksByTime.remove(dueKey);
        }
    }

    public boolean putWater(BlockPos pos, FluidStateBridge state) {
        if (pos == null || state == null
                || state.getKind() != FluidStateBridge.FluidKind.WATER) {
            return false;
        }

        Long key = Long.valueOf(pos.toLong());
        FluidStateBridge old = waterStates.put(key, state);
        if (old == null) {
            addToChunkIndex(key.longValue());
        }
        if (!state.equals(old)) {
            markDirty();
        }
        return true;
    }

    public boolean remove(BlockPos pos) {
        if (pos == null) {
            return false;
        }

        long packed = pos.toLong();
        FluidStateBridge removed = waterStates.remove(Long.valueOf(packed));
        if (removed != null) {
            removeFromChunkIndex(packed);
            markDirty();
            return true;
        }
        return false;
    }

    public List<BlockPos> getPositionsInChunk(int chunkX, int chunkZ) {
        List<BlockPos> result = new ArrayList<BlockPos>();
        Set<Long> packedPositions = chunkIndex.get(Long.valueOf(chunkKey(chunkX, chunkZ)));
        if (packedPositions == null) {
            return result;
        }
        for (Long key : packedPositions) {
            result.add(BlockPos.fromLong(key.longValue()));
        }
        return result;
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        waterStates.clear();
        chunkIndex.clear();
        scheduledFluidTicks.clear();
        scheduledFluidTicksByTime.clear();
        NBTTagList entries = compound.getTagList("water", 10);
        for (int i = 0; i < entries.tagCount(); i++) {
            NBTTagCompound entry = entries.getCompoundTagAt(i);
            long packedPos = entry.getLong("pos");
            int amount = entry.hasKey("amount") ? entry.getInteger("amount") : 8;
            boolean falling = entry.getBoolean("falling");
            // Older sidecar saves did not store an explicit source flag.
            // In that format, amount 8 with no falling bit represented a source.
            boolean source = entry.hasKey("source")
                    ? entry.getBoolean("source")
                    : amount >= FluidLevelMapper.MAX_AMOUNT && !falling;
            FluidStateBridge state = source
                    ? FluidStateBridge.source(FluidStateBridge.FluidKind.WATER)
                    : FluidStateBridge.flowing(FluidStateBridge.FluidKind.WATER,
                            amount, falling);
            waterStates.put(Long.valueOf(packedPos), state);
            addToChunkIndex(packedPos);
        }

        // Added in the persisted scheduler update. Old saves simply have no
        // "fluidTicks" list and remain fully compatible.
        NBTTagList ticks = compound.getTagList("fluidTicks", 10);
        for (int i = 0; i < ticks.tagCount(); i++) {
            NBTTagCompound entry = ticks.getCompoundTagAt(i);
            long packedPosition = entry.getLong("pos");
            long dueWorldTime = entry.getLong("due");
            Long key = Long.valueOf(packedPosition);
            Long previousDue = scheduledFluidTicks.put(
                    key, Long.valueOf(dueWorldTime));
            if (previousDue != null) {
                unindexScheduledTick(packedPosition, previousDue.longValue());
            }
            indexScheduledTick(packedPosition, dueWorldTime);
        }
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) ^ (chunkZ & 0xFFFFFFFFL);
    }

    private void addToChunkIndex(long packedPos) {
        BlockPos pos = BlockPos.fromLong(packedPos);
        Long key = Long.valueOf(chunkKey(pos.getX() >> 4, pos.getZ() >> 4));
        Set<Long> positions = chunkIndex.get(key);
        if (positions == null) {
            positions = new HashSet<Long>();
            chunkIndex.put(key, positions);
        }
        positions.add(Long.valueOf(packedPos));
    }

    private void removeFromChunkIndex(long packedPos) {
        BlockPos pos = BlockPos.fromLong(packedPos);
        Long key = Long.valueOf(chunkKey(pos.getX() >> 4, pos.getZ() >> 4));
        Set<Long> positions = chunkIndex.get(key);
        if (positions != null) {
            positions.remove(Long.valueOf(packedPos));
            if (positions.isEmpty()) {
                chunkIndex.remove(key);
            }
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        NBTTagList entries = new NBTTagList();
        for (Map.Entry<Long, FluidStateBridge> entry : waterStates.entrySet()) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setLong("pos", entry.getKey().longValue());
            tag.setInteger("amount", entry.getValue().getAmount());
            tag.setBoolean("falling", entry.getValue().isFalling());
            tag.setBoolean("source", entry.getValue().isSource());
            entries.appendTag(tag);
        }
        compound.setTag("water", entries);

        NBTTagList ticks = new NBTTagList();
        for (Map.Entry<Long, Long> entry : scheduledFluidTicks.entrySet()) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setLong("pos", entry.getKey().longValue());
            tag.setLong("due", entry.getValue().longValue());
            ticks.appendTag(tag);
        }
        compound.setTag("fluidTicks", ticks);
        return compound;
    }
}
