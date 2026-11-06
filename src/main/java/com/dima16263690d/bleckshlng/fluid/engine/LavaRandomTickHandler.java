package com.dima16263690d.bleckshlng.fluid.engine;

import java.util.Random;

import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Lava random-tick fire propagation adapted from 1.18.2 LavaFluid.randomTick().
 *
 * Called from flowing-lava scheduled updates at a rate calibrated to the
 * legacy lava tick period. The static vanilla lava block retains its own
 * native random tick and is not intercepted by this class.
 */
public final class LavaRandomTickHandler {
    private LavaRandomTickHandler() { }

    public static void randomTick(World world, BlockPos origin, Random random) {
        if (world == null || world.isRemote || origin == null || random == null
                || !world.getGameRules().getBoolean("doFireTick")
                || !world.isBlockLoaded(origin)) {
            return;
        }

        int attempts = random.nextInt(3);
        if (attempts > 0) {
            BlockPos cursor = origin;
            for (int i = 0; i < attempts; i++) {
                cursor = cursor.add(random.nextInt(3) - 1, 1,
                        random.nextInt(3) - 1);

                if (!world.isBlockLoaded(cursor)) {
                    return;
                }

                IBlockState target = world.getBlockState(cursor);
                if (target.getMaterial() == Material.AIR) {
                    if (hasFlammableNeighbours(world, cursor)) {
                        world.setBlockState(cursor, Blocks.FIRE.getDefaultState(), 3);
                        return;
                    }
                } else if (target.getMaterial().blocksMovement()) {
                    return;
                }
            }
        } else {
            for (int i = 0; i < 3; i++) {
                BlockPos candidate = origin.add(random.nextInt(3) - 1, 0,
                        random.nextInt(3) - 1);
                BlockPos firePos = candidate.up();
                if (!world.isBlockLoaded(candidate) || !world.isBlockLoaded(firePos)) {
                    return;
                }

                if (world.isAirBlock(firePos) && isFlammable(world, candidate)) {
                    world.setBlockState(firePos, Blocks.FIRE.getDefaultState(), 3);
                }
            }
        }
    }

    private static boolean hasFlammableNeighbours(World world, BlockPos pos) {
        for (EnumFacing direction : EnumFacing.values()) {
            if (!world.isBlockLoaded(pos.offset(direction))) {
                continue;
            }
            if (isFlammable(world, pos.offset(direction))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isFlammable(World world, BlockPos pos) {
        if (pos.getY() < 0 || pos.getY() >= 256 || !world.isBlockLoaded(pos)) {
            return false;
        }
        return world.getBlockState(pos).getMaterial().getCanBurn();
    }
}
