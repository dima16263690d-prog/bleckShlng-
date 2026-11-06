package com.dima16263690d.bleckshlng.fluid.network;

import java.util.HashMap;
import java.util.Map;
import com.dima16263690d.bleckshlng.fluid.state.FluidStateBridge;

/** Client read-only cache for server-authoritative waterlogged block states. */
public final class ClientFluidStateCache {
    private static final Map<Integer, Map<Long, FluidStateBridge>> STATES =
            new HashMap<Integer, Map<Long, FluidStateBridge>>();

    private ClientFluidStateCache() {
    }

    public static synchronized void apply(int dimension, long packedPosition,
                                          FluidStateBridge state, boolean removed) {
        Integer dimensionKey = Integer.valueOf(dimension);
        Map<Long, FluidStateBridge> map = STATES.get(dimensionKey);
        if (removed) {
            if (map != null) {
                map.remove(Long.valueOf(packedPosition));
                if (map.isEmpty()) {
                    STATES.remove(dimensionKey);
                }
            }
            return;
        }

        if (state == null) {
            return;
        }
        if (map == null) {
            map = new HashMap<Long, FluidStateBridge>();
            STATES.put(dimensionKey, map);
        }
        map.put(Long.valueOf(packedPosition), state);
    }

    public static synchronized FluidStateBridge get(int dimension, long packedPosition) {
        Map<Long, FluidStateBridge> map = STATES.get(Integer.valueOf(dimension));
        return map == null ? null : map.get(Long.valueOf(packedPosition));
    }

    public static synchronized void clearDimension(int dimension) {
        STATES.remove(Integer.valueOf(dimension));
    }

    public static synchronized void clearAll() {
        STATES.clear();
    }
}
