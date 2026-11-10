package com.dima16263690d.bleckshlng.fluid.core;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Portable port of the 1.18.2 FlowingFluid algorithm.
 *
 * The class owns fluid semantics: state transitions, source conversion,
 * slope search, downward priority, horizontal spreading and flow vectors.
 * All game access is delegated to FluidWorld.
 */
public abstract class FlowingFluid extends Fluid {
    private static final int NO_SLOPE = 1000;

    private final boolean sourceVariant;
    private FlowingFluid source;
    private FlowingFluid flowing;

    protected FlowingFluid(String id, String family, boolean sourceVariant) {
        super(id, family);
        this.sourceVariant = sourceVariant;
    }

    public final void bindVariants(FlowingFluid source, FlowingFluid flowing) {
        if (source == null || flowing == null) {
            throw new IllegalArgumentException("Source and flowing variants are required");
        }
        this.source = source;
        this.flowing = flowing;
    }

    public final FlowingFluid getSource() {
        return source == null ? this : source;
    }

    public final FlowingFluid getFlowing() {
        return flowing == null ? this : flowing;
    }

    protected final boolean isSourceVariant() {
        return sourceVariant;
    }

    public final FluidState sourceState() {
        return FluidState.source(getSource());
    }

    public final FluidState flowingState(int amount, boolean falling) {
        return FluidState.flowing(getFlowing(), clampAmount(amount), falling);
    }

    @Override
    public final boolean isSource(FluidState state) {
        return state != null && state.getType() == this && sourceVariant && state.isSource();
    }

    @Override
    public final int getAmount(FluidState state) {
        if (state == null || state.isEmpty() || !isSame(state.getType())) {
            return 0;
        }
        return state.isSource() ? FluidState.MAX_AMOUNT : state.getAmount();
    }

    @Override
    public final FluidVector getFlow(FluidWorld world, FluidPos pos, FluidState state) {
        if (world == null || pos == null || state == null || state.isEmpty()) {
            return FluidVector.ZERO;
        }

        double flowX = 0.0D;
        double flowZ = 0.0D;
        float ownHeight = getOwnHeight(state);

        for (FluidDirection direction : FluidDirection.HORIZONTAL) {
            FluidPos neighborPos = pos.offset(direction);
            if (!world.isLoaded(neighborPos)) {
                continue;
            }

            FluidState neighbor = safeFluid(world, neighborPos);
            // FlowingFluid#affectsFlow accepts only EMPTY or this same fluid.
            // A different fluid must not be treated as an empty cell: doing
            // so lets water pull its flow vector toward lava's lower neighbor
            // (and vice versa), which differs from the 1.18.2 implementation.
            if (!neighbor.isEmpty() && !isSame(neighbor.getType())) {
                continue;
            }

            boolean sameFluid = !neighbor.isEmpty();
            float neighborHeight = sameFluid
                    ? neighbor.getType().getOwnHeight(neighbor) : 0.0F;
            float difference = 0.0F;

            if (sameFluid) {
                difference = ownHeight - neighborHeight;
            } else if (!world.blocksMotion(neighborPos)) {
                // A different fluid (or air) does not block the surface
                // slope test. FlowingFluid checks the same fluid one block
                // below and uses the 8/9 offset when it is found.
                FluidPos belowPos = neighborPos.below();
                if (world.isLoaded(belowPos)) {
                    FluidState below = safeFluid(world, belowPos);
                    if (!below.isEmpty() && isSame(below.getType())) {
                        neighborHeight = below.getType().getOwnHeight(below);
                        if (neighborHeight > 0.0F) {
                            difference = ownHeight - (neighborHeight - 0.8888889F);
                        }
                    }
                }
            }

            flowX += direction.getX() * (double) difference;
            flowZ += direction.getZ() * (double) difference;
        }

        FluidVector vector = new FluidVector(flowX, 0.0D, flowZ);
        if (state.isFalling()) {
            for (FluidDirection direction : FluidDirection.HORIZONTAL) {
                FluidPos side = pos.offset(direction);
                if (!world.isLoaded(side)) {
                    continue;
                }
                FluidPos sideAbove = side.above();
                if (isSolidFace(world, side, direction)
                        || (world.isLoaded(sideAbove)
                        && isSolidFace(world, sideAbove, direction))) {
                    vector = vector.normalize().add(0.0D, -6.0D, 0.0D);
                    break;
                }
            }
        }
        return vector.normalize();
    }

    private boolean isSolidFace(FluidWorld world, FluidPos pos,
                                FluidDirection direction) {
        FluidState fluid = safeFluid(world, pos);
        if (fluid != null && !fluid.isEmpty() && isSame(fluid.getType())) {
            return false;
        }
        if (direction == FluidDirection.UP) {
            return true;
        }
        if (world.isIce(pos)) {
            return false;
        }
        return world.isFaceSturdy(pos, direction);
    }

