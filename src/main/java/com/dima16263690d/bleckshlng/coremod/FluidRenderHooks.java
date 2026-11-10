package com.dima16263690d.bleckshlng.coremod;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

/**
 * Legacy BlockRendererDispatcher hook retained for binary compatibility.
 * Vanilla water/lava are now replaced at BlockFluidRenderer.renderFluid by
 * BlockFluidRendererMixin; waterlogged host states are meshed separately in
 * the translucent chunk layer.
 */
public final class FluidRenderHooks {
    private FluidRenderHooks() {
    }

    public static void afterRenderBlock(IBlockState state, BlockPos pos,
                                        IBlockAccess access, BufferBuilder buffer) {
        // Intentionally empty: liquid geometry is owned by the modern mesher.
    }
}
