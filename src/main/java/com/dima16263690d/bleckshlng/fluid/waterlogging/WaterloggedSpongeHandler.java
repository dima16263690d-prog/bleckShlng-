package com.dima16263690d.bleckshlng.fluid.waterlogging;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import com.dima16263690d.bleckshlng.fluid.geometry.FluidShapeCalculator;

/**
 * Java Edition 1.13.2 sponge absorption. Only actual water blocks are absorbed;
 * water stored inside a waterlogged host is deliberately not removed.
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
     * Absorbs connected vanilla water blocks. Waterlogged host blocks are
     * not removed and do not count as absorbable water.
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
                // In 1.13.2, sponges absorb water blocks but do not drain
                // water stored inside waterlogged blocks (MC-126914).
                if (state.getMaterial() != Material.WATER) {
                    continue;
                }

                world.setBlockState(next, Blocks.AIR.getDefaultState(), 2);
                FluidShapeCalculator.invalidate(world, next);
                changedPositions.add(next);
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
