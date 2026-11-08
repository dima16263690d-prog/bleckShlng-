package com.dima16263690d.bleckshlng.client;

import java.lang.reflect.Field;
import com.dima16263690d.bleckshlng.BleckShlng;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;

/**
 * Swaps the vanilla {@code BlockFluidRenderer} inside the client {@link BlockRendererDispatcher}
 * for the ported 1.21 {@link FluidRenderer}.
 *
 * <p>The replacement is attempted once the texture atlas is available (it is not during mod
 * pre-initialization), so it is deferred to the first client tick.</p>
 */
@Mod.EventBusSubscriber(modid = BleckShlng.MODID, value = Side.CLIENT)
public final class FluidRendererInstaller {
    private static boolean installed;

    private FluidRendererInstaller() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (installed || event.phase != TickEvent.Phase.START) {
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.getTextureMapBlocks() == null) {
            return;
        }
        BlockRendererDispatcher dispatcher = minecraft.getBlockRendererDispatcher();
        if (dispatcher == null) {
            return;
        }
        try {
            Field field = BlockRendererDispatcher.class.getDeclaredField("field_175025_e");
            field.setAccessible(true);
            field.set(dispatcher, new FluidRenderer(minecraft.getBlockColors()));
            installed = true;
            BleckShlng.LOGGER.info("Installed 1.21 fluid renderer");
        } catch (Exception e) {
            BleckShlng.LOGGER.error("Unable to install 1.21 fluid renderer", e);
            installed = true;
        }
    }
}