    /**
     * FlowingFluid#getNewLiquid: horizontal maximum, source conversion,
     * same-fluid-above falling state and dimension-specific drop-off.
     */
    /**
     * Compatibility entry point used by tests and subclasses.
     * Production ticks share one per-tick lookup cache across spread search.
     */
    protected FluidState getNewLiquid(FluidWorld world, FluidPos pos) {
        return getNewLiquid(world, pos, new SearchCache(world));
    }

    private FluidState getNewLiquid(FluidWorld world, FluidPos pos,
                                    SearchCache cache) {
        int maximumAmount = 0;
        int sourceCount = 0;
        for (FluidDirection direction : FluidDirection.HORIZONTAL) {
            FluidPos neighborPos = pos.offset(direction);
            if (!cache.isLoaded(neighborPos)
                    || !cache.canPassThroughWall(pos, direction)) continue;
            FluidState neighbor = cache.fluid(neighborPos);
            if (neighbor.isEmpty() || !isSame(neighbor.getType())) continue;
            if (neighbor.isSource()) sourceCount++;
            maximumAmount = Math.max(maximumAmount, neighbor.getAmount());
        }
        if (canConvertToSource() && sourceCount >= 2
                && cache.canCreateFluidSource(pos, this)) {
            FluidPos below = pos.below();
            if (cache.isLoaded(below)) {
                FluidState belowFluid = cache.fluid(below);
                boolean sourceBelow = !belowFluid.isEmpty()
                        && belowFluid.isSource()
                        && isSame(belowFluid.getType());
                if (cache.isSolidMaterial(below) || sourceBelow) return sourceState();
            }
        }
        FluidPos above = pos.above();
        if (cache.isLoaded(above)
                && cache.canPassThroughWall(pos, FluidDirection.UP)) {
            FluidState aboveFluid = cache.fluid(above);
            if (!aboveFluid.isEmpty() && isSame(aboveFluid.getType())) {
                return flowingState(FluidState.MAX_AMOUNT, true);
            }
        }
        int amount = maximumAmount - getDropOff(world);
        return amount <= 0 ? FluidState.EMPTY : flowingState(amount, false);
    }

    protected abstract boolean canConvertToSource();
    public abstract int getSlopeFindDistance(FluidWorld world);
    protected abstract int getDropOff(FluidWorld world);

    protected int getSpreadDelay(FluidWorld world, FluidPos pos,
                                 FluidState current, FluidState next) {
        return getTickDelay(world);
    }

    @Override
    public void tick(FluidWorld world, FluidPos pos, FluidState state) {
        if (world == null || pos == null || state == null || state.isEmpty()
                || !world.isLoaded(pos) || !isSame(state.getType())) return;

        // Cache is local to one scheduled update; never retain mutable world state between ticks.
        SearchCache cache = new SearchCache(world);
        FluidState active = state;
        if (!state.isSource()) {
            FluidState next = getNewLiquid(world, pos, cache);
            int delay = getSpreadDelay(world, pos, state, next);
            if (next == null || next.isEmpty()) {
                world.removeFluid(pos);
                cache.invalidateFluid(pos);
                return;
            }
            if (!next.equals(state)) {
                active = next;
                world.setFluidState(pos, next);
                cache.invalidateFluid(pos);
                world.scheduleFluidTick(pos, next.getType(), delay);
                world.notifyFluidNeighbors(pos, next.getType());
            }
        }
        spread(world, pos, active, cache);
    }

    protected void spread(FluidWorld world, FluidPos pos, FluidState state) {
        spread(world, pos, state, new SearchCache(world));
    }

    private void spread(FluidWorld world, FluidPos pos, FluidState state,
                        SearchCache cache) {
        if (state == null || state.isEmpty() || !cache.isLoaded(pos)) return;
        FluidPos below = pos.below();
        if (!cache.isLoaded(below)) return;
        FluidState belowState = getNewLiquid(world, below, cache);
        if (canSpreadTo(world, pos, FluidDirection.DOWN, below, belowState, cache)) {
            world.spreadFluidTo(below, belowState, FluidDirection.DOWN);
            cache.invalidateFluid(below);
            if (sourceNeighborCount(pos, cache) >= 3) spreadToSides(world, pos, state, cache);
        } else if (state.isSource() || !isWaterHole(world, pos, below, cache)) {
            spreadToSides(world, pos, state, cache);
        }
    }

