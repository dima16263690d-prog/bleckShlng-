package com.dima16263690d.bleckshlng.fluid.waterlogging;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import com.dima16263690d.bleckshlng.fluid.geometry.FluidShapeCalculator;
import com.dima16263690d.bleckshlng.fluid.geometry.WaterloggedBlockRegistry;
import com.dima16263690d.bleckshlng.fluid.state.FluidStateBridge;
import com.dima16263690d.bleckshlng.fluid.state.FluidStateMapper;

/**
 * Sponge absorption adapted to the 1.18.2 FluidState/BucketPickup model.
 * Ordinary liquid blocks are removed; water inside a waterloggable host is
 * picked up without destroying or replacing that host block.
 */
public final class WaterloggedSpongeHandler {
    private static final int MAX_DEPTH = 6;
    private static final int MAX_WATER_BLOCKS = 65;

    private WaterloggedSpongeHandler() {
    }

    private static final class Node {
        private final BlockPos pos;
        private final int depth;

        private Node(BlockPos pos, int depth) {
            this.pos = pos;
            this.depth = depth;
        }
    }

    /**
     * Absorbs connected water states. Ordinary liquid blocks are removed;
     * waterlogged hosts are drained without removing the host block.
     *
     * Returns true if at least one water position was absorbed. When true,
     * the transformer skips the original vanilla absorb method because this
     * routine has already applied its full bounded search.
     */
    public static boolean absorb(World world, BlockPos spongePos) {
        if (world == null || world.isRemote || spongePos == null
                || !world.isBlockLoaded(spongePos)) {
            return false;
        }

        ArrayDeque<Node> queue = new ArrayDeque<Node>();
        Set<Long> visited = new HashSet<Long>();
        List<BlockPos> changedPositions = new ArrayList<BlockPos>();
        int absorbed = 0;

        queue.addLast(new Node(spongePos, 0));
        visited.add(Long.valueOf(spongePos.toLong()));

        while (!queue.isEmpty() && absorbed < MAX_WATER_BLOCKS) {
            Node node = queue.removeFirst();

            for (EnumFacing side : EnumFacing.values()) {
                BlockPos next = node.pos.offset(side);
                if (!world.isBlockLoaded(next)) {
                    continue;
                }

                Long key = Long.valueOf(next.toLong());
                if (!visited.add(key)) {
                    continue;
                }

                IBlockState state = world.getBlockState(next);
                FluidStateBridge fluid = FluidStateMapper.fromWorld(
                        world, next, state);
                if (fluid == null
                        || fluid.getKind() != FluidStateBridge.FluidKind.WATER) {
                    continue;
                }

                boolean removed;
                boolean needsNeighborNotify = false;
                if (state.getBlock() instanceof net.minecraft.block.BlockLiquid) {
                    // A liquid block is removed directly, including flowing
                    // water, exactly as the LiquidBlock sponge path does.
                    removed = world.setBlockToAir(next);
                    if (removed) {
                        FluidShapeCalculator.invalidate(world, next);
                        needsNeighborNotify = true;
                    }
                } else if (WaterloggedBlockRegistry.canWaterlog(state.getBlock())) {
                    // 1.18.2 SimpleWaterloggedBlock implements BucketPickup:
                    // clear the stored fluid while preserving the host block.
                    // pickupWater already synchronizes and notifies neighbors.
                    removed = WaterloggedFluidHandler.pickupWater(world, next, state);
                } else {
                    removed = false;
                }

                if (!removed) {
                    continue;
                }

                if (needsNeighborNotify) {
                    changedPositions.add(next);
                }
                ++absorbed;

                if (node.depth < MAX_DEPTH) {
                    queue.addLast(new Node(next, node.depth + 1));
                }

                if (absorbed >= MAX_WATER_BLOCKS) {
                    break;
                }
            }
        }

        for (BlockPos changed : changedPositions) {
            world.notifyNeighborsOfStateChange(changed, Blocks.AIR, false);
        }

        return absorbed > 0;
    }
}
