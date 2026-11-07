package com.dima16263690d.bleckshlng.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/**
 * Small RenderType equivalent for Minecraft 1.12.2.
 *
 * This first backend intentionally targets the BLOCK vertex layout used by
 * custom world geometry. Vanilla block models continue through Forge's renderer.
 */
public final class RenderType112 {
    public enum BlendMode {
        OPAQUE,
        ALPHA,
        ADDITIVE
    }

    public static final RenderType112 SOLID_BLOCKS = new RenderType112(
            "solid_blocks", TextureMap.LOCATION_BLOCKS_TEXTURE,
            BlendMode.OPAQUE, true, true, 0.1F);

    public static final RenderType112 CUTOUT_BLOCKS = new RenderType112(
            "cutout_blocks", TextureMap.LOCATION_BLOCKS_TEXTURE,
            BlendMode.OPAQUE, true, true, 0.1F);

    public static final RenderType112 TRANSLUCENT_BLOCKS = new RenderType112(
            "translucent_blocks", TextureMap.LOCATION_BLOCKS_TEXTURE,
            BlendMode.ALPHA, true, false, 0.0039215689F);

    public static final RenderType112 ADDITIVE_BLOCKS = new RenderType112(
            "additive_blocks", TextureMap.LOCATION_BLOCKS_TEXTURE,
            BlendMode.ADDITIVE, false, false, 0.0039215689F);

    private final String name;
    private final ResourceLocation texture;
    private final BlendMode blendMode;
    private final boolean cull;
    private final boolean depthWrite;
    private final float alphaThreshold;

    private RenderType112(
            String name,
            ResourceLocation texture,
            BlendMode blendMode,
            boolean cull,
            boolean depthWrite,
            float alphaThreshold) {
        this.name = name;
        this.texture = texture;
        this.blendMode = blendMode;
        this.cull = cull;
        this.depthWrite = depthWrite;
        this.alphaThreshold = alphaThreshold;
    }

    public static RenderType112 create(
            String name,
            ResourceLocation texture,
            BlendMode blendMode,
            boolean cull,
            boolean depthWrite) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Render type name must not be empty");
        }
        if (blendMode == null) {
            throw new IllegalArgumentException("Blend mode must not be null");
        }

        float threshold = blendMode == BlendMode.OPAQUE ? 0.1F : 0.0039215689F;
        return new RenderType112(name, texture, blendMode, cull, depthWrite, threshold);
    }

    public String getName() {
        return name;
    }

    VertexFormat getVertexFormat() {
        return DefaultVertexFormats.BLOCK;
    }

    int getDrawMode() {
        return GL11.GL_QUADS;
    }

    void setupRenderState() {
        RenderSystem112.assertOnRenderThread();

        GlStateManager.enableTexture2D();
        if (texture != null) {
            Minecraft.getMinecraft().getTextureManager().bindTexture(texture);
        }

        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(GL11.GL_GREATER, alphaThreshold);

        if (blendMode == BlendMode.OPAQUE) {
            GlStateManager.disableBlend();
        } else {
            GlStateManager.enableBlend();
            if (blendMode == BlendMode.ALPHA) {
                GlStateManager.tryBlendFuncSeparate(
                        GL11.GL_SRC_ALPHA,
                        GL11.GL_ONE_MINUS_SRC_ALPHA,
                        GL11.GL_ONE,
                        GL11.GL_ZERO);
            } else {
                GlStateManager.tryBlendFuncSeparate(
                        GL11.GL_SRC_ALPHA,
                        GL11.GL_ONE,
                        GL11.GL_ONE,
                        GL11.GL_ZERO);
            }
        }

        if (cull) {
            GlStateManager.enableCull();
        } else {
            GlStateManager.disableCull();
        }

        GlStateManager.depthMask(depthWrite);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    void clearRenderState() {
        RenderSystem112.assertOnRenderThread();

        GlStateManager.depthMask(true);
        GlStateManager.disableBlend();
        GlStateManager.enableCull();
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @Override
    public String toString() {
        return "RenderType112[" + name + "]";
    }
}
