package com.dima16263690d.bleckshlng.fluid.network;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

import com.dima16263690d.bleckshlng.fluid.state.FluidStateBridge;
import com.dima16263690d.bleckshlng.fluid.waterlogging.ChunkFluidData;

/** Network transport for waterlogged states; world changes remain server-owned. */
public final class FluidNetwork {
    private static final String CHANNEL_NAME = "bleckfluid";
    private static SimpleNetworkWrapper channel;
    private static boolean initialized;

    private FluidNetwork() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        channel = NetworkRegistry.INSTANCE.newSimpleChannel(CHANNEL_NAME);
        channel.registerMessage(FluidStateSyncMessage.Handler.class,
                FluidStateSyncMessage.class, 0, Side.CLIENT);
        initialized = true;
    }

    public static void syncState(World world, BlockPos pos, FluidStateBridge state) {
        send(world, pos, state, false);
    }

    public static void removeState(World world, BlockPos pos) {
        send(world, pos, null, true);
    }

    private static void send(World world, BlockPos pos,
                             FluidStateBridge state, boolean removed) {
        if (channel == null || world == null || world.isRemote || pos == null) {
            return;
        }

        FluidStateSyncMessage message = new FluidStateSyncMessage(
                world.provider.getDimension(), pos.toLong(), state, removed);
        NetworkRegistry.TargetPoint target = new NetworkRegistry.TargetPoint(
                world.provider.getDimension(), pos.getX() + 0.5D,
                pos.getY() + 0.5D, pos.getZ() + 0.5D, 128.0D);
        channel.sendToAllAround(message, target);
    }

    public static void syncChunkToPlayer(World world, Chunk chunk, EntityPlayerMP player) {
        if (world == null || chunk == null || player == null || world.isRemote || channel == null) {
            return;
        }
        ChunkFluidData data = ChunkFluidData.get(world);
        if (data == null) {
            return;
        }

        for (BlockPos pos : data.getPositionsInChunk(chunk.x, chunk.z)) {
            FluidStateBridge state = data.getWater(pos);
            if (state != null) {
                channel.sendTo(new FluidStateSyncMessage(
                        world.provider.getDimension(), pos.toLong(), state, false), player);
            }
        }
    }
}
