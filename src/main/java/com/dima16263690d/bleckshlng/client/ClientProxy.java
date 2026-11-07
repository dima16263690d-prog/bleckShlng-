package com.dima16263690d.bleckshlng.client;

import java.lang.reflect.Field;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockFluidRenderer;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.dima16263690d.bleckshlng.BleckShlng;
import com.dima16263690d.bleckshlng.CommonProxy;
import com.dima16263690d.bleckshlng.client.render.ModernLiquidBlockRenderer112;
import com.dima16263690d.bleckshlng.client.render.WaterloggedFluidRenderer;
import com.dima16263690d.bleckshlng.fluid.network.ClientFluidStateCache;
import com.dima16263690d.bleckshlng.fluid.network.FluidStateSyncMessage;

/** Client proxy applies sync messages and client-only fluid rendering. */
@SideOnly(Side.CLIENT)
@Mod.EventBusSubscriber(modid = BleckShlng.MODID, value = Side.CLIENT)
public final class ClientProxy extends CommonProxy {
    private static boolean modernLiquidRendererInstalled;
    private static boolean modernLiquidRendererInstallFailed;

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

    /**
     * Install the modern surface algorithm into the existing vanilla dispatcher.
     * This keeps native liquid geometry in the normal chunk render pass.
     */
    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START
                || modernLiquidRendererInstalled
                || modernLiquidRendererInstallFailed) {
            return;
        }

        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null) {
            return;
        }

        BlockRendererDispatcher dispatcher = minecraft.getBlockRendererDispatcher();
        if (dispatcher == null) {
            return;
        }

        try {
            Field rendererField = findFluidRendererField();
            rendererField.setAccessible(true);

            Object currentRenderer = rendererField.get(dispatcher);
            if (currentRenderer instanceof ModernLiquidBlockRenderer112) {
                modernLiquidRendererInstalled = true;
                return;
            }

            ModernLiquidBlockRenderer112 modernRenderer =
                    new ModernLiquidBlockRenderer112(minecraft.getBlockColors());
            rendererField.set(dispatcher, modernRenderer);

            modernLiquidRendererInstalled = true;
            BleckShlng.LOGGER.info(
                    "Installed modern 1.21-style liquid surface renderer on Minecraft 1.12.2");
        } catch (Throwable error) {
            modernLiquidRendererInstallFailed = true;
            BleckShlng.LOGGER.error(
                    "Could not install modern liquid renderer; vanilla fluid renderer remains active",
                    error);
        }
    }

    private static Field findFluidRendererField() throws NoSuchFieldException {
        for (Field field : BlockRendererDispatcher.class.getDeclaredFields()) {
            if (BlockFluidRenderer.class.isAssignableFrom(field.getType())) {
                return field;
            }
        }

        throw new NoSuchFieldException(
                "BlockRendererDispatcher has no BlockFluidRenderer field");
    }

    @SubscribeEvent
    public static void onRenderWorldLast(RenderWorldLastEvent event) {
        // Keep the existing sidecar path for water stored inside legacy blocks.
        WaterloggedFluidRenderer.render(event.getPartialTicks());
    }
}
