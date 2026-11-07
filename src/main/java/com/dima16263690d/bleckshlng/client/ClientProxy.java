package com.dima16263690d.bleckshlng.client;

import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.dima16263690d.bleckshlng.BleckShlng;
import com.dima16263690d.bleckshlng.CommonProxy;
import com.dima16263690d.bleckshlng.client.render.WaterloggedFluidRenderer;
import com.dima16263690d.bleckshlng.fluid.network.ClientFluidStateCache;
import com.dima16263690d.bleckshlng.fluid.network.FluidStateSyncMessage;

/**
 * Client proxy: sync waterlogged states and draw them via WorldLast overlay.
 *
 * Chunk-mesher injection cannot put fluid into the TRANSLUCENT pass while the
 * host block is compiled in SOLID — so overlay remains the reliable path on 1.12.2.
 */
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
                Minecraft mc = Minecraft.getMinecraft();
                if (mc.world == null
                        || mc.world.provider.getDimension() != message.dimension) {
                    return;
                }
                ClientFluidStateCache.apply(message.dimension, message.packedPosition,
                        message.createState(), message.removed);
                BlockPos pos = BlockPos.fromLong(message.packedPosition);
                // Still mark for update so lighting / neighbor water refresh.
                mc.world.markBlockRangeForRenderUpdate(
                        pos.getX() - 1, pos.getY() - 1, pos.getZ() - 1,
                        pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);
            }
        });
    }

    @SubscribeEvent
    public static void onRenderWorldLast(RenderWorldLastEvent event) {
        WaterloggedFluidRenderer.render(event.getPartialTicks());
    }
}
