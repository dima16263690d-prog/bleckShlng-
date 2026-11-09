package com.dima16263690d.bleckshlng.mixin.client;

import com.dima16263690d.bleckshlng.client.render.WaterloggedChunkRenderHooks;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.chunk.ChunkCompileTaskGenerator;
import net.minecraft.client.renderer.chunk.CompiledChunk;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.math.BlockPos;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds the translucent layer before vanilla iterates the chunk's finished
 * layers, then emits waterlogged quads in postRenderBlocks before sorting and
 * upload. No ordinal-sensitive bytecode target is used.
 */
@Mixin(value = RenderChunk.class, remap = false)
public abstract class RenderChunkWaterloggedMixin {

    /**
     * Start the translucent buffer at the beginning of a rebuild whenever
     * this chunk has sidecar water. Vanilla will then naturally call
     * postRenderBlocks for this layer after its ordinary block traversal.
     */
    @Inject(
            method = {"rebuildChunk", "func_178581_b"},
            at = @At("HEAD"),
            require = 1,
            remap = false
    )
    private void bleckshlng$startWaterloggedLayer(
            float x,
            float y,
            float z,
            ChunkCompileTaskGenerator generator,
            CallbackInfo callbackInfo) {
        RenderChunk renderChunk = (RenderChunk) (Object) this;
        if (generator == null
                || !WaterloggedChunkRenderHooks.hasWaterloggedFluid(renderChunk)) {
            return;
        }

        CompiledChunk compiledChunk = generator.getCompiledChunk();
        if (compiledChunk == null) {
            return;
        }

        BlockRenderLayer layer = BlockRenderLayer.TRANSLUCENT;
        if (compiledChunk.isLayerStarted(layer)) {
            return;
        }

        BufferBuilder buffer = generator.getRegionRenderCacheBuilder()
                .getWorldRendererByLayer(layer);
        compiledChunk.setLayerStarted(layer);
        BlockPos origin = renderChunk.getPosition();
        buffer.begin(7, DefaultVertexFormats.BLOCK);
        buffer.setTranslation(
                -origin.getX(), -origin.getY(), -origin.getZ());
    }

    /**
     * This method is called by vanilla for every started layer. Starting the
     * translucent layer above makes this hook independent of whether ordinary
     * translucent blocks happen to exist in the chunk.
     */
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
                || compiledChunk == null || buffer == null) {
            return;
        }

        RenderChunk renderChunk = (RenderChunk) (Object) this;
        if (WaterloggedChunkRenderHooks.renderTranslucentLayer(
                renderChunk, layer, buffer)) {
            // setLayerUsed is protected in 1.12.2; use the cached MCP/SRG
            // reflective bridge, then vanilla sorts and records vertex state.
            WaterloggedChunkRenderHooks.markLayerUsed(compiledChunk, layer);
        }
    }
}
