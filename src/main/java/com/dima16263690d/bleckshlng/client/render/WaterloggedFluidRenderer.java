package com.dima16263690d.bleckshlng.client.render;

import java.util.Map;

import net.minecraft.block.BlockSlab;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.lwjgl.opengl.GL11;

import com.dima16263690d.bleckshlng.fluid.network.ClientFluidStateCache;
import com.dima16263690d.bleckshlng.fluid.state.FluidHeightCalculator;
import com.dima16263690d.bleckshlng.fluid.state.FluidStateBridge;

/**
 * Stable overlay for water stored in the sidecar (waterlogged half-slabs etc.).
 *
 * Unlike a full port of the 1.21 liquid mesher (not usable on 1.12 GL/TESSELLATOR),
 * this draws a simple water volume in the free half of the host block. Designed to
 * avoid the z-fighting flicker of the previous surface-only path when next to
 * vanilla water blocks.
 */
@SideOnly(Side.CLIENT)
public final class WaterloggedFluidRenderer {
    private static final int CHUNK_RADIUS = 3;
    private static final double RENDER_DISTANCE_SQ = 48.0D * 48.0D;
    /** Slight inset so quads do not z-fight neighbouring vanilla water faces. */
    private static final float INSET = 0.002F;

    private WaterloggedFluidRenderer() {
    }

