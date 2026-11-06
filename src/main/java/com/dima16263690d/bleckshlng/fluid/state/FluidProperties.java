package com.dima16263690d.bleckshlng.fluid.state;

import net.minecraft.block.material.Material;
import net.minecraft.world.World;

/**
 * Runtime properties adapted from the modern fluid definitions.
 * Timings retain vanilla 1.12.2 values for compatibility.
 */
public final class FluidProperties {
    private FluidProperties() {
    }

    public static int tickRate(World world, Material material) {
        if (material == Material.WATER) {
            return 5;
        }

        if (material == Material.LAVA) {
            // Slightly faster than the 1.18.2 baseline (30/10 ticks),
            // while preserving the Overworld/Nether distinction.
            return world != null && world.provider.doesWaterVaporize() ? 8 : 25;
        }

        return 5;
    }

    public static int slopeFindDistance(World world, Material material) {
        if (material == Material.LAVA
                && (world == null || !world.provider.doesWaterVaporize())) {
            return 2;
        }
        return 4;
    }

    public static int horizontalDecay(World world, Material material) {
        if (material == Material.LAVA
                && (world == null || !world.provider.doesWaterVaporize())) {
            return 2;
        }
        return 1;
    }
}
