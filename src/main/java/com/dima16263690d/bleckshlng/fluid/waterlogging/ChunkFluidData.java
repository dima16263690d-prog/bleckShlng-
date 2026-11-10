package com.dima16263690d.bleckshlng.fluid.waterlogging;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    /** Absolute world-time deadlines for pending fluid updates, keyed by BlockPos. */
    private final Map<Long, Long> scheduledTicks =
            new HashMap<Long, Long>();

    public ChunkFluidData() {
        super(DATA_NAME);
    }

    public ChunkFluidData(String name) {
        super(name);
    }

    public static ChunkFluidData get(World world) {
        if (world == null || world.isRemote) {
            return null;
        }

        MapStorage storage = world.getPerWorldStorage();
        if (storage == null) {
            return null;
        }

        ChunkFluidData data = (ChunkFluidData) storage.getOrLoadData(
                ChunkFluidData.class, DATA_NAME);
        if (data == null) {
            data = new ChunkFluidData();
            storage.setData(DATA_NAME, data);
        }
        return data;
    }

    public FluidStateBridge getWater(BlockPos pos) {
        return pos == null ? null : waterStates.get(Long.valueOf(pos.toLong()));
    }

    /**
     * Returns the persisted absolute world-time deadline for a fluid update,
     * or null when the position has no saved update.
     */
    public Long getScheduledTick(BlockPos pos) {
        return pos == null ? null : scheduledTicks.get(Long.valueOf(pos.toLong()));
    }

    /**
     * Stores the earliest deadline. A later reschedule must never postpone an
     * already pending vanilla-style fluid update.
     *
     * @return the deadline that is now authoritative for this position
     */
    public long putScheduledTick(BlockPos pos, long dueTime) {
        if (pos == null) {
            return dueTime;
        }
        Long key = Long.valueOf(pos.toLong());
        Long old = scheduledTicks.get(key);
        if (old == null || dueTime < old.longValue()) {
            scheduledTicks.put(key, Long.valueOf(dueTime));
            markDirty();
            return dueTime;
        }
        return old.longValue();
    }

    public boolean removeScheduledTick(BlockPos pos) {
        if (pos == null) {
            return false;
        }
        Long removed = scheduledTicks.remove(Long.valueOf(pos.toLong()));
        if (removed != null) {
            markDirty();
            return true;
        }
        return false;
    }

    public List<BlockPos> getScheduledTickPositionsInChunk(int chunkX, int chunkZ) {
        List<BlockPos> result = new ArrayList<BlockPos>();
        for (Long packed : scheduledTicks.keySet()) {
            BlockPos pos = BlockPos.fromLong(packed.longValue());
            if ((pos.getX() >> 4) == chunkX && (pos.getZ() >> 4) == chunkZ) {
                result.add(pos);
            }
        }
        return result;
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
        Long removedTick = scheduledTicks.remove(Long.valueOf(packed));
        if (removed != null) {
            removeFromChunkIndex(packed);
        }
        if (removed != null || removedTick != null) {
            markDirty();
        }
        if (removed != null) {
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
        scheduledTicks.clear();
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

        // Backward compatible: older saves simply have no "scheduled_ticks" list.
        NBTTagList ticks = compound.getTagList("scheduled_ticks", 10);
        for (int i = 0; i < ticks.tagCount(); i++) {
            NBTTagCompound entry = ticks.getCompoundTagAt(i);
            long packedPos = entry.getLong("pos");
            long dueTime = entry.getLong("due");
            scheduledTicks.put(Long.valueOf(packedPos), Long.valueOf(dueTime));
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
        for (Map.Entry<Long, Long> entry : scheduledTicks.entrySet()) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setLong("pos", entry.getKey().longValue());
            tag.setLong("due", entry.getValue().longValue());
            ticks.appendTag(tag);
        }
        compound.setTag("scheduled_ticks", ticks);
        return compound;
    }
}