    public static void render(float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();
        World world = mc.world;
        if (world == null || mc.getRenderViewEntity() == null) {
            return;
        }

        double cameraX = mc.getRenderViewEntity().lastTickPosX
                + (mc.getRenderViewEntity().posX - mc.getRenderViewEntity().lastTickPosX)
                * (double) partialTicks;
        double cameraY = mc.getRenderViewEntity().lastTickPosY
                + (mc.getRenderViewEntity().posY - mc.getRenderViewEntity().lastTickPosY)
                * (double) partialTicks;
        double cameraZ = mc.getRenderViewEntity().lastTickPosZ
                + (mc.getRenderViewEntity().posZ - mc.getRenderViewEntity().lastTickPosZ)
                * (double) partialTicks;

        int centerChunkX = ((int) Math.floor(cameraX)) >> 4;
        int centerChunkZ = ((int) Math.floor(cameraZ)) >> 4;
        int dimension = world.provider.getDimension();

        TextureAtlasSprite sprite = mc.getBlockRendererDispatcher()
                .getBlockModelShapes().getTexture(Blocks.WATER.getDefaultState());
        if (sprite == null) {
            return;
        }

        mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);

        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.tryBlendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO);
        GlStateManager.disableCull();
        GlStateManager.enableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.enablePolygonOffset();
        GlStateManager.doPolygonOffset(-1.0F, -10.0F);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK);

        boolean hasVertices = false;
        for (int cx = centerChunkX - CHUNK_RADIUS; cx <= centerChunkX + CHUNK_RADIUS; cx++) {
            for (int cz = centerChunkZ - CHUNK_RADIUS; cz <= centerChunkZ + CHUNK_RADIUS; cz++) {
                Map<BlockPos, FluidStateBridge> states =
                        ClientFluidStateCache.copyChunk(dimension, cx, cz);
                if (states == null || states.isEmpty()) {
                    continue;
                }
                for (Map.Entry<BlockPos, FluidStateBridge> entry : states.entrySet()) {
                    BlockPos pos = entry.getKey();
                    FluidStateBridge fluid = entry.getValue();
                    if (fluid == null || fluid.getKind() != FluidStateBridge.FluidKind.WATER) {
                        continue;
                    }
                    double dx = (pos.getX() + 0.5D) - cameraX;
                    double dy = (pos.getY() + 0.5D) - cameraY;
                    double dz = (pos.getZ() + 0.5D) - cameraZ;
                    if (dx * dx + dy * dy + dz * dz > RENDER_DISTANCE_SQ) {
                        continue;
                    }
                    if (!world.isBlockLoaded(pos)) {
                        continue;
                    }

                    IBlockState host = world.getBlockState(pos);
                    float fluidHeight = FluidHeightCalculator.getHeight(world, pos, fluid);
                    if (fluidHeight <= 0.05F) {
                        continue;
                    }

                    float minY = 0.0F;
                    float maxY = fluidHeight;
                    if (host.getBlock() instanceof BlockSlab
                            && !((BlockSlab) host.getBlock()).isDouble()) {
                        boolean topHalf = host.getValue(BlockSlab.HALF)
                                == BlockSlab.EnumBlockHalf.TOP;
                        if (topHalf) {
                            minY = 0.5F;
                            maxY = Math.min(1.0F, Math.max(minY + 0.05F, fluidHeight));
                        } else {
                            minY = 0.5F;
                            maxY = Math.max(minY + 0.05F, Math.min(1.0F, fluidHeight));
                            if (maxY <= minY) {
                                maxY = 0.8889F;
                            }
                        }
                    }

                    int color = 0x3F76E4;
                    try {
                        int tint = mc.getBlockColors().colorMultiplier(
                                Blocks.WATER.getDefaultState(), world, pos, 0);
                        if (tint != -1) {
                            color = tint;
                        }
                    } catch (Throwable ignored) {
                    }
                    int r = (color >> 16) & 0xFF;
                    int g = (color >> 8) & 0xFF;
                    int b = color & 0xFF;
                    int a = 180;

                    int packedLight = world.getCombinedLight(pos, 0);
                    int sky = (packedLight >> 16) & 0xFFFF;
                    int block = packedLight & 0xFFFF;

                    hasVertices |= putWaterBox(buffer, pos, minY, maxY,
                            cameraX, cameraY, cameraZ, sprite, r, g, b, a, sky, block);
                }
            }
        }

        if (hasVertices) {
            tessellator.draw();
        } else {
            buffer.finishDrawing();
        }

        GlStateManager.disablePolygonOffset();
        GlStateManager.depthMask(true);
        GlStateManager.enableCull();
        GlStateManager.enableAlpha();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    private static boolean putWaterBox(BufferBuilder buffer, BlockPos pos,
                                       float minY, float maxY,
                                       double cameraX, double cameraY, double cameraZ,
                                       TextureAtlasSprite sprite,
                                       int r, int g, int b, int a,
                                       int skyLight, int blockLight) {
        if (maxY - minY <= 0.01F) {
            return false;
        }

        double x0 = pos.getX() + (double) INSET - cameraX;
        double x1 = pos.getX() + 1.0D - (double) INSET - cameraX;
        double y0 = pos.getY() + (double) minY - cameraY;
        double y1 = pos.getY() + (double) maxY - (double) INSET - cameraY;
        double z0 = pos.getZ() + (double) INSET - cameraZ;
        double z1 = pos.getZ() + 1.0D - (double) INSET - cameraZ;

        float u0 = sprite.getMinU();
        float u1 = sprite.getMaxU();
        float v0 = sprite.getMinV();
        float v1 = sprite.getMaxV();

        vert(buffer, x0, y1, z0, u0, v0, r, g, b, a, skyLight, blockLight);
        vert(buffer, x0, y1, z1, u0, v1, r, g, b, a, skyLight, blockLight);
        vert(buffer, x1, y1, z1, u1, v1, r, g, b, a, skyLight, blockLight);
        vert(buffer, x1, y1, z0, u1, v0, r, g, b, a, skyLight, blockLight);

        vert(buffer, x0, y0, z0, u0, v0, r, g, b, a, skyLight, blockLight);
        vert(buffer, x1, y0, z0, u1, v0, r, g, b, a, skyLight, blockLight);
        vert(buffer, x1, y0, z1, u1, v1, r, g, b, a, skyLight, blockLight);
        vert(buffer, x0, y0, z1, u0, v1, r, g, b, a, skyLight, blockLight);

        vert(buffer, x0, y0, z0, u0, v1, r, g, b, a, skyLight, blockLight);
        vert(buffer, x0, y1, z0, u0, v0, r, g, b, a, skyLight, blockLight);
        vert(buffer, x1, y1, z0, u1, v0, r, g, b, a, skyLight, blockLight);
        vert(buffer, x1, y0, z0, u1, v1, r, g, b, a, skyLight, blockLight);

        vert(buffer, x1, y0, z1, u0, v1, r, g, b, a, skyLight, blockLight);
        vert(buffer, x1, y1, z1, u0, v0, r, g, b, a, skyLight, blockLight);
        vert(buffer, x0, y1, z1, u1, v0, r, g, b, a, skyLight, blockLight);
        vert(buffer, x0, y0, z1, u1, v1, r, g, b, a, skyLight, blockLight);

        vert(buffer, x0, y0, z1, u0, v1, r, g, b, a, skyLight, blockLight);
        vert(buffer, x0, y1, z1, u0, v0, r, g, b, a, skyLight, blockLight);
        vert(buffer, x0, y1, z0, u1, v0, r, g, b, a, skyLight, blockLight);
        vert(buffer, x0, y0, z0, u1, v1, r, g, b, a, skyLight, blockLight);

        vert(buffer, x1, y0, z0, u0, v1, r, g, b, a, skyLight, blockLight);
        vert(buffer, x1, y1, z0, u0, v0, r, g, b, a, skyLight, blockLight);
        vert(buffer, x1, y1, z1, u1, v0, r, g, b, a, skyLight, blockLight);
        vert(buffer, x1, y0, z1, u1, v1, r, g, b, a, skyLight, blockLight);

        return true;
    }

    private static void vert(BufferBuilder buffer,
                             double x, double y, double z,
                             float u, float v,
                             int r, int g, int b, int a,
                             int skyLight, int blockLight) {
        buffer.pos(x, y, z)
                .color(r, g, b, a)
                .tex((double) u, (double) v)
                .lightmap(skyLight, blockLight)
                .endVertex();
    }
}
