package com.dima16263690d.bleckshlng.client.render;

import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.lwjgl.opengl.GL11;

import com.dima16263690d.bleckshlng.fluid.geometry.FluidShapeCalculator;
import com.dima16263690d.bleckshlng.fluid.network.ClientFluidStateCache;
import com.dima16263690d.bleckshlng.fluid.state.FluidHeightCalculator;
import com.dima16263690d.bleckshlng.fluid.state.FluidStateBridge;

/**
 * Renders the visible water surface in the free volume of legacy waterlogged
 * blocks. The 4x4 surface mask follows the host collision boxes, so top-half
 * stairs, bottom slabs and fences do not get a solid full-block water overlay.
 *
 * This is a surface renderer; it does not replace vanilla fluid block models.
 */
@SideOnly(Side.CLIENT)
public final class WaterloggedFluidRenderer {
    private static final int CHUNK_RADIUS = 2;
    private static final double RENDER_DISTANCE_SQ = 40.0D * 40.0D;
    private static final int GRID = 4;

    private WaterloggedFluidRenderer() { }

    public static void render(float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();
        World world = mc.world;
        if (world == null || mc.getRenderViewEntity() == null) return;

        double cameraX = mc.getRenderViewEntity().lastTickPosX
                + (mc.getRenderViewEntity().posX - mc.getRenderViewEntity().lastTickPosX) * partialTicks;
        double cameraY = mc.getRenderViewEntity().lastTickPosY
                + (mc.getRenderViewEntity().posY - mc.getRenderViewEntity().lastTickPosY) * partialTicks;
        double cameraZ = mc.getRenderViewEntity().lastTickPosZ
                + (mc.getRenderViewEntity().posZ - mc.getRenderViewEntity().lastTickPosZ) * partialTicks;

        int centerChunkX = ((int) Math.floor(cameraX)) >> 4;
        int centerChunkZ = ((int) Math.floor(cameraZ)) >> 4;

        TextureAtlasSprite sprite = mc.getBlockRendererDispatcher()
                .getBlockModelShapes().getTexture(Blocks.WATER.getDefaultState());
        mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);

        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO);
        GlStateManager.disableCull();
        GlStateManager.depthMask(false);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK);
        boolean hasVertices = false;

        try {
            for (int chunkX = centerChunkX - CHUNK_RADIUS;
                 chunkX <= centerChunkX + CHUNK_RADIUS; chunkX++) {
                for (int chunkZ = centerChunkZ - CHUNK_RADIUS;
                     chunkZ <= centerChunkZ + CHUNK_RADIUS; chunkZ++) {
                    Map<Long, FluidStateBridge> states =
                            ClientFluidStateCache.copyChunk(
                                    world.provider.getDimension(), chunkX, chunkZ);

                    for (Map.Entry<Long, FluidStateBridge> entry : states.entrySet()) {
                        BlockPos pos = BlockPos.fromLong(entry.getKey().longValue());
                        double dx = pos.getX() + 0.5D - cameraX;
                        double dy = pos.getY() + 0.5D - cameraY;
                        double dz = pos.getZ() + 0.5D - cameraZ;
                        if (dx * dx + dy * dy + dz * dz > RENDER_DISTANCE_SQ
                                || !world.isBlockLoaded(pos)) continue;

                        IBlockState hostState = world.getBlockState(pos);
                        if (hostState.getBlock() == Blocks.AIR
                                || hostState.getBlock() instanceof BlockLiquid) continue;

                        float height = FluidHeightCalculator.getHeight(
                                world, pos, entry.getValue());
                        if (height <= 0.0F) continue;

                        FluidShapeCalculator.invalidate(world, pos);
                        double surfaceY = pos.getY() + (double) height - 0.002D;
                        hasVertices |= renderSurfaceMask(buffer, world, pos,
                                hostState, surfaceY, cameraX, cameraY, cameraZ, sprite);
                    }
                }
            }

            tessellator.draw();
        } finally {
            GlStateManager.depthMask(true);
            GlStateManager.enableCull();
            GlStateManager.disableBlend();
            GlStateManager.popMatrix();
        }
    }

    private static boolean renderSurfaceMask(BufferBuilder buffer, World world,
                                             BlockPos pos, IBlockState state,
                                             double surfaceY, double cameraX,
                                             double cameraY, double cameraZ,
                                             TextureAtlasSprite sprite) {
        boolean wrote = false;
        double minX = pos.getX();
        double minZ = pos.getZ();
        float uSpan = sprite.getMaxU() - sprite.getMinU();
        float vSpan = sprite.getMaxV() - sprite.getMinV();

        for (int x = 0; x < GRID; x++) {
            for (int z = 0; z < GRID; z++) {
                double centerX = minX + (x + 0.5D) / GRID;
                double centerZ = minZ + (z + 0.5D) / GRID;
                if (!FluidShapeCalculator.containsFluidPoint(
                        world, pos, state, centerX, surfaceY, centerZ,
                        (float) (surfaceY - pos.getY() + 0.002D))) continue;

                double x0 = minX + (double) x / GRID;
                double x1 = minX + (double) (x + 1) / GRID;
                double z0 = minZ + (double) z / GRID;
                double z1 = minZ + (double) (z + 1) / GRID;

                float u0 = sprite.getMinU() + (float) x / GRID * uSpan;
                float u1 = sprite.getMinU() + (float) (x + 1) / GRID * uSpan;
                float v0 = sprite.getMinV() + (float) z / GRID * vSpan;
                float v1 = sprite.getMinV() + (float) (z + 1) / GRID * vSpan;

                addVertex(buffer, x0-cameraX, surfaceY-cameraY, z0-cameraZ,
                        u0,v0,0.28F,0.52F,0.92F,0.62F);
                addVertex(buffer, x0-cameraX, surfaceY-cameraY, z1-cameraZ,
                        u0,v1,0.28F,0.52F,0.92F,0.62F);
                addVertex(buffer, x1-cameraX, surfaceY-cameraY, z1-cameraZ,
                        u1,v1,0.28F,0.52F,0.92F,0.62F);
                addVertex(buffer, x1-cameraX, surfaceY-cameraY, z0-cameraZ,
                        u1,v0,0.28F,0.52F,0.92F,0.62F);
                wrote = true;
            }
        }
        return wrote;
    }

    private static void addVertex(BufferBuilder buffer, double x, double y, double z,
                                  float u, float v, float red, float green,
                                  float blue, float alpha) {
        buffer.pos(x,y,z)
                .color(red,green,blue,alpha)
                .tex(u,v)
                .lightmap(240,240)
                .normal(0.0F,1.0F,0.0F)
                .endVertex();
    }
}
