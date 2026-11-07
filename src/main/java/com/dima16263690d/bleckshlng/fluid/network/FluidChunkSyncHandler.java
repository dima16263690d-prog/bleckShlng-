package com.dima16263690d.bleckshlng.fluid.network;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.event.world.ChunkWatchEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import com.dima16263690d.bleckshlng.BleckShlng;

/**
 * Pushes already-stored waterlogged states to a player when their client
 * starts watching a chunk. Without this, ClientFluidStateCache stays empty
 * after login / teleport and WaterloggedFluidRenderer draws nothing.
 */
@Mod.EventBusSubscriber(modid = BleckShlng.MODID)
public final class FluidChunkSyncHandler {
    private FluidChunkSyncHandler() {
    }

    @SubscribeEvent
    public static void onChunkWatch(ChunkWatchEvent.Watch event) {
        EntityPlayerMP player = event.getPlayer();
        if (player == null) {
            return;
        }
        World world = player.world;
        if (world == null || world.isRemote) {
            return;
        }

        ChunkPos chunkPos = event.getChunk();
        if (chunkPos == null) {
            return;
        }

        Chunk chunk = world.getChunkProvider().getLoadedChunk(chunkPos.x, chunkPos.z);
        if (chunk == null) {
            return;
        }

        FluidNetwork.syncChunkToPlayer(world, chunk, player);
    }
}
