package com.dima16263690d.bleckshlng.mixin.client;

import com.dima16263690d.bleckshlng.client.render.WaterloggedChunkRenderHooks;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.chunk.ChunkCompileTaskGenerator;
import net.minecraft.client.renderer.chunk.CompiledChunk;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.util.BlockRenderLayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds waterlogged fluid to the normal translucent pass when vanilla started
 * that layer. For chunks with no vanilla translucent blocks, a guarded
 * fallback builds the layer after vanilla's traversal has finished.
 *
 * No mixin starts a BufferBuilder before vanilla begins its own render pass.
 */
@Mixin(value = RenderChunk.class, remap = false)
public abstract class RenderChunkWaterloggedMixin {
    @Inject(
            method = {"postRenderBlocks", "func_178584_a"},
            at = @At("HEAD"),
            require = 0,
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
                || compiledChunk == null || buffer == null) {
            return;
        }

        RenderChunk renderChunk = (RenderChunk) (Object) this;
        if (!WaterloggedChunkRenderHooks.hasWaterloggedFluid(renderChunk)) {
            return;
        }

        if (WaterloggedChunkRenderHooks.renderTranslucentLayer(
                renderChunk, layer, buffer)) {
            WaterloggedChunkRenderHooks.markLayerUsed(compiledChunk, layer);
        }
    }

    /**
     * Handles chunks that contain only waterlogged solids and therefore never
     * entered the vanilla TRANSLUCENT pass. The fallback runs after all normal
     * layers were finalized but before ChunkRenderWorker uploads the mesh.
     */
    @Inject(
            method = {"rebuildChunk", "func_178581_b"},
            at = @At("RETURN"),
            require = 0,
            remap = false
    )
    private void bleckshlng$renderWaterloggedOnlyLayer(
            float x,
            float y,
            float z,
            ChunkCompileTaskGenerator generator,
            CallbackInfo callbackInfo) {
        WaterloggedChunkRenderHooks.renderMissingTranslucentLayer(
                (RenderChunk) (Object) this, generator, x, y, z);
    }
}
