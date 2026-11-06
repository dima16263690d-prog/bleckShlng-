package com.dima16263690d.bleckshlng.fluid.geometry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * World-scoped geometry cache. Entries are invalidated when fluid logic changes
 * a block and the cache is reset at the start of each independent fluid update.
 */
public final class FluidOcclusionCache {
    private static final Map<World, Cache> CACHES = new WeakHashMap<World, Cache>();

    private FluidOcclusionCache() {
    }

    private static Cache get(World world, boolean create) {
        synchronized (CACHES) {
            Cache cache = CACHES.get(world);
            if (cache == null && create) {
                cache = new Cache();
                CACHES.put(world, cache);
            }
            return cache;
        }
    }

    public static void beginFluidUpdate(World world) {
        Cache cache = get(world, true);
        synchronized (cache) {
            cache.clear();
        }
    }

    public static void invalidate(World world, BlockPos pos) {
        Cache cache = get(world, false);
        if (cache == null) return;
        synchronized (cache) {
            cache.boxes.remove(Long.valueOf(pos.toLong()));
            cache.fullBlocks.remove(Long.valueOf(pos.toLong()));
            for (EnumFacing face : EnumFacing.values()) {
                cache.faceCoverage.remove(new FaceKey(pos.toLong(), face.ordinal()));
                cache.mergedFaces.remove(new FaceKey(pos.toLong(), face.ordinal()));
                BlockPos neighbor = pos.offset(face);
                cache.mergedFaces.remove(new FaceKey(neighbor.toLong(), face.getOpposite().ordinal()));
            }
        }
    }

    public static List<AxisAlignedBB> getCollisionBoxes(World world, BlockPos pos) {
        Cache cache = get(world, false);
        if (cache == null) return null;
        synchronized (cache) {
            return cache.boxes.get(Long.valueOf(pos.toLong()));
        }
    }

    public static void putCollisionBoxes(World world, BlockPos pos, List<AxisAlignedBB> boxes) {
        Cache cache = get(world, true);
        synchronized (cache) {
            cache.boxes.put(Long.valueOf(pos.toLong()),
                    Collections.unmodifiableList(new ArrayList<AxisAlignedBB>(boxes)));
        }
    }

    public static Boolean getFaceCoverage(World world, BlockPos pos, EnumFacing face) {
        Cache cache = get(world, false);
        if (cache == null) return null;
        synchronized (cache) {
            return cache.faceCoverage.get(new FaceKey(pos.toLong(), face.ordinal()));
        }
    }

    public static void putFaceCoverage(World world, BlockPos pos,
                                       EnumFacing face, boolean value) {
        Cache cache = get(world, true);
        synchronized (cache) {
            cache.faceCoverage.put(new FaceKey(pos.toLong(), face.ordinal()), Boolean.valueOf(value));
        }
    }

    public static Boolean getMergedFace(World world, BlockPos pos, EnumFacing face) {
        Cache cache = get(world, false);
        if (cache == null) return null;
        synchronized (cache) {
            return cache.mergedFaces.get(new FaceKey(pos.toLong(), face.ordinal()));
        }
    }

    public static void putMergedFace(World world, BlockPos pos,
                                     EnumFacing face, boolean value) {
        Cache cache = get(world, true);
        synchronized (cache) {
            cache.mergedFaces.put(new FaceKey(pos.toLong(), face.ordinal()), Boolean.valueOf(value));
        }
    }

    public static Boolean getFullBlock(World world, BlockPos pos) {
        Cache cache = get(world, false);
        if (cache == null) return null;
        synchronized (cache) {
            return cache.fullBlocks.get(Long.valueOf(pos.toLong()));
        }
    }

    public static void putFullBlock(World world, BlockPos pos, boolean value) {
        Cache cache = get(world, true);
        synchronized (cache) {
            cache.fullBlocks.put(Long.valueOf(pos.toLong()), Boolean.valueOf(value));
        }
    }

    private static final class Cache {
        private final Map<Long, List<AxisAlignedBB>> boxes =
                new HashMap<Long, List<AxisAlignedBB>>();
        private final Map<FaceKey, Boolean> faceCoverage =
                new HashMap<FaceKey, Boolean>();
        private final Map<FaceKey, Boolean> mergedFaces =
                new HashMap<FaceKey, Boolean>();
        private final Map<Long, Boolean> fullBlocks = new HashMap<Long, Boolean>();

        private void clear() {
            boxes.clear();
            faceCoverage.clear();
            mergedFaces.clear();
            fullBlocks.clear();
        }
    }

    private static final class FaceKey {
        private final long position;
        private final int face;

        private FaceKey(long position, int face) {
            this.position = position;
            this.face = face;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) return true;
            if (!(object instanceof FaceKey)) return false;
            FaceKey other = (FaceKey) object;
            return position == other.position && face == other.face;
        }

        @Override
        public int hashCode() {
            int result = (int) (position ^ (position >>> 32));
            return 31 * result + face;
        }
    }
}
