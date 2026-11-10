package com.dima16263690d.bleckshlng.client;

import net.minecraft.world.World;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

import com.dima16263690d.bleckshlng.fluid.network.ClientFluidStateCache;

/**
 * Drops client-side waterlogged state when its chunk or dimension unloads.
 * The cache is visual/read-only; the server remains authoritative.
 */
@Mod.EventBusSubscriber(modid = "bleckshlng", value = Side.CLIENT)
public final class FluidClientCacheEvents {
    private FluidClientCacheEvents() {
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        World world = event.getWorld();
        if (world == null || !world.isRemote) {
            return;
        }

        ClientFluidStateCache.clearChunk(world.provider.getDimension(),
                event.getChunk().x, event.getChunk().z);
    }

    @SubscribeEvent
    public static void onWorldUnload(WorldEvent.Unload event) {
        World world = event.getWorld();
        if (world != null && world.isRemote) {
            ClientFluidStateCache.clearDimension(world.provider.getDimension());
        }
    }
}
