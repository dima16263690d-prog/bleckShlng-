package com.dima16263690d.bleckshlng.fluid;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.block.Block;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/**
 * Complete port of {@code net.minecraft.world.level.material.FlowingFluid} from Minecraft 1.21,
 * including the spread algorithm, slope-distance search, occlusion checks and source formation.
 *
 * <p>The 1.21 amount model (1..8) is kept internally; every world interaction is expressed in
 * terms of the legacy liquid block states through {@link FluidState#createLegacyBlock()}.</p>
 */
public abstract class FlowingFluid extends Fluid {
    private static final double MIN_LEVEL_CUTOFF = 0.8888889D;
    private static final double OCCLUDE_EPSILON = 1.0E-7D;

    protected FlowingFluid(String name) {
        super(name);
    }

    // ------------------------------------------------------------------ flow

    @Override
    public Vec3d getFlow(IBlockAccess world, BlockPos pos, FluidState state) {
        double dx = 0.0D;
        double dz = 0.0D;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (EnumFacing direction : EnumFacing.Plane.HORIZONTAL) {
            cursor.setPos(pos).move(direction);
            FluidState neighbour = FluidAccess.getFluidState(world, cursor);
            if (!this.affectsFlow(neighbour)) {
                continue;
            }
            float ownHeight = neighbour.getOwnHeight();
            float delta = 0.0F;
            if (ownHeight == 0.0F) {
                if (!world.getBlockState(cursor).getMaterial().blocksMovement()) {
                    BlockPos below = cursor.down();
                    FluidState belowState = FluidAccess.getFluidState(world, below);
                    if (this.affectsFlow(belowState)) {
                        ownHeight = belowState.getOwnHeight();
                        if (ownHeight > 0.0F) {
                            delta = state.getOwnHeight() - (ownHeight - (float) MIN_LEVEL_CUTOFF);
                        }
                    }
                }
            } else {
                delta = state.getOwnHeight() - ownHeight;
            }
            if (delta != 0.0F) {
                dx += (double) direction.getFrontOffsetX() * (double) delta;
                dz += (double) direction.getFrontOffsetZ() * (double) delta;
            }
        }

        Vec3d flow = new Vec3d(dx, 0.0D, dz);
        if (state.isFalling()) {
            for (EnumFacing direction : EnumFacing.Plane.HORIZONTAL) {
                cursor.setPos(pos).move(direction);
                if (this.isSolidFace(world, cursor, direction) || this.isSolidFace(world, cursor.up(), direction)) {
                    flow = flow.normalize().addVector(0.0D, -6.0D, 0.0D);
                    break;
                }
            }
        }
        return flow.normalize();
    }

    private boolean affectsFlow(FluidState state) {
        return state.isEmpty() || state.getType().isSame(this);
    }

    protected boolean isSolidFace(IBlockAccess world, BlockPos pos, EnumFacing side) {
        IBlockState state = world.getBlockState(pos);
        if (FluidAccess.getFluidState(world, pos).getType().isSame(this)) {
            return false;
        }
        if (side == EnumFacing.UP) {
            return true;
        }
        if (state.getBlock() == Blocks.ICE || state.getBlock() == Blocks.PACKED_ICE || state.getBlock() == Blocks.FROSTED_ICE) {
            return false;
        }
        return state.getBlockFaceShape(world, pos, side) == BlockFaceShape.SOLID;
    }

    // ------------------------------------------------------------------ spread

    public void spread(World world, BlockPos pos, IBlockState blockState, FluidState state) {
        if (state.isEmpty()) {
            return;
        }
        BlockPos below = pos.down();
        IBlockState belowState = world.getBlockState(below);
        FluidState belowFluid = FluidAccess.getFluidState(world, below);
        if (this.canMaybePassThrough(world, pos, blockState, EnumFacing.DOWN, below, belowState, belowFluid)) {
            FluidState newLiquid = this.getNewLiquid(world, below, belowState);
            Fluid fluid = newLiquid.getType();
            if (belowFluid.canBeReplacedWith(world, below, fluid, EnumFacing.DOWN)
                    && canHoldSpecificFluid(world, below, belowState, fluid)) {
                this.spreadTo(world, below, belowState, EnumFacing.DOWN, newLiquid);
                if (this.sourceNeighborCount(world, pos) >= 3) {
                    this.spreadToSides(world, pos, state, blockState);
                }
                return;
            }
        }

        if (state.isSource() || !this.isWaterHole(world, pos, blockState, below, belowState)) {
            this.spreadToSides(world, pos, state, blockState);
        }
    }

