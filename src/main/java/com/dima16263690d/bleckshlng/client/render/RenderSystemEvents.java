package com.dima16263690d.bleckshlng.client.render;

import com.dima16263690d.bleckshlng.BleckShlng;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;

/**
 * Hooks the render-thread queue into Forge's client render loop.
 */
@Mod.EventBusSubscriber(modid = BleckShlng.MODID, value = Side.CLIENT)
public final class RenderSystemEvents {
    private RenderSystemEvents() {
    }

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            RenderSystem112.initRenderThread();
            RenderSystem112.replayQueue();
        }
    }
}
