package com.dima16263690d.bleckshlng.client.render;

import java.lang.reflect.Field;
import java.util.Map;

import net.minecraft.block.BlockLiquid;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockFluidRenderer;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.lwjgl.opengl.GL11;

import com.dima16263690d.bleckshlng.fluid.network.ClientFluidStateCache;
import com.dima16263690d.bleckshlng.fluid.state.FluidStateBridge;

/**
 * Renders waterlogged fluid using vanilla BlockFluidRenderer + FluidViewAccess.
 * 1.12.2 has no public getFluidRenderer(); resolved via field / constructor.
 */
@SideOnly(Side.CLIENT)
public final class WaterloggedFluidRenderer {
    private static final int CHUNK_RADIUS = 3;
    private static final double RENDER_DISTANCE_SQ = 64.0D * 64.0D;

    private static BlockFluidRenderer cachedFluidRenderer;

    private WaterloggedFluidRenderer() {
    }

    private static BlockFluidRenderer resolveFluidRenderer(Minecraft mc) {
        if (cachedFluidRenderer != null) {
            return cachedFluidRenderer;
        }
        BlockRendererDispatcher dispatcher = mc.getBlockRendererDispatcher();
        // Prefer the dispatcher instance (shares atlas sprites).
        try {
            Field[] fields = BlockRendererDispatcher.class.getDeclaredFields();
            for (int i = 0; i < fields.length; i++) {
                Field field = fields[i];
                if (field.getType() == BlockFluidRenderer.class) {
                    field.setAccessible(true);
                    Object value = field.get(dispatcher);
                    if (value instanceof BlockFluidRenderer) {
                        cachedFluidRenderer = (BlockFluidRenderer) value;
                        return cachedFluidRenderer;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        // Fallback: own instance with the same BlockColors.
        try {
            cachedFluidRenderer = new BlockFluidRenderer(mc.getBlockColors());
            // initAtlasSprites is called by dispatcher on resource reload; call if present.
            try {
                java.lang.reflect.Method init = BlockFluidRenderer.class
                        .getDeclaredMethod("initAtlasSprites");
                init.setAccessible(true);
                init.invoke(cachedFluidRenderer);
            } catch (Throwable ignored) {
            }
            return cachedFluidRenderer;
        } catch (Throwable t) {
            return null;
        }
    }

    public static void render(float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();
        World world = mc.world;
        if (world == null || mc.getRenderViewEntity() == null) {
            return;
        }

        double camX = mc.getRenderViewEntity().lastTickPosX
                + (mc.getRenderViewEntity().posX - mc.getRenderViewEntity().lastTickPosX)
                * (double) partialTicks;
        double camY = mc.getRenderViewEntity().lastTickPosY
                + (mc.getRenderViewEntity().posY - mc.getRenderViewEntity().lastTickPosY)
                * (double) partialTicks;
        double camZ = mc.getRenderViewEntity().lastTickPosZ
                + (mc.getRenderViewEntity().posZ - mc.getRenderViewEntity().lastTickPosZ)
                * (double) partialTicks;

        int originChunkX = MathHelper.floor(camX) >> 4;
        int originChunkZ = MathHelper.floor(camZ) >> 4;
        int dimension = world.provider.getDimension();

        BlockFluidRenderer fluidRenderer = resolveFluidRenderer(mc);
        if (fluidRenderer == null) {
            return;
        }

        FluidViewAccess access = new FluidViewAccess(world);

        mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);

        GlStateManager.pushMatrix();
        GlStateManager.translate(-camX, -camY, -camZ);
        GlStateManager.enableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.tryBlendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO);
        GlStateManager.enableCull();
        GlStateManager.enableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.enablePolygonOffset();
        GlStateManager.doPolygonOffset(-1.0F, -10.0F);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK);

        boolean wrote = false;
        for (int cx = originChunkX - CHUNK_RADIUS; cx <= originChunkX + CHUNK_RADIUS; cx++) {
            for (int cz = originChunkZ - CHUNK_RADIUS; cz <= originChunkZ + CHUNK_RADIUS; cz++) {
                Map<Long, FluidStateBridge> states =
                        ClientFluidStateCache.copyChunk(dimension, cx, cz);
                if (states == null || states.isEmpty()) {
                    continue;
                }
                for (Map.Entry<Long, FluidStateBridge> entry : states.entrySet()) {
                    if (entry.getKey() == null || entry.getValue() == null) {
                        continue;
                    }
                    if (entry.getValue().getKind() != FluidStateBridge.FluidKind.WATER) {
                        continue;
                    }
                    BlockPos pos = BlockPos.fromLong(entry.getKey().longValue());
                    double dx = pos.getX() + 0.5D - camX;
                    double dy = pos.getY() + 0.5D - camY;
                    double dz = pos.getZ() + 0.5D - camZ;
                    if (dx * dx + dy * dy + dz * dz > RENDER_DISTANCE_SQ) {
                        continue;
                    }
                    if (!world.isBlockLoaded(pos)) {
                        continue;
                    }

                    IBlockState host = world.getBlockState(pos);
                    if (host.getBlock() instanceof BlockSlab
                            && ((BlockSlab) host.getBlock()).isDouble()) {
                        continue;
                    }

                    IBlockState fluidState = access.getBlockState(pos);
                    if (!(fluidState.getBlock() instanceof BlockLiquid)) {
                        fluidState = Blocks.WATER.getDefaultState();
                    }

                    try {
                        if (fluidRenderer.renderFluid(access, fluidState, pos, buffer)) {
                            wrote = true;
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }
        }

        if (wrote) {
            tessellator.draw();
        } else {
            buffer.finishDrawing();
        }

        GlStateManager.disablePolygonOffset();
        GlStateManager.depthMask(true);
        GlStateManager.enableAlpha();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    public static boolean renderIntoChunk(BufferBuilder buffer, World world,
                                          BlockPos pos, IBlockState host,
                                          FluidStateBridge fluid) {
        if (buffer == null || world == null || pos == null || fluid == null) {
            return false;
        }
        if (fluid.getKind() != FluidStateBridge.FluidKind.WATER) {
            return false;
        }
        try {
            Minecraft mc = Minecraft.getMinecraft();
            BlockFluidRenderer fluidRenderer = resolveFluidRenderer(mc);
            if (fluidRenderer == null) {
                return false;
            }
            FluidViewAccess access = new FluidViewAccess(world);
            IBlockState fluidState = access.getBlockState(pos);
            if (!(fluidState.getBlock() instanceof BlockLiquid)) {
                fluidState = Blocks.WATER.getDefaultState();
            }
            return fluidRenderer.renderFluid(access, fluidState, pos, buffer);
        } catch (Throwable t) {
            return false;
        }
    }
}
