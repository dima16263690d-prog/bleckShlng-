package com.dima16263690d.bleckshlng.fluid.geometry;

import net.minecraft.block.Block;
import com.dima16263690d.bleckshlng.fluid.waterlogging.IFluidWaterloggable;
import com.dima16263690d.bleckshlng.fluid.waterlogging.IFluidWaterloggable;
import net.minecraft.block.BlockFence;
import net.minecraft.block.BlockLadder;
import net.minecraft.block.BlockPane;
import net.minecraft.block.BlockSign;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.BlockTrapDoor;
import net.minecraft.block.BlockWall;

/**
 * 1.12.2 does not have the Waterlogged property. This registry identifies
 * legacy vanilla blocks whose 1.18.2 counterparts can contain still water.
 *
 * The actual state is stored separately; the original block state and metadata
 * are never rewritten.
 */
public final class WaterloggedBlockRegistry {
    private WaterloggedBlockRegistry() {
    }

    public static boolean canWaterlog(Block block) {
        if (block == null) {
            return false;
        }

        if (block instanceof IFluidWaterloggable) {
            return true;
        }

        if (block instanceof BlockSlab) {
            return !((BlockSlab) block).isDouble();
        }

        return block instanceof BlockStairs
                || block instanceof BlockFence
                || block instanceof BlockWall
                || block instanceof BlockTrapDoor
                || block instanceof BlockLadder
                || block instanceof BlockSign
                || block instanceof BlockPane;
    }
}
