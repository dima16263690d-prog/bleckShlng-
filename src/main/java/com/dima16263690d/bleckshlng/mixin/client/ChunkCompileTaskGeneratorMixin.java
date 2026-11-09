package com.dima16263690d.bleckshlng.mixin.client;

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

import com.dima16263690d.bleckshlng.client.render.WaterloggedChunkRenderHooks;

/**
 * Starts the translucent buffer after the compile task receives its new
 * CompiledChunk. This avoids fragile ordinal-based injections inside
 * RenderChunk.rebuildChunk and ensures vanilla later finalizes the layer.
 */
@Mixin(value = ChunkCompileTaskGenerator.class, remap = false)
public abstract class ChunkCompileTaskGeneratorMixin {
    @Inject(
            method = {"setCompiledChunk", "func_178543_a"},
            at = @At("RETURN"),
            require = 1,
            remap = false
    )
    private void bleckshlng$startWaterloggedLayer(
            CompiledChunk compiledChunk,
            CallbackInfo callbackInfo) {
        ChunkCompileTaskGenerator generator =
                (ChunkCompileTaskGenerator) (Object) this;
        if (compiledChunk == null
                || generator.getType() != ChunkCompileTaskGenerator.Type.REBUILD_CHUNK) {
            return;
        }

        RenderChunk renderChunk = generator.getRenderChunk();
        if (!WaterloggedChunkRenderHooks.hasWaterloggedFluid(renderChunk)
                || compiledChunk.isLayerStarted(BlockRenderLayer.TRANSLUCENT)) {
            return;
        }

        BufferBuilder buffer = generator.getRegionRenderCacheBuilder()
                .getWorldRendererByLayer(BlockRenderLayer.TRANSLUCENT);
        if (buffer == null) {
            return;
        }

        compiledChunk.setLayerStarted(BlockRenderLayer.TRANSLUCENT);
        BlockPos origin = renderChunk.getPosition();
        buffer.begin(7, DefaultVertexFormats.BLOCK);
        buffer.setTranslation(
                -origin.getX(), -origin.getY(), -origin.getZ());
    }
}
