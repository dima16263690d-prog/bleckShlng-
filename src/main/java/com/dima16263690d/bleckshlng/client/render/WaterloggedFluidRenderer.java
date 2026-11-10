package com.dima16263690d.bleckshlng.client.render;

import java.util.Map;

import net.minecraft.block.BlockSlab;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.lwjgl.opengl.GL11;

import com.dima16263690d.bleckshlng.fluid.network.ClientFluidStateCache;
import com.dima16263690d.bleckshlng.fluid.state.FluidStateBridge;

/**
 * Waterlogged-fluid renderer for the 1.12.2 client.
 *
 * Deliberately does not call BlockFluidRenderer or
 * BlockRendererDispatcher.renderBlock: waterlogged geometry is emitted by
 * WaterloggedChunkMesher, which owns fluid surface heights, flow UVs and
 * directional lighting independently of the legacy fluid renderer.
 */
@SideOnly(Side.CLIENT)
public final class WaterloggedFluidRenderer {
    private static final int CHUNK_RADIUS = 3;
    private static final double RENDER_DISTANCE_SQ = 64.0D * 64.0D;

    private WaterloggedFluidRenderer() {
    }

    /**
     * Optional WorldLast fallback. Normal operation renders through the
     * compiled chunk layer; this path is retained only for diagnostics.
     */
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
        FluidViewAccess access = new FluidViewAccess(world);

        mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        GlStateManager.pushMatrix();
        GlStateManager.translate(-camX, -camY, -camZ);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO);
        GlStateManager.enableCull();
        GlStateManager.enableDepth();
        GlStateManager.depthMask(false);

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
                    FluidStateBridge fluid = entry.getValue();
                    if (entry.getKey() == null || fluid == null
                            || fluid.getKind() != FluidStateBridge.FluidKind.WATER) {
                        continue;
                    }
                    BlockPos pos = BlockPos.fromLong(entry.getKey().longValue());
                    double dx = pos.getX() + 0.5D - camX;
                    double dy = pos.getY() + 0.5D - camY;
                    double dz = pos.getZ() + 0.5D - camZ;
                    if (dx * dx + dy * dy + dz * dz > RENDER_DISTANCE_SQ
                            || !world.isBlockLoaded(pos)) {
                        continue;
                    }

                    IBlockState host = access.getOriginalBlockState(pos);
                    if (host.getBlock() instanceof BlockSlab
                            && ((BlockSlab) host.getBlock()).isDouble()) {
                        continue;
                    }
                    int before = buffer.getVertexCount();
                    WaterloggedChunkMesher.tessellate(access, host, pos, fluid, buffer);
                    wrote |= buffer.getVertexCount() > before;
                }
            }
        }

        if (wrote) {
            tessellator.draw();
        } else {
            buffer.finishDrawing();
        }
        GlStateManager.depthMask(true);
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    public static boolean renderIntoChunk(BufferBuilder buffer, World world,
                                          BlockPos pos, IBlockState host,
                                          FluidStateBridge fluid) {
        if (world == null) {
            return false;
        }
        return renderIntoChunk(buffer, new FluidViewAccess(world), host, pos, fluid);
    }

    /**
     * Chunk rebuild overload. The original host state is kept separate from
     * the virtual fluid view so the renderer can clip water against the
     * actual waterloggable block shape.
     */
    public static boolean renderIntoChunk(BufferBuilder buffer, IBlockAccess access,
                                          BlockPos pos, FluidStateBridge fluid) {
        if (access == null || pos == null) {
            return false;
        }
        IBlockState host = access instanceof FluidViewAccess
                ? ((FluidViewAccess) access).getOriginalBlockState(pos)
                : access.getBlockState(pos);
        return renderIntoChunk(buffer, access, host, pos, fluid);
    }

    public static boolean renderIntoChunk(BufferBuilder buffer, IBlockAccess access,
                                          IBlockState host, BlockPos pos,
                                          FluidStateBridge fluid) {
        if (buffer == null || access == null || host == null || pos == null
                || fluid == null || fluid.getKind() != FluidStateBridge.FluidKind.WATER) {
            return false;
        }
        int before = buffer.getVertexCount();
        WaterloggedChunkMesher.tessellate(access, host, pos, fluid, buffer);
        return buffer.getVertexCount() > before;
    }
}
