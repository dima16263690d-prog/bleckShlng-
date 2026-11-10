package com.dima16263690d.bleckshlng.fluid.state;

import net.minecraft.block.material.Material;
import net.minecraft.world.World;

/**
 * Flow and timing parameters from Java Edition 1.18.2 FlowingFluid/LavaFluid.
 */
public final class FluidProperties {
    private FluidProperties() {
    }

    public static int tickRate(World world, Material material) {
        if (material == Material.WATER) {
            return 5;
        }
        if (material == Material.LAVA) {
            return isUltraWarm(world) ? 10 : 30;
        }
        return 5;
    }

    public static int slopeFindDistance(World world, Material material) {
        if (material == Material.LAVA) {
            return isUltraWarm(world) ? 4 : 2;
        }
        return 4;
    }

    public static int horizontalDecay(World world, Material material) {
        if (material == Material.LAVA) {
            return isUltraWarm(world) ? 1 : 2;
        }
        return 1;
    }

    /**
     * FlowingFluid.spreadToSides: amount minus the dimension-specific drop-off.
     * Falling fluids are promoted to amount 7 by the caller, not here.
     */
    public static int nextHorizontalAmount(World world, Material material, int amount) {
        return amount - horizontalDecay(world, material);
    }

    private static boolean isUltraWarm(World world) {
        return world != null && world.provider.doesWaterVaporize();
    }
}
