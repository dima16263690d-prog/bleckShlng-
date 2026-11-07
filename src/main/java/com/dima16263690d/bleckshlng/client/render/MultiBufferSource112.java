package com.dima16263690d.bleckshlng.client.render;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.WorldVertexBufferUploader;

/**
 * Batched buffer source adapted to the single-active-state model used by
 * modern MultiBufferSource.BufferSource, backed by 1.12.2 BufferBuilder.
 */
public final class MultiBufferSource112 implements AutoCloseable {
    private static final int INITIAL_BUFFER_SIZE = 262144;

    private final Map<RenderType112, BufferEntry> buffers =
            new LinkedHashMap<RenderType112, BufferEntry>();
    private RenderType112 activeType;

    public VertexConsumer112 getBuffer(RenderType112 type, PoseStack112 poseStack) {
        if (type == null || poseStack == null) {
            throw new IllegalArgumentException("Render type and pose stack must not be null");
        }

        RenderSystem112.assertOnRenderThread();

        if (activeType != type) {
            if (activeType != null) {
                endBatch(activeType);
            }

            BufferEntry entry = getOrCreate(type);
            type.setupRenderState();
            entry.builder.begin(type.getDrawMode(), type.getVertexFormat());
            entry.drawing = true;
            activeType = type;
        }

        BufferEntry active = buffers.get(type);
        return new VertexConsumer112(active.builder, poseStack.last().copy());
    }

    public void endBatch() {
        if (activeType != null) {
            endBatch(activeType);
        }
    }

    public void endBatch(RenderType112 type) {
        RenderSystem112.assertOnRenderThread();

        if (type == null || activeType != type) {
            return;
        }

        BufferEntry entry = buffers.get(type);
        if (entry == null || !entry.drawing) {
            activeType = null;
            return;
        }

        try {
            entry.builder.finishDrawing();
            entry.uploader.draw(entry.builder);
        } finally {
            type.clearRenderState();
            entry.drawing = false;
            activeType = null;
        }
    }

    public boolean isBatchActive() {
        return activeType != null;
    }

    @Override
    public void close() {
        endBatch();
        buffers.clear();
    }

    private BufferEntry getOrCreate(RenderType112 type) {
        BufferEntry entry = buffers.get(type);
        if (entry == null) {
            entry = new BufferEntry();
            buffers.put(type, entry);
        }
        return entry;
    }

    private static final class BufferEntry {
        private final BufferBuilder builder = new BufferBuilder(INITIAL_BUFFER_SIZE);
        private final WorldVertexBufferUploader uploader = new WorldVertexBufferUploader();
        private boolean drawing;
    }
}
