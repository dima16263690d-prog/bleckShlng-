package com.dima16263690d.bleckshlng.client.render;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;

/**
 * Vertex writer for the 1.12.2 BLOCK layout:
 * POSITION, COLOR, UV0, lightmap UV1.
 *
 * The old block vertex format does not contain the separate overlay and normal
 * attributes used by modern Minecraft. Those attributes are not silently
 * remapped here; lightmap coordinates remain explicit arguments.
 */
public final class VertexConsumer112 {
    private final BufferBuilder builder;
    private final PoseStack112.Pose pose;
    private final float[] transformedPosition = new float[3];

    VertexConsumer112(BufferBuilder builder, PoseStack112.Pose pose) {
        if (builder == null || pose == null) {
            throw new IllegalArgumentException("Builder and pose must not be null");
        }
        if (!DefaultVertexFormats.BLOCK.equals(builder.getVertexFormat())) {
            throw new IllegalArgumentException(
                    "VertexConsumer112 currently supports only DefaultVertexFormats.BLOCK");
        }

        this.builder = builder;
        this.pose = pose;
    }

    /**
     * Adds a transformed block vertex.
     *
     * @param skyLight and blockLight are the two 1.12.2 lightmap UV values
     */
    public VertexConsumer112 addVertex(
            float x, float y, float z,
            int red, int green, int blue, int alpha,
            float u, float v,
            int skyLight, int blockLight) {
        pose.transformPosition(x, y, z, transformedPosition);

        builder.pos(transformedPosition[0], transformedPosition[1], transformedPosition[2])
                .color(clampColor(red), clampColor(green), clampColor(blue), clampColor(alpha))
                .tex(u, v)
                .lightmap(skyLight, blockLight)
                .endVertex();

        return this;
    }

    /**
     * Adds a vertex using a packed 0xAARRGGBB color.
     */
    public VertexConsumer112 addVertex(
            float x, float y, float z,
            int argb,
            float u, float v,
            int skyLight, int blockLight) {
        return addVertex(
                x, y, z,
                (argb >> 16) & 255,
                (argb >> 8) & 255,
                argb & 255,
                (argb >>> 24) & 255,
                u, v, skyLight, blockLight);
    }

    private static int clampColor(int color) {
        return Math.max(0, Math.min(255, color));
    }
}