    private void spreadToSides(World world, BlockPos pos, FluidState state, IBlockState blockState) {
        int amount = state.getAmount() - this.getDropOff(world);
        if (state.isFalling()) {
            amount = 7;
        }
        if (amount <= 0) {
            return;
        }
        Map<EnumFacing, FluidState> spread = this.getSpread(world, pos, blockState);
        for (Map.Entry<EnumFacing, FluidState> entry : spread.entrySet()) {
            EnumFacing direction = entry.getKey();
            BlockPos target = pos.offset(direction);
            this.spreadTo(world, target, world.getBlockState(target), direction, entry.getValue());
        }
    }

    protected FluidState getNewLiquid(World world, BlockPos pos, IBlockState blockState) {
        int maxAmount = 0;
        int sources = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (EnumFacing direction : EnumFacing.Plane.HORIZONTAL) {
            BlockPos neighbourPos = cursor.setPos(pos).move(direction);
            IBlockState neighbourState = world.getBlockState(neighbourPos);
            FluidState neighbourFluid = FluidAccess.getFluidState(world, neighbourPos);
            if (neighbourFluid.getType().isSame(this)
                    && canPassThroughWall(direction, world, pos, blockState, neighbourPos, neighbourState)) {
                if (neighbourFluid.isSource()) {
                    sources++;
                }
                maxAmount = Math.max(maxAmount, neighbourFluid.getAmount());
            }
        }

        if (sources >= 2 && this.canConvertToSource(world)) {
            BlockPos belowPos = cursor.setPos(pos).move(EnumFacing.DOWN);
            IBlockState belowState = world.getBlockState(belowPos);
            FluidState belowFluid = FluidAccess.getFluidState(world, belowPos);
            if (belowState.getMaterial().blocksMovement() || this.isSourceBlockOfThisType(belowFluid)) {
                return this.getSource(false);
            }
        }

        BlockPos abovePos = cursor.setPos(pos).move(EnumFacing.UP);
        IBlockState aboveState = world.getBlockState(abovePos);
        FluidState aboveFluid = FluidAccess.getFluidState(world, abovePos);
        if (!aboveFluid.isEmpty() && aboveFluid.getType().isSame(this)
                && canPassThroughWall(EnumFacing.UP, world, pos, blockState, abovePos, aboveState)) {
            return this.getFlowing(8, true);
        }
        int newAmount = maxAmount - this.getDropOff(world);
        return newAmount <= 0 ? Fluids.EMPTY.defaultState() : this.getFlowing(newAmount, false);
    }

    public FluidState getFlowing(int amount, boolean falling) {
        return this.getFlowingFluid().flowingState(amount, falling);
    }

    public FluidState getSource(boolean falling) {
        return this.getSourceFluid().flowingState(FluidState.AMOUNT_FULL, falling);
    }

    protected abstract Fluid getFlowingFluid();

    protected abstract Fluid getSourceFluid();

    protected abstract boolean canConvertToSource(World world);

    protected abstract int getDropOff(World world);

    protected abstract int getSlopeFindDistance(World world);

    protected void spreadTo(World world, BlockPos pos, IBlockState blockState, EnumFacing direction, FluidState state) {
        if (blockState.getMaterial() != Material.AIR) {
            this.beforeDestroyingBlock(world, pos, blockState);
        }
        world.setBlockState(pos, state.createLegacyBlock(), 3);
    }

    protected abstract void beforeDestroyingBlock(World world, BlockPos pos, IBlockState state);

    // ------------------------------------------------------------------ search

