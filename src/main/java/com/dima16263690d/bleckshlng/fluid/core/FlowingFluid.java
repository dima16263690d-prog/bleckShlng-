package com.dima16263690d.bleckshlng.fluid.core;

import java.util.EnumMap;
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
            boolean sameFluid = !neighbor.isEmpty()
                    && isSame(neighbor.getType());
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
    protected FluidState getNewLiquid(FluidWorld world, FluidPos pos) {
        int maximumAmount = 0;
        int sourceCount = 0;

        for (FluidDirection direction : FluidDirection.HORIZONTAL) {
            FluidPos neighborPos = pos.offset(direction);
            if (!world.isLoaded(neighborPos)
                    || !world.canPassThroughWall(pos, direction)) {
                continue;
            }

            FluidState neighbor = safeFluid(world, neighborPos);
            if (neighbor == null || neighbor.isEmpty()
                    || !isSame(neighbor.getType())) {
                continue;
            }
            if (neighbor.isSource()) {
                sourceCount++;
            }
            maximumAmount = Math.max(maximumAmount, neighbor.getAmount());
        }

        if (canConvertToSource() && sourceCount >= 2
                && world.canCreateFluidSource(pos, this)) {
            FluidPos below = pos.below();
            if (world.isLoaded(below)) {
                FluidState belowFluid = safeFluid(world, below);
                boolean sourceBelow = belowFluid != null
                        && !belowFluid.isEmpty()
                        && belowFluid.isSource()
                        && isSame(belowFluid.getType());
                if (world.isSolidMaterial(below) || sourceBelow) {
                    return sourceState();
                }
            }
        }

        FluidPos above = pos.above();
        if (world.isLoaded(above) && world.canPassThroughWall(pos, FluidDirection.UP)) {
            FluidState aboveFluid = safeFluid(world, above);
            if (aboveFluid != null && !aboveFluid.isEmpty()
                    && isSame(aboveFluid.getType())) {
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
                || !world.isLoaded(pos) || !isSame(state.getType())) {
            return;
        }

        FluidState active = state;
        if (!state.isSource()) {
            FluidState next = getNewLiquid(world, pos);
            int delay = getSpreadDelay(world, pos, state, next);
            if (next == null || next.isEmpty()) {
                world.removeFluid(pos);
                return;
            }
            if (!next.equals(state)) {
                active = next;
                world.setFluidState(pos, next);
                world.scheduleFluidTick(pos, next.getType(), delay);
                world.notifyFluidNeighbors(pos, next.getType());
            }
        }
        spread(world, pos, active);
    }

    protected void spread(FluidWorld world, FluidPos pos, FluidState state) {
        if (state == null || state.isEmpty() || !world.isLoaded(pos)) {
            return;
        }

        FluidPos below = pos.below();
        if (!world.isLoaded(below)) {
            return;
        }

        FluidState belowState = getNewLiquid(world, below);
        if (canSpreadTo(world, pos, FluidDirection.DOWN, below, belowState)) {
            world.spreadFluidTo(below, belowState, FluidDirection.DOWN);
            if (sourceNeighborCount(world, pos) >= 3) {
                spreadToSides(world, pos, state);
            }
        } else if (state.isSource() || !isWaterHole(world, pos, below)) {
            spreadToSides(world, pos, state);
        }
    }

    private void spreadToSides(FluidWorld world, FluidPos pos, FluidState state) {
        int amount = state.getAmount() - getDropOff(world);
        if (state.isFalling()) {
            amount = 7;
        }
        if (amount <= 0) {
            return;
        }

        Map<FluidDirection, FluidState> spread = getSpread(world, pos);
        for (Map.Entry<FluidDirection, FluidState> entry : spread.entrySet()) {
            FluidDirection direction = entry.getKey();
            FluidPos target = pos.offset(direction);
            if (!world.isLoaded(target)
                    || !canSpreadTo(world, pos, direction, target, entry.getValue())) {
                continue;
            }
            world.spreadFluidTo(target, entry.getValue(), direction);
        }
    }

    private Map<FluidDirection, FluidState> getSpread(FluidWorld world, FluidPos pos) {
        Map<FluidDirection, FluidState> result =
                new EnumMap<FluidDirection, FluidState>(FluidDirection.class);
        int bestDistance = NO_SLOPE;

        for (FluidDirection direction : FluidDirection.HORIZONTAL) {
            FluidPos target = pos.offset(direction);
            if (!world.isLoaded(target)) {
                continue;
            }

            FluidState targetFluid = getNewLiquid(world, target);
            if (!canPassThrough(world, target, direction, targetFluid)) {
                continue;
            }

            FluidPos targetBelow = target.below();
            int distance = isWaterHole(world, target, targetBelow)
                    ? 0 : getSlopeDistance(world, target, 1,
                    direction.opposite(), new HashSet<FluidPos>());

            if (distance < bestDistance) {
                result.clear();
                bestDistance = distance;
            }
            if (distance == bestDistance) {
                result.put(direction, targetFluid);
            }
        }
        return result;
    }

    private int getSlopeDistance(FluidWorld world, FluidPos pos, int depth,
                                 FluidDirection cameFrom, Set<FluidPos> visited) {
        if (depth > getSlopeFindDistance(world) || !visited.add(pos)) {
            return NO_SLOPE;
        }

        int best = NO_SLOPE;
        for (FluidDirection direction : FluidDirection.HORIZONTAL) {
            if (direction == cameFrom || !world.isLoaded(pos)) {
                continue;
            }

            FluidPos next = pos.offset(direction);
            if (!world.isLoaded(next)) {
                continue;
            }
            FluidState nextFluid = getNewLiquid(world, next);
            if (!canPassThrough(world, next, direction, nextFluid)) {
                continue;
            }

            FluidPos below = next.below();
            if (isWaterHole(world, next, below)) {
                visited.remove(pos);
                return depth;
            }

            if (depth < getSlopeFindDistance(world)) {
                int distance = getSlopeDistance(world, next, depth + 1,
                        direction.opposite(), visited);
                best = Math.min(best, distance);
            }
        }
        visited.remove(pos);
        return best;
    }

    private boolean isWaterHole(FluidWorld world, FluidPos from, FluidPos below) {
        if (!world.isLoaded(from) || !world.isLoaded(below)
                || !world.canPassThroughWall(from, FluidDirection.DOWN)) {
            return false;
        }
        FluidState belowFluid = safeFluid(world, below);
        if (belowFluid != null && !belowFluid.isEmpty()
                && isSame(belowFluid.getType())) {
            return true;
        }
        return world.canHoldFluid(below, getFlowing());
    }

    private boolean canPassThrough(FluidWorld world, FluidPos target,
                                   FluidDirection direction, FluidState targetFluid) {
        if (!world.isLoaded(target)) {
            return false;
        }
        if (targetFluid != null && !targetFluid.isEmpty()
                && targetFluid.isSource() && isSame(targetFluid.getType())) {
            return false;
        }
        return world.canPassThroughWall(target.offset(direction.opposite()), direction)
                && world.canHoldFluid(target, getFlowing());
    }

    private boolean canSpreadTo(FluidWorld world, FluidPos from,
                                FluidDirection direction, FluidPos target,
                                FluidState incoming) {
        if (!world.isLoaded(from) || !world.isLoaded(target)
                || incoming == null || incoming.isEmpty()) {
            return false;
        }
        FluidState current = safeFluid(world, target);
        if (current != null && !current.isEmpty()
                && !current.getType().canBeReplacedWith(current, world,
                target, incoming.getType(), direction)) {
            return false;
        }
        return world.canPassThroughWall(from, direction)
                && world.canReplaceFluid(target, incoming.getType(), direction)
                && world.canHoldFluid(target, incoming.getType());
    }

    private int sourceNeighborCount(FluidWorld world, FluidPos pos) {
        int count = 0;
        for (FluidDirection direction : FluidDirection.HORIZONTAL) {
            FluidPos neighborPos = pos.offset(direction);
            if (!world.isLoaded(neighborPos)) {
                continue;
            }
            FluidState neighbor = safeFluid(world, neighborPos);
            if (neighbor != null && !neighbor.isEmpty()
                    && neighbor.isSource() && isSame(neighbor.getType())) {
                count++;
            }
        }
        return count;
    }

    @Override
    public float getExplosionResistance() {
        return 100.0F;
    }

    private static FluidState safeFluid(FluidWorld world, FluidPos pos) {
        FluidState state = world.getFluidState(pos);
        return state == null ? FluidState.EMPTY : state;
    }

    private static int clampAmount(int amount) {
        return Math.max(1, Math.min(FluidState.MAX_AMOUNT, amount));
    }
}
