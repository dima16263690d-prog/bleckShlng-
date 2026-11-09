package com.dima16263690d.bleckshlng.mixin.client;

import com.dima16263690d.bleckshlng.client.render.WaterloggedChunkRenderHooks;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.chunk.CompiledChunk;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.util.BlockRenderLayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Emits waterlogged quads immediately before vanilla sorts and finalizes the
 * translucent layer. The layer is started by ChunkCompileTaskGeneratorMixin
 * after the new CompiledChunk has been attached to the rebuild task.
 */
@Mixin(value = RenderChunk.class, remap = false)
public abstract class RenderChunkWaterloggedMixin {
    @Inject(
            method = {"postRenderBlocks", "func_178584_a"},
            at = @At("HEAD"),
            require = 1,
            remap = false
    )
    private void bleckshlng$renderWaterloggedFluid(
            BlockRenderLayer layer,
            float x,
            float y,
            float z,
            BufferBuilder buffer,
            CompiledChunk compiledChunk,
            CallbackInfo callbackInfo) {
        if (layer != BlockRenderLayer.TRANSLUCENT
                || compiledChunk == null || buffer == null
                || !WaterloggedChunkRenderHooks.isWaterloggedRebuildActive()) {
            return;
        }

        RenderChunk renderChunk = (RenderChunk) (Object) this;
        if (WaterloggedChunkRenderHooks.renderTranslucentLayer(
                renderChunk, layer, buffer)) {
            WaterloggedChunkRenderHooks.markLayerUsed(compiledChunk, layer);
        }
    }
    @Inject(
            method = {"rebuildChunk", "func_178581_b"},
            at = @At("RETURN"),
            require = 1,
            remap = false
    )
    private void bleckshlng$endWaterloggedRebuild(
            float x,
            float y,
            float z,
            net.minecraft.client.renderer.chunk.ChunkCompileTaskGenerator generator,
            CallbackInfo callbackInfo) {
        WaterloggedChunkRenderHooks.endWaterloggedRebuild();
    }

}
