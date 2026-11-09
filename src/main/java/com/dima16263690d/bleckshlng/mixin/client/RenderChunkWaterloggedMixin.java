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
 * Ensures waterlogged fluid can contribute to a chunk even if vanilla did not
 * start the TRANSLUCENT layer for any ordinary block.
 *
 * The injection is placed immediately before RenderChunk's loop that finalizes
 * the render layers, after vanilla has traversed all 16x16x16 block positions.
 */
@Mixin(value = RenderChunk.class, remap = false)
public abstract class RenderChunkWaterloggedMixin {
    @Inject(
            method = {"rebuildChunk", "func_178581_b"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/BlockRenderLayer;values()[Lnet/minecraft/util/BlockRenderLayer;",
                    ordinal = 1
            ),
            require = 1,
            remap = false
    )
    private void bleckshlng$renderWaterloggedFluid(
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
        BufferBuilder buffer = generator.getRegionRenderCacheBuilder()
                .getWorldRendererByLayer(layer);

        if (!compiledChunk.isLayerStarted(layer)) {
            compiledChunk.setLayerStarted(layer);
            BlockPos origin = renderChunk.getPosition();
            buffer.begin(7, DefaultVertexFormats.BLOCK);
            buffer.setTranslation(
                    -origin.getX(), -origin.getY(), -origin.getZ());
        }

        if (WaterloggedChunkRenderHooks.renderTranslucentLayer(
                renderChunk, layer, buffer)) {
            WaterloggedChunkRenderHooks.markLayerUsed(compiledChunk, layer);
        }
    }
}