    private void spreadToSides(FluidWorld world, FluidPos pos, FluidState state,
                               SearchCache cache) {
        int amount = state.getAmount() - getDropOff(world);
        if (state.isFalling()) amount = 7;
        if (amount <= 0) return;

        Map<FluidDirection, FluidState> spread = getSpread(world, pos, cache);
        for (Map.Entry<FluidDirection, FluidState> entry : spread.entrySet()) {
            FluidDirection direction = entry.getKey();
            FluidPos target = pos.offset(direction);
            if (!cache.isLoaded(target)
                    || !canSpreadTo(world, pos, direction, target, entry.getValue(), cache)) {
                continue;
            }
            world.spreadFluidTo(target, entry.getValue(), direction);
            cache.invalidateFluid(target);
        }
    }

    private Map<FluidDirection, FluidState> getSpread(FluidWorld world, FluidPos pos,
                                                       SearchCache cache) {
        Map<FluidDirection, FluidState> result =
                new EnumMap<FluidDirection, FluidState>(FluidDirection.class);
        int bestDistance = NO_SLOPE;
        for (FluidDirection direction : FluidDirection.HORIZONTAL) {
            FluidPos target = pos.offset(direction);
            if (!cache.isLoaded(target)) continue;
            FluidState existingFluid = cache.fluid(target);
            if (!canPassThrough(target, direction, existingFluid, cache)) continue;
            FluidState nextFluid = getNewLiquid(world, target, cache);
            if (nextFluid == null || nextFluid.isEmpty()) continue;
            FluidPos targetBelow = target.below();
            int distance = isWaterHole(world, target, targetBelow, cache)
                    ? 0 : getSlopeDistance(world, target, 1, direction.opposite(),
                    new HashSet<FluidPos>(), cache);
            if (distance < bestDistance) {
                result.clear();
                bestDistance = distance;
            }
            if (distance == bestDistance) result.put(direction, nextFluid);
        }
        return result;
    }

    private int getSlopeDistance(FluidWorld world, FluidPos pos, int depth,
                                 FluidDirection cameFrom, Set<FluidPos> visited,
                                 SearchCache cache) {
        if (depth > getSlopeFindDistance(world) || !visited.add(pos)) return NO_SLOPE;
        int best = NO_SLOPE;
        for (FluidDirection direction : FluidDirection.HORIZONTAL) {
            if (direction == cameFrom || !cache.isLoaded(pos)) continue;
            FluidPos next = pos.offset(direction);
            if (!cache.isLoaded(next)) continue;
            FluidState existingFluid = cache.fluid(next);
            if (!canPassThrough(next, direction, existingFluid, cache)) continue;
            FluidPos below = next.below();
            if (isWaterHole(world, next, below, cache)) {
                visited.remove(pos);
                return depth;
            }
            if (depth < getSlopeFindDistance(world)) {
                int distance = getSlopeDistance(world, next, depth + 1,
                        direction.opposite(), visited, cache);
                best = Math.min(best, distance);
            }
        }
        visited.remove(pos);
        return best;
    }

    private boolean isWaterHole(FluidWorld world, FluidPos from, FluidPos below,
                                SearchCache cache) {
        Boolean cached = cache.waterHoles.get(from);
        if (cached != null) return cached.booleanValue();
        boolean result = false;
        if (cache.isLoaded(from) && cache.isLoaded(below)
                && cache.canPassThroughWall(from, FluidDirection.DOWN)) {
            FluidState belowFluid = cache.fluid(below);
            result = (!belowFluid.isEmpty() && isSame(belowFluid.getType()))
                    || cache.canHoldFluid(below, getFlowing());
        }
        cache.waterHoles.put(from, Boolean.valueOf(result));
        return result;
    }

    private boolean canPassThrough(FluidPos target, FluidDirection direction,
                                   FluidState targetFluid, SearchCache cache) {
        if (!cache.isLoaded(target)) return false;
        if (!targetFluid.isEmpty() && targetFluid.isSource()
                && isSame(targetFluid.getType())) return false;
        FluidPos from = target.offset(direction.opposite());
        return cache.canPassThroughWall(from, direction)
                && cache.canHoldFluid(target, getFlowing());
    }

    private boolean canSpreadTo(FluidWorld world, FluidPos from,
                                FluidDirection direction, FluidPos target,
                                FluidState incoming, SearchCache cache) {
        if (!cache.isLoaded(from) || !cache.isLoaded(target)
                || incoming == null || incoming.isEmpty()) return false;
        FluidState current = cache.fluid(target);
        if (!current.isEmpty()
                && !current.getType().canBeReplacedWith(current, world,
                target, incoming.getType(), direction)) return false;
        return cache.canPassThroughWall(from, direction)
                && world.canReplaceFluid(target, incoming.getType(), direction)
                && cache.canHoldFluid(target, incoming.getType());
    }