    protected int getSlopeDistance(World world, BlockPos pos, int distance, EnumFacing skip, IBlockState blockState, SpreadContext context) {
        int best = 1000;
        for (EnumFacing direction : EnumFacing.Plane.HORIZONTAL) {
            if (direction == skip) {
                continue;
            }
            BlockPos neighbourPos = pos.offset(direction);
            IBlockState neighbourState = context.getBlockState(neighbourPos);
            FluidState neighbourFluid = FluidAccess.getFluidState(world, neighbourPos);
            if (!this.canPassThrough(world, this.getFlowingFluid(), pos, blockState, direction, neighbourPos, neighbourState, neighbourFluid)) {
                continue;
            }
            if (context.isHole(neighbourPos)) {
                return distance;
            }
            if (distance < this.getSlopeFindDistance(world)) {
                int next = this.getSlopeDistance(world, neighbourPos, distance + 1, direction.getOpposite(), neighbourState, context);
                if (next < best) {
                    best = next;
                }
            }
        }
        return best;
    }

    boolean isWaterHole(World world, BlockPos pos, IBlockState blockState, BlockPos belowPos, IBlockState belowState) {
        if (!canPassThroughWall(EnumFacing.DOWN, world, pos, blockState, belowPos, belowState)) {
            return false;
        }
        return FluidAccess.getFluidState(world, belowPos).getType().isSame(this)
                || canHoldFluid(world, belowPos, belowState, this.getFlowingFluid());
    }

    private boolean canPassThrough(World world, Fluid fluid, BlockPos pos, IBlockState blockState, EnumFacing direction,
            BlockPos neighbourPos, IBlockState neighbourState, FluidState neighbourFluid) {
        return this.canMaybePassThrough(world, pos, blockState, direction, neighbourPos, neighbourState, neighbourFluid)
                && canHoldSpecificFluid(world, neighbourPos, neighbourState, fluid);
    }

    private boolean canMaybePassThrough(World world, BlockPos pos, IBlockState blockState, EnumFacing direction,
            BlockPos neighbourPos, IBlockState neighbourState, FluidState neighbourFluid) {
        return !this.isSourceBlockOfThisType(neighbourFluid)
                && canHoldAnyFluid(neighbourState)
                && canPassThroughWall(direction, world, pos, blockState, neighbourPos, neighbourState);
    }

    private boolean isSourceBlockOfThisType(FluidState state) {
        return state.getType().isSame(this) && state.isSource();
    }

    private int sourceNeighborCount(World world, BlockPos pos) {
        int count = 0;
        for (EnumFacing direction : EnumFacing.Plane.HORIZONTAL) {
            if (this.isSourceBlockOfThisType(FluidAccess.getFluidState(world, pos.offset(direction)))) {
                count++;
            }
        }
        return count;
    }

    protected Map<EnumFacing, FluidState> getSpread(World world, BlockPos pos, IBlockState blockState) {
        int best = 1000;
        Map<EnumFacing, FluidState> spread = new EnumMap<>(EnumFacing.class);
        SpreadContext context = null;

        for (EnumFacing direction : EnumFacing.Plane.HORIZONTAL) {
            BlockPos neighbourPos = pos.offset(direction);
            IBlockState neighbourState = world.getBlockState(neighbourPos);
            FluidState neighbourFluid = FluidAccess.getFluidState(world, neighbourPos);
            if (!this.canMaybePassThrough(world, pos, blockState, direction, neighbourPos, neighbourState, neighbourFluid)) {
                continue;
            }
            FluidState newLiquid = this.getNewLiquid(world, neighbourPos, neighbourState);
            if (!canHoldSpecificFluid(world, neighbourPos, neighbourState, newLiquid.getType())) {
                continue;
            }
            if (context == null) {
                context = new SpreadContext(world, pos);
            }
            int distance;
            if (context.isHole(neighbourPos)) {
                distance = 0;
            } else {
                distance = this.getSlopeDistance(world, neighbourPos, 1, direction.getOpposite(), neighbourState, context);
            }
            if (distance < best) {
                spread.clear();
            }
            if (distance <= best) {
                if (neighbourFluid.canBeReplacedWith(world, neighbourPos, newLiquid.getType(), direction)) {
                    spread.put(direction, newLiquid);
                }
                best = distance;
            }
        }
        return spread;
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Port of {@code FlowingFluid.canPassThroughWall}. The modern game decides this from the two
     * blocks' collision shapes ({@code Shapes.mergedFaceOccludes}); the legacy {@code
     * getBlockFaceShape} approximation used before mis-handled partial blocks such as slabs, so it
     * is reproduced here with the legacy collision AABBs.
     */
    private static boolean canPassThroughWall(EnumFacing direction, IBlockAccess world, BlockPos pos1, IBlockState state1,
            BlockPos pos2, IBlockState state2) {
        AxisAlignedBB box1 = collisionBox(world, pos1, state1);
        AxisAlignedBB box2 = collisionBox(world, pos2, state2);
        if (isFullBox(box1) || isFullBox(box2)) {
            return false;
        }
        if (box1 == null && box2 == null) {
            return true;
        }
        return !mergedFaceOccludes(box1, box2, direction);
    }

