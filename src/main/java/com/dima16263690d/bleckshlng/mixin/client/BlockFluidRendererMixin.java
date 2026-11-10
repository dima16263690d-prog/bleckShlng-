package com.dima16263690d.bleckshlng.mixin.client;

import com.dima16263690d.bleckshlng.client.render.WaterloggedChunkMesher;

import net.minecraft.block.BlockLiquid;
import net.minecraft.init.Blocks;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockFluidRenderer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import com.dima16263690d.bleckshlng.client.render.FluidViewAccess;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Replaces the 1.12.2 vanilla liquid mesh for vanilla water and lava with the
 * 1.18.2-compatible mesh generator. Fluid simulation and world states are not
 * changed by this client-side renderer hook.
 */
@Mixin(value = BlockFluidRenderer.class, remap = false)
public abstract class BlockFluidRendererMixin {
    @Inject(
            method = {"renderFluid", "func_178270_a"},
            at = @At("HEAD"),
            cancellable = true,
            require = 0,
            remap = false
    )
    private void bleckshlng$renderModernFluid(
            IBlockAccess access,
            IBlockState state,
            BlockPos pos,
            BufferBuilder buffer,
            CallbackInfoReturnable<Boolean> callback) {
        if (state == null || !(state.getBlock() instanceof BlockLiquid)) {
            return;
        }
        // Do not replace third-party BlockLiquid subclasses or Forge fluids.
        if (state.getBlock() != Blocks.WATER
                && state.getBlock() != Blocks.FLOWING_WATER
                && state.getBlock() != Blocks.LAVA
                && state.getBlock() != Blocks.FLOWING_LAVA) {
            return;
        }

        IBlockAccess renderAccess = access;
        if (!(access instanceof FluidViewAccess)) {
            World world = Minecraft.getMinecraft().world;
            if (world != null) {
                renderAccess = new FluidViewAccess(
                        access, world.provider.getDimension(), world);
            }
        }
        // Only cancel vanilla after the custom mesher actually emitted geometry.
        // A false result means that our visibility/occlusion pass produced no
        // vertices; vanilla must still get a chance to render the liquid.
        if (WaterloggedChunkMesher.tessellateFluid(renderAccess, state, pos, buffer)) {
            callback.setReturnValue(true);
        }
    }
}
