package com.dima16263690d.bleckshlng.fluid.geometry;

import net.minecraft.block.Block;
import net.minecraft.block.BlockFence;
import net.minecraft.block.BlockChest;
import net.minecraft.block.BlockEnderChest;
import net.minecraft.block.BlockLadder;
import net.minecraft.block.BlockSign;
import net.minecraft.block.BlockPane;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.BlockTrapDoor;
import net.minecraft.block.BlockWall;

import com.dima16263690d.bleckshlng.fluid.waterlogging.IFluidWaterloggable;

/**
 * Explicit compatibility list for legacy vanilla blocks whose 1.18.2
 * counterparts expose a waterlogged state.
 *
 * Stairs, single slabs, fences, walls, trapdoors, glass panes and iron bars
 * are waterloggable. Signs and ladders are deliberately excluded: the
 * 1.18.2 FlowingFluid.canHoldFluid() rejects both, as it does doors.
 *
 * The 1.12.2 block state and metadata stay unchanged. Their water state is
 * stored separately by ChunkFluidData.
 */
public final class WaterloggedBlockRegistry {
    private WaterloggedBlockRegistry() {
    }

    public static boolean canWaterlog(Block block) {
        if (block == null) {
            return false;
        }

        // Extension point for mod blocks that provide their own adapter.
        if (block instanceof IFluidWaterloggable) {
            return true;
        }

        if (block instanceof BlockSlab) {
            return !((BlockSlab) block).isDouble();
        }

        // Exact Java Edition 1.13 / Update Aquatic waterlogging set:
        // chests, trapped chests, ender chests, stairs, single slabs,
        // fences, walls, trapdoors, ladders, signs, iron bars and panes.
        return block instanceof BlockChest
                || block instanceof BlockEnderChest
                || block instanceof BlockStairs
                || block instanceof BlockFence
                || block instanceof BlockWall
                || block instanceof BlockTrapDoor
                || block instanceof BlockLadder
                || block instanceof BlockSign
                || block instanceof BlockPane;
    }
}
