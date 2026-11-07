package com.dima16263690d.bleckshlng.client.render;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.dima16263690d.bleckshlng.fluid.state.FluidStateBridge;

/**
 * Bridge so FluidRenderHooks can call into the chunk mesher without depending
 * on private overlay helpers.
 */
@SideOnly(Side.CLIENT)
public final class WaterloggedFluidRendererChunkEntry {
    private WaterloggedFluidRendererChunkEntry() {
    }

    public static boolean renderIntoChunk(BufferBuilder buffer, World world,
                                          BlockPos pos, IBlockState host,
                                          FluidStateBridge fluid) {
        return WaterloggedFluidRenderer.renderIntoChunk(buffer, world, pos, host, fluid);
    }
}
