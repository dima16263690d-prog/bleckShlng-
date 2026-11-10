package com.dima16263690d.bleckshlng.fluid.geometry;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import com.dima16263690d.bleckshlng.fluid.api.IFluidImpermeable;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

/** 1.12.2 compatibility bridge for the modern #minecraft:impermeable tag. */
public final class FluidImpermeableRegistry {
    private static final Set<Block> BLOCKS = Collections.newSetFromMap(
            new IdentityHashMap<Block, Boolean>());

    private FluidImpermeableRegistry() { }

    public static synchronized void register(Block block) {
        if (block != null) BLOCKS.add(block);
    }

    public static synchronized void unregister(Block block) {
        if (block != null) BLOCKS.remove(block);
    }

    public static boolean isImpermeable(
            IBlockState state, IBlockAccess world, BlockPos pos) {
        if (state == null) return false;
        if (state.getBlock() instanceof IFluidImpermeable) {
            return ((IFluidImpermeable) state.getBlock())
                    .isFluidImpermeable(state, world, pos);
        }
        synchronized (FluidImpermeableRegistry.class) {
            if (BLOCKS.contains(state.getBlock())) return true;
        }
        // Compare registry IDs instead of touching net.minecraft.init.Blocks.
        // This also keeps headless Forge unit tests from forcing Minecraft's
        // global bootstrap while preserving the 1.18.2 tag membership.
        return isVanillaTagMember(state.getBlock().getRegistryName());
    }

    static boolean isVanillaTagMember(ResourceLocation id) {
        if (id == null || !"minecraft".equals(id.getNamespace())) return false;
        String path = id.getPath();
        return "glass".equals(path) || "stained_glass".equals(path);
    }
}