    private int sourceNeighborCount(FluidPos pos, SearchCache cache) {
        int count = 0;
        for (FluidDirection direction : FluidDirection.HORIZONTAL) {
            FluidPos neighborPos = pos.offset(direction);
            if (!cache.isLoaded(neighborPos)) continue;
            FluidState neighbor = cache.fluid(neighborPos);
            if (!neighbor.isEmpty() && neighbor.isSource()
                    && isSame(neighbor.getType())) count++;
        }
        return count;
    }

    @Override
    public float getExplosionResistance() {
        return 100.0F;
    }

    /**
     * Per-tick neighbour cache for the flow/slope search. Mutated positions
     * invalidate state, geometry and dependent water-hole entries.
     */
    private static final class SearchCache {
        private final FluidWorld world;
        private final Map<FluidPos, Boolean> loaded = new HashMap<FluidPos, Boolean>();
        private final Map<FluidPos, FluidState> fluids = new HashMap<FluidPos, FluidState>();
        private final Map<FaceKey, Boolean> openFaces = new HashMap<FaceKey, Boolean>();
        private final Map<FluidPos, Boolean> holdFluid = new HashMap<FluidPos, Boolean>();
        private final Map<FluidPos, Boolean> solidMaterials = new HashMap<FluidPos, Boolean>();
        private final Map<FluidPos, Boolean> sourceCreation = new HashMap<FluidPos, Boolean>();
        private final Map<FluidPos, Boolean> waterHoles = new HashMap<FluidPos, Boolean>();

        private SearchCache(FluidWorld world) { this.world = world; }

        private boolean isLoaded(FluidPos pos) {
            Boolean value = loaded.get(pos);
            if (value == null) {
                value = Boolean.valueOf(world.isLoaded(pos));
                loaded.put(pos, value);
            }
            return value.booleanValue();
        }

        private FluidState fluid(FluidPos pos) {
            if (!isLoaded(pos)) return FluidState.EMPTY;
            FluidState value = fluids.get(pos);
            if (value == null) {
                value = world.getFluidState(pos);
                if (value == null) value = FluidState.EMPTY;
                fluids.put(pos, value);
            }
            return value;
        }

        private boolean canPassThroughWall(FluidPos from, FluidDirection direction) {
            FaceKey key = new FaceKey(from, direction);
            Boolean value = openFaces.get(key);
            if (value == null) {
                value = Boolean.valueOf(isLoaded(from)
                        && world.canPassThroughWall(from, direction));
                openFaces.put(key, value);
            }
            return value.booleanValue();
        }

        private boolean canHoldFluid(FluidPos pos, Fluid fluid) {
            Boolean value = holdFluid.get(pos);
            if (value == null) {
                value = Boolean.valueOf(isLoaded(pos) && world.canHoldFluid(pos, fluid));
                holdFluid.put(pos, value);
            }
            return value.booleanValue();
        }

        private boolean isSolidMaterial(FluidPos pos) {
            Boolean value = solidMaterials.get(pos);
            if (value == null) {
                value = Boolean.valueOf(isLoaded(pos) && world.isSolidMaterial(pos));
                solidMaterials.put(pos, value);
            }
            return value.booleanValue();
        }

        private boolean canCreateFluidSource(FluidPos pos, Fluid fluid) {
            Boolean value = sourceCreation.get(pos);
            if (value == null) {
                value = Boolean.valueOf(isLoaded(pos) && world.canCreateFluidSource(pos, fluid));
                sourceCreation.put(pos, value);
            }
            return value.booleanValue();
        }

        private void invalidateFluid(FluidPos pos) {
            if (pos == null) return;
            fluids.remove(pos);
            holdFluid.remove(pos);
            solidMaterials.remove(pos);
            sourceCreation.remove(pos);
            waterHoles.remove(pos);
            waterHoles.remove(pos.above());
            for (FluidDirection direction : FluidDirection.values()) {
                openFaces.remove(new FaceKey(pos, direction));
                openFaces.remove(new FaceKey(pos.offset(direction), direction.opposite()));
            }
        }
    }

    private static final class FaceKey {
        private final FluidPos from;
        private final FluidDirection direction;

        private FaceKey(FluidPos from, FluidDirection direction) {
            this.from = from;
            this.direction = direction;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) return true;
            if (!(object instanceof FaceKey)) return false;
            FaceKey other = (FaceKey) object;
            return from.equals(other.from) && direction == other.direction;
        }

        @Override
        public int hashCode() {
            return 31 * from.hashCode() + direction.hashCode();
        }
    }

    private static FluidState safeFluid(FluidWorld world, FluidPos pos) {
        FluidState state = world.getFluidState(pos);
        return state == null ? FluidState.EMPTY : state;
    }

    private static int clampAmount(int amount) {
        return Math.max(1, Math.min(FluidState.MAX_AMOUNT, amount));
    }
}
