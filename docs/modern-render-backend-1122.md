# Modern render backend for BleckShlng (Minecraft 1.12.2)

## Scope

This is a compatibility layer inspired by the rendering architecture present in
the mod-1.21 source tree. It is implemented against the actual Minecraft 1.12.2
and Forge 14.23.5.2864 classes listed in mc-1.12.2-source_files_Test.

It is deliberately **not** a replacement for the Minecraft renderer. BleckShlng
is a normal Forge mod, not a coremod: it cannot replace RenderGlobal,
RenderChunk, or ChunkRenderDispatcher merely by adding Java classes to its own
package. Existing blockstate JSON files and baked models therefore continue to
use the native 1.12.2 block-model pipeline.

## API mapping

| Modern 1.21 concept | BleckShlng 1.12.2 adaptation |
|---|---|
| RenderSystem render-thread ownership and deferred calls | RenderSystem112 |
| PoseStack | PoseStack112 |
| RenderType | RenderType112 |
| MultiBufferSource.BufferSource | MultiBufferSource112 |
| VertexConsumer | VertexConsumer112 |
| GPU vertex uploads | 1.12.2 BufferBuilder + WorldVertexBufferUploader |
| GL state | 1.12.2 GlStateManager |
| Framebuffer / post-process passes | Keep using 1.12.2 Framebuffer / ShaderGroup |

## Important 1.12.2 differences

The source version of DefaultVertexFormats.BLOCK contains position, color,
base UV and lightmap UV. It does not have the separate modern overlay-UV and
normal attributes. VertexConsumer112 therefore exposes explicit sky/block
lightmap values and does not pretend that a lightmap coordinate is an overlay.

The first buffer backend supports block-layout quads. Entity, particle, GUI,
shader-pack and post-processing layouts need separate adapters before they can
be used. The 1.21 GPU-buffer and shader classes are not copied because their
GPU API and dependency assumptions do not exist in Forge 1.12.2.

## Thread contract

Forge's RenderTickEvent initializes the render-thread owner and drains queued
work at the start of the render tick. Never call GlStateManager, bind textures,
or submit a buffer from a chunk worker or another background thread. Use
RenderSystem112.recordRenderCall(...) or RenderSystem112.execute(...) to
handoff GL work.

## Example

    PoseStack112 pose = new PoseStack112();
    pose.pushPose();
    pose.translate(0.5D, 0.5D, 0.5D);

    MultiBufferSource112 buffers = new MultiBufferSource112();
    VertexConsumer112 vertices = buffers.getBuffer(RenderType112.SOLID_BLOCKS, pose);

    vertices.addVertex(-0.5F, -0.5F, 0.0F, 255, 255, 255, 255,
            0.0F, 1.0F, 240, 0);
    vertices.addVertex( 0.5F, -0.5F, 0.0F, 255, 255, 255, 255,
            1.0F, 1.0F, 240, 0);
    vertices.addVertex( 0.5F,  0.5F, 0.0F, 255, 255, 255, 255,
            1.0F, 0.0F, 240, 0);
    vertices.addVertex(-0.5F,  0.5F, 0.0F, 255, 255, 255, 255,
            0.0F, 0.0F, 240, 0);

    buffers.endBatch();
    pose.popPose();

The caller must submit and flush custom geometry from a valid render callback.
The buffer source flushes the previous render type before switching GL state.

## Next migration stages

1. Add a dedicated entity/item vertex layout with normal and overlay handling.
2. Adapt particle and GUI buffer formats.
3. Add reusable VBO-backed storage using the 1.12.2 VertexBuffer implementation.
4. Only after those adapters are validated, consider a separate coremod project
   for chunk compilation, render graph and world-renderer replacement.

The current 12 stripped-wood blocks do not need a custom renderer: their
existing blockstate/model JSON is the correct and more compatible rendering
path for static cube geometry.
