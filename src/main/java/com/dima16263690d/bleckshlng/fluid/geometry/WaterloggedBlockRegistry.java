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
 * Java Edition 1.13.2 / Update Aquatic waterlogging set, mapped to the
 * existing Minecraft 1.12.2 vanilla block classes.
 *
 * Includes chests, trapped chests, ender chests, stairs, single slabs,
 * fences, walls, iron bars, glass panes, trapdoors, ladders and signs.
 * Doors and sugar cane are not waterloggable.
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
