package com.dima16263690d.bleckshlng.fluid.geometry;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import com.dima16263690d.bleckshlng.BleckShlng;

/**
 * Invalidates collision-shape cache entries when the world geometry changes.
 */
@Mod.EventBusSubscriber(modid = BleckShlng.MODID)
public final class FluidShapeEventHandler {
    private FluidShapeEventHandler() { }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.PlaceEvent event) {
        invalidateAround(event.getWorld(), event.getPos());
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        invalidateAround(event.getWorld(), event.getPos());
    }

    @SubscribeEvent
    public static void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
        invalidateAround(event.getWorld(), event.getPos());
    }

    private static void invalidateAround(World world, BlockPos pos) {
        if (world == null || pos == null) return;
        FluidShapeCalculator.invalidate(world, pos);
        for (EnumFacing direction : EnumFacing.values()) {
            BlockPos neighbor = pos.offset(direction);
            if (world.isBlockLoaded(neighbor)) {
                FluidShapeCalculator.invalidate(world, neighbor);
            }
        }
    }
}
