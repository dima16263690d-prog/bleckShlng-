package com.dima16263690d.bleckshlng.client;

import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.dima16263690d.bleckshlng.BleckShlng;
import com.dima16263690d.bleckshlng.CommonProxy;
import com.dima16263690d.bleckshlng.fluid.network.ClientFluidStateCache;
import com.dima16263690d.bleckshlng.fluid.network.FluidStateSyncMessage;
import com.dima16263690d.bleckshlng.client.render.WaterloggedFluidRenderer;

/** Client proxy applies sync messages and client-only fluid rendering. */
@SideOnly(Side.CLIENT)
@Mod.EventBusSubscriber(modid = BleckShlng.MODID, value = Side.CLIENT)
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

    @SubscribeEvent
    public static void onRenderWorldLast(RenderWorldLastEvent event) {
        WaterloggedFluidRenderer.render(event.getPartialTicks());
    }
}
