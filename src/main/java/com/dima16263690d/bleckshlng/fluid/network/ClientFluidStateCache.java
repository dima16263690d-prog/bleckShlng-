package com.dima16263690d.bleckshlng.fluid.network;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.util.math.BlockPos;
import com.dima16263690d.bleckshlng.fluid.state.FluidStateBridge;

/**
 * Client read-only cache for server-authoritative waterlogged block states.
 * A chunk index keeps the render path bounded to nearby chunks.
 */
public final class ClientFluidStateCache {
    private static final Map<Integer, Map<Long, FluidStateBridge>> STATES =
            new HashMap<Integer, Map<Long, FluidStateBridge>>();
    private static final Map<Integer, Map<Long, Set<Long>>> CHUNKS =
            new HashMap<Integer, Map<Long, Set<Long>>>();

    private ClientFluidStateCache() { }

    public static synchronized void apply(int dimension, long packedPosition,
                                          FluidStateBridge state, boolean removed) {
        Integer dimensionKey = Integer.valueOf(dimension);
        Map<Long, FluidStateBridge> map = STATES.get(dimensionKey);
        if (removed) {
            if (map != null) {
                FluidStateBridge old = map.remove(Long.valueOf(packedPosition));
                if (old != null) {
                    removeFromChunkIndex(dimensionKey, packedPosition);
                }
                if (map.isEmpty()) STATES.remove(dimensionKey);
            }
            return;
        }
        if (state == null) return;

        if (map == null) {
            map = new HashMap<Long, FluidStateBridge>();
            STATES.put(dimensionKey, map);
        }

        Long posKey = Long.valueOf(packedPosition);
        FluidStateBridge old = map.put(posKey, state);
        if (old == null) addToChunkIndex(dimensionKey, packedPosition);
    }

    public static synchronized FluidStateBridge get(int dimension, long packedPosition) {
        Map<Long, FluidStateBridge> map = STATES.get(Integer.valueOf(dimension));
        return map == null ? null : map.get(Long.valueOf(packedPosition));
    }

    public static synchronized Map<Long, FluidStateBridge> copyChunk(
            int dimension, int chunkX, int chunkZ) {
        Integer dimKey = Integer.valueOf(dimension);
        Map<Long, Set<Long>> chunks = CHUNKS.get(dimKey);
        Map<Long, FluidStateBridge> states = STATES.get(dimKey);
        if (chunks == null || states == null) return Collections.emptyMap();

        Set<Long> positions = chunks.get(Long.valueOf(chunkKey(chunkX, chunkZ)));
        if (positions == null || positions.isEmpty()) return Collections.emptyMap();

        Map<Long, FluidStateBridge> result =
                new HashMap<Long, FluidStateBridge>(positions.size());
        for (Long pos : positions) {
            FluidStateBridge state = states.get(pos);
            if (state != null) result.put(pos, state);
        }
        return result;
    }

    public static synchronized void clearDimension(int dimension) {
        Integer key = Integer.valueOf(dimension);
        STATES.remove(key);
        CHUNKS.remove(key);
    }


    public static synchronized void clearChunk(int dimension, int chunkX, int chunkZ) {
        Integer dimensionKey = Integer.valueOf(dimension);
        Map<Long, Set<Long>> chunks = CHUNKS.get(dimensionKey);
        Map<Long, FluidStateBridge> states = STATES.get(dimensionKey);
        if (chunks == null || states == null) {
            return;
        }

        Set<Long> positions = chunks.remove(Long.valueOf(chunkKey(chunkX, chunkZ)));
        if (positions != null) {
            for (Long packedPosition : positions) {
                states.remove(packedPosition);
            }
        }

        if (states.isEmpty()) {
            STATES.remove(dimensionKey);
        }
        if (chunks.isEmpty()) {
            CHUNKS.remove(dimensionKey);
        }
    }

    public static synchronized void clearAll() {
        STATES.clear();
        CHUNKS.clear();
    }

    private static void addToChunkIndex(Integer dimension, long packedPosition) {
        Map<Long, Set<Long>> chunks = CHUNKS.get(dimension);
        if (chunks == null) {
            chunks = new HashMap<Long, Set<Long>>();
            CHUNKS.put(dimension, chunks);
        }
        BlockPos pos = BlockPos.fromLong(packedPosition);
        Long key = Long.valueOf(chunkKey(pos.getX() >> 4, pos.getZ() >> 4));
        Set<Long> positions = chunks.get(key);
        if (positions == null) {
            positions = new HashSet<Long>();
            chunks.put(key, positions);
        }
        positions.add(Long.valueOf(packedPosition));
    }

    private static void removeFromChunkIndex(Integer dimension, long packedPosition) {
        Map<Long, Set<Long>> chunks = CHUNKS.get(dimension);
        if (chunks == null) return;
        BlockPos pos = BlockPos.fromLong(packedPosition);
        Long key = Long.valueOf(chunkKey(pos.getX() >> 4, pos.getZ() >> 4));
        Set<Long> positions = chunks.get(key);
        if (positions != null) {
            positions.remove(Long.valueOf(packedPosition));
            if (positions.isEmpty()) chunks.remove(key);
        }
        if (chunks.isEmpty()) CHUNKS.remove(dimension);
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) ^ (chunkZ & 0xFFFFFFFFL);
    }
}
