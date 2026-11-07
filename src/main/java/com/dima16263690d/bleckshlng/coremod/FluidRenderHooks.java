package com.dima16263690d.bleckshlng.coremod;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

/**
 * Intentionally a no-op.
 *
 * Emitting fluid into the SOLID block pass (BlockRendererDispatcher.renderBlock)
 * double-draws against the WorldLast overlay and puts translucent water into
 * the wrong render layer — that is the solid blue "box" around ladders/slabs.
 *
 * Waterlogged fluid is drawn only by WaterloggedFluidRenderer (WorldLast).
 */
public final class FluidRenderHooks {
    private FluidRenderHooks() {
    }

    public static void afterRenderBlock(IBlockState state, BlockPos pos,
                                        IBlockAccess access, BufferBuilder buffer) {
        // no-op: see class comment
    }
}