    private static AxisAlignedBB collisionBox(IBlockAccess world, BlockPos pos, IBlockState state) {
        AxisAlignedBB box = state.getCollisionBoundingBox(world, pos);
        return box == null || box == Block.NULL_AABB ? null : box;
    }

    private static boolean isFullBox(AxisAlignedBB box) {
        return box != null
                && box.minX <= 0.0D && box.minY <= 0.0D && box.minZ <= 0.0D
                && box.maxX >= 1.0D && box.maxY >= 1.0D && box.maxZ >= 1.0D;
    }

    /**
     * Legacy counterpart of {@code Shapes.mergedFaceOccludes}: the face between the two shapes is
     * occluded only when together they cover the whole face plane.
     */
    private static boolean mergedFaceOccludes(AxisAlignedBB a, AxisAlignedBB b, EnumFacing direction) {
        if (isFullBox(a) || isFullBox(b)) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        EnumFacing.Axis axis = direction.getAxis();
        boolean positive = direction.getAxisDirection() == EnumFacing.AxisDirection.POSITIVE;
        AxisAlignedBB shapeA = positive ? a : b;
        AxisAlignedBB shapeB = positive ? b : a;
        if (axisMax(shapeA, axis) < 1.0D - OCCLUDE_EPSILON) {
            return false;
        }
        if (axisMin(shapeB, axis) > OCCLUDE_EPSILON) {
            return false;
        }
        return coversFace(shapeA, shapeB, axis);
    }

    private static boolean coversFace(AxisAlignedBB s1, AxisAlignedBB s2, EnumFacing.Axis axis) {
        double[] r1 = planeRect(s1, axis);
        double[] r2 = planeRect(s2, axis);
        double[] edges = {0.0D, 1.0D, r1[0], r1[1], r2[0], r2[1]};
        java.util.Arrays.sort(edges);
        for (int i = 0; i < edges.length - 1; i++) {
            double lo = edges[i];
            double hi = edges[i + 1];
            if (hi - lo <= OCCLUDE_EPSILON) {
                continue;
            }
            double mid = (lo + hi) * 0.5D;
            double vMin = Double.MAX_VALUE;
            double vMax = -Double.MAX_VALUE;
            if (mid >= r1[0] && mid <= r1[1]) {
                vMin = Math.min(vMin, r1[2]);
                vMax = Math.max(vMax, r1[3]);
            }
            if (mid >= r2[0] && mid <= r2[1]) {
                vMin = Math.min(vMin, r2[2]);
                vMax = Math.max(vMax, r2[3]);
            }
            if (vMin > OCCLUDE_EPSILON || vMax < 1.0D - OCCLUDE_EPSILON) {
                return false;
            }
        }
        return true;
    }

    private static double[] planeRect(AxisAlignedBB box, EnumFacing.Axis axis) {
        switch (axis) {
            case X:
                return new double[] {box.minY, box.maxY, box.minZ, box.maxZ};
            case Z:
                return new double[] {box.minX, box.maxX, box.minY, box.maxY};
            case Y:
            default:
                return new double[] {box.minX, box.maxX, box.minZ, box.maxZ};
        }
    }

