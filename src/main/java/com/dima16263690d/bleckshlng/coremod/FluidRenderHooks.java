package com.dima16263690d.bleckshlng.coremod;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.dima16263690d.bleckshlng.client.render.WaterloggedChunkMesher;
import com.dima16263690d.bleckshlng.fluid.network.ClientFluidStateCache;
import com.dima16263690d.bleckshlng.fluid.state.FluidStateBridge;

/**
 * Client-side chunk mesh hook for waterlogged fluid.
 * Invoked from FluidRenderTransformer at the end of BlockRendererDispatcher.renderBlock.
 */
public final class FluidRenderHooks {
    private static final Logger LOGGER = LogManager.getLogger("BleckShlng/FluidRender");

    private FluidRenderHooks() {
    }

    public static void afterRenderBlock(IBlockState state, BlockPos pos,
                                        IBlockAccess access, BufferBuilder buffer) {
        try {
            if (state == null || pos == null || access == null || buffer == null) {
                return;
            }
            int dimension;
            if (access instanceof World) {
                World world = (World) access;
                if (!world.isRemote) {
                    return;
                }
                dimension = world.provider.getDimension();
            } else {
                Minecraft mc = Minecraft.getMinecraft();
                if (mc == null || mc.world == null) {
                    return;
                }
                dimension = mc.world.provider.getDimension();
            }

            FluidStateBridge fluid = ClientFluidStateCache.get(dimension, pos.toLong());
            if (fluid == null
                    || fluid.getKind() != FluidStateBridge.FluidKind.WATER) {
                return;
            }

            WaterloggedChunkMesher.tessellate(access, pos, fluid, buffer);
        } catch (Throwable error) {
            LOGGER.error("Waterlogged chunk mesh emit failed at {}", pos, error);
        }
    }
}
