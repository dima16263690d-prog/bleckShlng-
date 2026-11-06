package com.dima16263690d.bleckshlng.client;

import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.dima16263690d.bleckshlng.CommonProxy;
import com.dima16263690d.bleckshlng.fluid.network.ClientFluidStateCache;
import com.dima16263690d.bleckshlng.fluid.network.FluidStateSyncMessage;

/** Client proxy applies sync messages on the Minecraft client thread. */
@SideOnly(Side.CLIENT)
public final class ClientProxy extends CommonProxy {
    @Override
    public void preInit(FMLPreInitializationEvent event) {
    }

    @Override
    public void handleFluidStateSync(final FluidStateSyncMessage message) {
        Minecraft.getMinecraft().addScheduledTask(new Runnable() {
            @Override
            public void run() {
                if (Minecraft.getMinecraft().world == null
                        || Minecraft.getMinecraft().world.provider.getDimension()
                        != message.dimension) {
                    return;
                }
                ClientFluidStateCache.apply(message.dimension, message.packedPosition,
                        message.createState(), message.removed);
            }
        });
    }
}
