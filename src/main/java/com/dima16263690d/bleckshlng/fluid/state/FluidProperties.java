package com.dima16263690d.bleckshlng.fluid.state;

import net.minecraft.block.material.Material;
import net.minecraft.world.World;

/**
 * Fluid timing and decay follow the Java Edition 1.13.2 baseline.
 * Lava is intentionally a little faster and reaches one block farther in
 * ordinary dimensions, as requested; water keeps the 1.13.2 values.
 */
public final class FluidProperties {
    private FluidProperties() {
    }

    public static int tickRate(World world, Material material) {
        if (material == Material.WATER) {
            return 5;
        }

        if (material == Material.LAVA) {
            // 1.13.2: 30 ticks in ordinary dimensions, 10 in the Nether.
            // Small speed increase: 25 and 8 ticks respectively.
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

    /**
     * Computes the amount carried into the next horizontal block.
     *
     * Water and Nether lava keep the 1.13.2 one-level decay. Overworld/End
     * lava keeps its normal two-level decay, with a final one-level tail:
     * this extends the horizontal reach by one block without making the
     * flowing lava renewable or changing its source-conversion rules.
     */
    public static int nextHorizontalAmount(World world, Material material, int amount) {
        boolean ordinaryDimensionLava = material == Material.LAVA
                && (world == null || !world.provider.doesWaterVaporize());
        if (ordinaryDimensionLava && amount == 2) {
            return 1;
        }
        return amount - horizontalDecay(world, material);
    }
}
