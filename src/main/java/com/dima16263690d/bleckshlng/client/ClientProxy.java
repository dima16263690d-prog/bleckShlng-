package com.dima16263690d.bleckshlng.client;

import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.dima16263690d.bleckshlng.BleckShlng;
import com.dima16263690d.bleckshlng.CommonProxy;
import com.dima16263690d.bleckshlng.fluid.network.ClientFluidStateCache;
import com.dima16263690d.bleckshlng.fluid.network.FluidStateSyncMessage;

/**
 * Client proxy: apply fluid sync and force chunk rebuild so the waterlogged
 * mesher (BlockRendererDispatcher hook) picks up new states.
 *
 * WorldLast overlay is intentionally disabled — fluid is in the chunk mesh.
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
                mc.world.markBlockRangeForRenderUpdate(
                        pos.getX() - 1, pos.getY() - 1, pos.getZ() - 1,
                        pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);
            }
        });
    }
}