    private static double axisMin(AxisAlignedBB box, EnumFacing.Axis axis) {
        switch (axis) {
            case X:
                return box.minX;
            case Y:
                return box.minY;
            case Z:
            default:
                return box.minZ;
        }
    }

    private static double axisMax(AxisAlignedBB box, EnumFacing.Axis axis) {
        switch (axis) {
            case X:
                return box.maxX;
            case Y:
                return box.maxY;
            case Z:
            default:
                return box.maxZ;
        }
    }

    private static boolean canHoldAnyFluid(IBlockState state) {
        Block block = state.getBlock();
        if (state.getMaterial().blocksMovement()) {
            return false;
        }
        return !(block instanceof BlockDoor)
                && block != Blocks.STANDING_SIGN
                && block != Blocks.WALL_SIGN
                && block != Blocks.LADDER
                && block != Blocks.REEDS
                && block != Blocks.PORTAL
                && block != Blocks.END_PORTAL
                && block != Blocks.END_GATEWAY
                && block != Blocks.STRUCTURE_VOID;
    }

    private static boolean canHoldFluid(World world, BlockPos pos, IBlockState state, Fluid fluid) {
        return canHoldAnyFluid(state) && canHoldSpecificFluid(world, pos, state, fluid);
    }

    private static boolean canHoldSpecificFluid(World world, BlockPos pos, IBlockState state, Fluid fluid) {
        return true;
    }

    protected int getSpreadDelay(World world, BlockPos pos, FluidState state, FluidState newState) {
        return this.getTickDelay(world);
    }

    // ------------------------------------------------------------------ tick

    @Override
    public void tick(World world, BlockPos pos, IBlockState blockState, FluidState state) {
        if (!state.isSource()) {
            FluidState newState = this.getNewLiquid(world, pos, world.getBlockState(pos));
            int delay = this.getSpreadDelay(world, pos, state, newState);
            if (newState.isEmpty()) {
                state = newState;
                blockState = Blocks.AIR.getDefaultState();
                world.setBlockState(pos, blockState, 3);
            } else if (!newState.equals(state)) {
                state = newState;
                blockState = newState.createLegacyBlock();
                world.setBlockState(pos, blockState, 3);
                world.scheduleUpdate(pos, blockState.getBlock(), delay);
            }
        }
        this.spread(world, pos, blockState, state);
    }

    @Override
    public float getHeight(FluidState state, IBlockAccess world, BlockPos pos) {
        return hasSameAbove(state, world, pos) ? 1.0F : state.getOwnHeight();
    }

    @Override
    public float getOwnHeight(FluidState state) {
        return (float) state.getAmount() / 9.0F;
    }

    private static boolean hasSameAbove(FluidState state, IBlockAccess world, BlockPos pos) {
        return state.getType().isSame(FluidAccess.getFluidState(world, pos.up()).getType());
    }

    /** Caches block states and hole lookups during a single spread evaluation. */
    protected final class SpreadContext {
        private final World world;
        private final BlockPos origin;
        private final Map<BlockPos, IBlockState> stateCache = new HashMap<>();
        private final Map<BlockPos, Boolean> holeCache = new HashMap<>();

        SpreadContext(World world, BlockPos origin) {
            this.world = world;
            this.origin = origin;
        }

        IBlockState getBlockState(BlockPos pos) {
            IBlockState cached = this.stateCache.get(pos);
            if (cached == null) {
                cached = this.world.getBlockState(pos);
                this.stateCache.put(pos.toImmutable(), cached);
            }
            return cached;
        }

        boolean isHole(BlockPos pos) {
            Boolean cached = this.holeCache.get(pos);
            if (cached != null) {
                return cached;
            }
            IBlockState state = this.getBlockState(pos);
            BlockPos below = pos.down();
            IBlockState belowState = this.world.getBlockState(below);
            boolean hole = FlowingFluid.this.isWaterHole(this.world, pos, state, below, belowState);
            this.holeCache.put(pos.toImmutable(), hole);
            return hole;
        }
    }
}
