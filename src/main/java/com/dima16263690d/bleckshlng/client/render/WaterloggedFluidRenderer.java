package com.dima16263690d.bleckshlng.client.render;

import java.util.Map;

import net.minecraft.block.BlockLiquid;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.lwjgl.opengl.GL11;

import com.dima16263690d.bleckshlng.fluid.network.ClientFluidStateCache;
import com.dima16263690d.bleckshlng.fluid.state.FluidHeightCalculator;
import com.dima16263690d.bleckshlng.fluid.state.FluidStateBridge;
import com.dima16263690d.bleckshlng.fluid.state.FluidStateMapper;

/**
 * Overlay for sidecar waterlogged fluid.
 *
 * Draws only faces that are not hidden by neighbouring water so ladders/fences
 * underwater no longer look like solid blue cubes, and ocean waterlogged cells
 * blend with the surrounding water surface.
 */
@SideOnly(Side.CLIENT)
public final class WaterloggedFluidRenderer {
    private static final int CHUNK_RADIUS = 3;
    private static final double RENDER_DISTANCE_SQ = 48.0D * 48.0D;
    private static final float INSET = 0.0015F;

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
                Map<Long, FluidStateBridge> states =
                        ClientFluidStateCache.copyChunk(dimension, cx, cz);
                if (states == null || states.isEmpty()) {
                    continue;
                }
                for (Map.Entry<Long, FluidStateBridge> entry : states.entrySet()) {
                    if (entry.getKey() == null) {
                        continue;
                    }
                    BlockPos pos = BlockPos.fromLong(entry.getKey().longValue());
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
                        fluidHeight = 0.8889F;
                    }

                    float minY = 0.0F;
                    float maxY = Math.min(1.0F, fluidHeight);
                    if (host.getBlock() instanceof BlockSlab
                            && !((BlockSlab) host.getBlock()).isDouble()) {
                        boolean topHalf = host.getValue(BlockSlab.HALF)
                                == BlockSlab.EnumBlockHalf.TOP;
                        if (topHalf) {
                            minY = 0.5F;
                            maxY = 1.0F;
                        } else {
                            // Bottom slab: water sits in the upper half of the cell
                            // (same look as 1.13 waterlogged bottom slab).
                            minY = 0.5F;
                            maxY = Math.max(0.8889F, maxY);
                            if (maxY > 1.0F) {
                                maxY = 1.0F;
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
                    // More transparent so host model (ladder, fence, slab) stays visible.
                    int a = 120;

                    int packedLight = world.getCombinedLight(pos, 0);
                    int sky = (packedLight >> 16) & 0xFFFF;
                    int block = packedLight & 0xFFFF;

                    hasVertices |= putVisibleFaces(buffer, world, pos, minY, maxY,
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

    /**
     * Emits only faces that are not covered by neighbouring water (vanilla block
     * or another waterlogged cell). This removes the solid-cube look underwater.
     */
    private static boolean putVisibleFaces(BufferBuilder buffer, World world,
                                           BlockPos pos, float minY, float maxY,
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

        boolean wrote = false;

        // UP — hide only if a full water cell sits above and our surface is at ceiling.
        if (maxY >= 0.999F && isWaterNeighbor(world, pos.up())) {
            // fully submerged top
        } else if (!isWaterNeighbor(world, pos.up()) || maxY < 0.95F) {
            vert(buffer, x0, y1, z0, u0, v0, r, g, b, a, skyLight, blockLight);
            vert(buffer, x0, y1, z1, u0, v1, r, g, b, a, skyLight, blockLight);
            vert(buffer, x1, y1, z1, u1, v1, r, g, b, a, skyLight, blockLight);
            vert(buffer, x1, y1, z0, u1, v0, r, g, b, a, skyLight, blockLight);
            wrote = true;
        }

        // DOWN
        if (minY <= 0.001F && isWaterNeighbor(world, pos.down())) {
            // hide
        } else {
            vert(buffer, x0, y0, z0, u0, v0, r, g, b, a, skyLight, blockLight);
            vert(buffer, x1, y0, z0, u1, v0, r, g, b, a, skyLight, blockLight);
            vert(buffer, x1, y0, z1, u1, v1, r, g, b, a, skyLight, blockLight);
            vert(buffer, x0, y0, z1, u0, v1, r, g, b, a, skyLight, blockLight);
            wrote = true;
        }

        // Horizontal sides — skip when neighbour is water at overlapping height.
        if (!isWaterNeighbor(world, pos.north())) {
            vert(buffer, x0, y0, z0, u0, v1, r, g, b, a, skyLight, blockLight);
            vert(buffer, x0, y1, z0, u0, v0, r, g, b, a, skyLight, blockLight);
            vert(buffer, x1, y1, z0, u1, v0, r, g, b, a, skyLight, blockLight);
            vert(buffer, x1, y0, z0, u1, v1, r, g, b, a, skyLight, blockLight);
            wrote = true;
        }
        if (!isWaterNeighbor(world, pos.south())) {
            vert(buffer, x1, y0, z1, u0, v1, r, g, b, a, skyLight, blockLight);
            vert(buffer, x1, y1, z1, u0, v0, r, g, b, a, skyLight, blockLight);
            vert(buffer, x0, y1, z1, u1, v0, r, g, b, a, skyLight, blockLight);
            vert(buffer, x0, y0, z1, u1, v1, r, g, b, a, skyLight, blockLight);
            wrote = true;
        }
        if (!isWaterNeighbor(world, pos.west())) {
            vert(buffer, x0, y0, z1, u0, v1, r, g, b, a, skyLight, blockLight);
            vert(buffer, x0, y1, z1, u0, v0, r, g, b, a, skyLight, blockLight);
            vert(buffer, x0, y1, z0, u1, v0, r, g, b, a, skyLight, blockLight);
            vert(buffer, x0, y0, z0, u1, v1, r, g, b, a, skyLight, blockLight);
            wrote = true;
        }
        if (!isWaterNeighbor(world, pos.east())) {
            vert(buffer, x1, y0, z0, u0, v1, r, g, b, a, skyLight, blockLight);
            vert(buffer, x1, y1, z0, u0, v0, r, g, b, a, skyLight, blockLight);
            vert(buffer, x1, y1, z1, u1, v0, r, g, b, a, skyLight, blockLight);
            vert(buffer, x1, y0, z1, u1, v1, r, g, b, a, skyLight, blockLight);
            wrote = true;
        }

        return wrote;
    }

    private static boolean isWaterNeighbor(World world, BlockPos pos) {
        if (world == null || pos == null || !world.isBlockLoaded(pos)) {
            return false;
        }
        IBlockState state = world.getBlockState(pos);
        if (state.getMaterial() == Material.WATER || state.getBlock() instanceof BlockLiquid) {
            return true;
        }
        FluidStateBridge fluid = FluidStateMapper.fromWorld(world, pos, state);
        return fluid != null && fluid.getKind() == FluidStateBridge.FluidKind.WATER;
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
