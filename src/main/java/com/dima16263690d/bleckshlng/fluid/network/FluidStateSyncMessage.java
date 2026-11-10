package com.dima16263690d.bleckshlng.fluid.network;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.dima16263690d.bleckshlng.BleckShlng;
import com.dima16263690d.bleckshlng.fluid.state.FluidStateBridge;

/** Server-to-client sync for fluid stored in blocks without native fluid metadata. */
public final class FluidStateSyncMessage implements IMessage {
    public int dimension;
    public long packedPosition;
    public boolean removed;
    public int fluidKind;
    public int amount;
    public boolean falling;
    public boolean source;

    public FluidStateSyncMessage() {
    }

    public FluidStateSyncMessage(int dimension, long packedPosition,
                                 FluidStateBridge state, boolean removed) {
        this.dimension = dimension;
        this.packedPosition = packedPosition;
        this.removed = removed;
        if (state != null) {
            this.fluidKind = state.getKind().ordinal();
            this.amount = state.getAmount();
            this.falling = state.isFalling();
            this.source = state.isSource();
        }
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        dimension = buffer.readInt();
        packedPosition = buffer.readLong();
        removed = buffer.readBoolean();
        fluidKind = buffer.readUnsignedByte();
        amount = buffer.readUnsignedByte();
        falling = buffer.readBoolean();
        source = buffer.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeInt(dimension);
        buffer.writeLong(packedPosition);
        buffer.writeBoolean(removed);
        buffer.writeByte(fluidKind);
        buffer.writeByte(amount);
        buffer.writeBoolean(falling);
        buffer.writeBoolean(source);
    }

    public FluidStateBridge createState() {
        FluidStateBridge.FluidKind[] kinds = FluidStateBridge.FluidKind.values();
        if (fluidKind < 0 || fluidKind >= kinds.length) {
            return null;
        }
        if (source) {
            return FluidStateBridge.source(kinds[fluidKind]);
        }
        return FluidStateBridge.flowing(kinds[fluidKind], amount, falling);
    }

    public static final class Handler
            implements IMessageHandler<FluidStateSyncMessage, IMessage> {
        @Override
        public IMessage onMessage(final FluidStateSyncMessage message, MessageContext context) {
            BleckShlng.PROXY.handleFluidStateSync(message);
            return null;
        }
    }
}
