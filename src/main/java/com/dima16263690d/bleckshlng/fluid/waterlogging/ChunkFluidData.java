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

    /**
     * Returns already loaded/saved fluid data without creating an empty
     * WorldSavedData instance. This is used by hot World#setBlockState hooks,
     * which must not create persistent data for ordinary block changes.
     */
    public static ChunkFluidData getIfPresent(World world) {
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

    public FluidStateBridge getWater(BlockPos pos) {
        return pos == null ? null : waterStates.get(Long.valueOf(pos.toLong()));
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
        NBTTagList entries = compound.getTagList("water", 10);
        for (int i = 0; i < entries.tagCount(); i++) {
            NBTTagCompound entry = entries.getCompoundTagAt(i);
            long packedPos = entry.getLong("pos");
            int amount = entry.hasKey("amount") ? entry.getInteger("amount") : 8;
            boolean falling = entry.getBoolean("falling");
            boolean source = entry.hasKey("source") && entry.getBoolean("source");
            FluidStateBridge state = source
                    ? FluidStateBridge.source(FluidStateBridge.FluidKind.WATER)
                    : FluidStateBridge.flowing(FluidStateBridge.FluidKind.WATER,
                            amount, falling);
            waterStates.put(Long.valueOf(packedPos), state);
            addToChunkIndex(packedPos);
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
        return compound;
    }
}
