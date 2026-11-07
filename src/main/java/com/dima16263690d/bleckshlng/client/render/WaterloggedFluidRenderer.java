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
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.lwjgl.opengl.GL11;

import com.dima16263690d.bleckshlng.fluid.network.ClientFluidStateCache;
import com.dima16263690d.bleckshlng.fluid.state.FluidHeightCalculator;
import com.dima16263690d.bleckshlng.fluid.state.FluidStateBridge;
import com.dima16263690d.bleckshlng.fluid.state.FluidStateMapper;

/**
 * Waterlogged fluid overlay. Faces against neighbouring water are omitted so
 * the volume merges with the ocean instead of looking like a separate box.
 */
@SideOnly(Side.CLIENT)
public final class WaterloggedFluidRenderer {
    private static final int CHUNK_RADIUS = 3;
    private static final double RENDER_DISTANCE_SQ = 64.0D * 64.0D;

    private WaterloggedFluidRenderer() {
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

        TextureAtlasSprite still = mc.getBlockRendererDispatcher()
                .getBlockModelShapes().getTexture(Blocks.WATER.getDefaultState());
        TextureAtlasSprite flow = still;
        try {
            flow = mc.getTextureMapBlocks().getAtlasSprite("minecraft:blocks/water_flow");
            if (flow == null || "missingno".equals(flow.getIconName())) {
                flow = still;
            }
        } catch (Throwable ignored) {
            flow = still;
        }
        if (still == null) {
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
        // Back-face cull: disableCull made every waterlogged cell a solid blue box.
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
                    wrote |= tessellateWaterloggedCell(buffer, world, pos,
                            entry.getValue(), still, flow, camX, camY, camZ, mc);
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
            WaterloggedChunkMesher.tessellate(world, pos, fluid, buffer);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean tessellateWaterloggedCell(BufferBuilder buffer, World world,
                                                    BlockPos pos, FluidStateBridge fluid,
                                                    TextureAtlasSprite still,
                                                    TextureAtlasSprite flow,
                                                    double camX, double camY, double camZ,
                                                    Minecraft mc) {
        IBlockState host = world.getBlockState(pos);
        float ownHeight = FluidHeightCalculator.getHeight(world, pos, fluid);
        if (ownHeight <= 0.001F) {
            ownHeight = 0.8888889F;
        }

        float minY = 0.0F;
        if (host.getBlock() instanceof BlockSlab
                && !((BlockSlab) host.getBlock()).isDouble()) {
            boolean top = host.getValue(BlockSlab.HALF) == BlockSlab.EnumBlockHalf.TOP;
            if (top) {
                minY = 0.5F;
                ownHeight = Math.max(ownHeight, 1.0F);
            } else {
                minY = 0.5F;
                ownHeight = Math.max(ownHeight, 0.8888889F);
            }
        }

        boolean waterAbove = isSameFluid(world, pos.up());
        if (waterAbove) {
            ownHeight = 1.0F;
        }

        float hNW = sampleCornerHeight(world, pos, ownHeight, -1, -1);
        float hNE = sampleCornerHeight(world, pos, ownHeight, 1, -1);
        float hSE = sampleCornerHeight(world, pos, ownHeight, 1, 1);
        float hSW = sampleCornerHeight(world, pos, ownHeight, -1, 1);
        hNW = Math.max(hNW, minY);
        hNE = Math.max(hNE, minY);
        hSE = Math.max(hSE, minY);
        hSW = Math.max(hSW, minY);
        if (waterAbove) {
            hNW = 1.0F;
            hNE = 1.0F;
            hSE = 1.0F;
            hSW = 1.0F;
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
        float r = (float) (color >> 16 & 255) / 255.0F;
        float g = (float) (color >> 8 & 255) / 255.0F;
        float b = (float) (color & 255) / 255.0F;
        int a = 180;

        int packed = world.getCombinedLight(pos, 0);
        int sky = packed >> 16 & 65535;
        int block = packed & 65535;

        boolean any = false;
        double x = pos.getX();
        double y = pos.getY();
        double z = pos.getZ();

        if (!waterAbove) {
            any |= emitUp(buffer, x, y, z, hNW, hNE, hSE, hSW, minY,
                    camX, camY, camZ, still, r, g, b, a, sky, block);
        }

        if (minY <= 0.001F && !isSameFluid(world, pos.down())) {
            any |= emitDown(buffer, x, y, z, minY, camX, camY, camZ,
                    still, r, g, b, a, sky, block);
        }

        if (!isSameFluid(world, pos.north())) {
            any |= emitSide(buffer, x, y, z, EnumFacing.NORTH, hNW, hNE, minY,
                    camX, camY, camZ, flow, r, g, b, a, sky, block);
        }
        if (!isSameFluid(world, pos.south())) {
            any |= emitSide(buffer, x, y, z, EnumFacing.SOUTH, hSW, hSE, minY,
                    camX, camY, camZ, flow, r, g, b, a, sky, block);
        }
        if (!isSameFluid(world, pos.west())) {
            any |= emitSide(buffer, x, y, z, EnumFacing.WEST, hNW, hSW, minY,
                    camX, camY, camZ, flow, r, g, b, a, sky, block);
        }
        if (!isSameFluid(world, pos.east())) {
            any |= emitSide(buffer, x, y, z, EnumFacing.EAST, hNE, hSE, minY,
                    camX, camY, camZ, flow, r, g, b, a, sky, block);
        }

        return any;
    }

    private static float sampleCornerHeight(World world, BlockPos pos,
                                            float ownHeight, int ox, int oz) {
        float sum = ownHeight;
        int count = 1;
        BlockPos[] samples = new BlockPos[] {
                pos.add(ox < 0 ? -1 : 0, 0, oz < 0 ? -1 : 0),
                pos.add(ox > 0 ? 1 : 0, 0, oz < 0 ? -1 : 0),
                pos.add(ox < 0 ? -1 : 0, 0, oz > 0 ? 1 : 0),
                pos.add(ox > 0 ? 1 : 0, 0, oz > 0 ? 1 : 0),
                pos.offset(EnumFacing.NORTH),
                pos.offset(EnumFacing.SOUTH),
                pos.offset(EnumFacing.WEST),
                pos.offset(EnumFacing.EAST)
        };
        for (int i = 0; i < samples.length; i++) {
            BlockPos p = samples[i];
            if (!world.isBlockLoaded(p)) {
                continue;
            }
            float h = fluidHeightAt(world, p);
            if (h > 0.0F) {
                sum += h;
                count++;
            }
        }
        return sum / (float) count;
    }

    private static float fluidHeightAt(World world, BlockPos pos) {
        IBlockState state = world.getBlockState(pos);
        if (state.getMaterial() == Material.WATER && state.getBlock() instanceof BlockLiquid) {
            int level = state.getValue(BlockLiquid.LEVEL).intValue();
            if (level >= 8) {
                return 1.0F;
            }
            return (float) (8 - level) / 9.0F;
        }
        FluidStateBridge fluid = FluidStateMapper.fromWorld(world, pos, state);
        if (fluid != null && fluid.getKind() == FluidStateBridge.FluidKind.WATER) {
            return FluidHeightCalculator.getHeight(world, pos, fluid);
        }
        return 0.0F;
    }

    private static boolean isSameFluid(World world, BlockPos pos) {
        if (!world.isBlockLoaded(pos)) {
            return false;
        }
        IBlockState state = world.getBlockState(pos);
        if (state.getMaterial() == Material.WATER) {
            return true;
        }
        FluidStateBridge fluid = FluidStateMapper.fromWorld(world, pos, state);
        return fluid != null && fluid.getKind() == FluidStateBridge.FluidKind.WATER;
    }

    private static boolean emitUp(BufferBuilder buffer, double x, double y, double z,
                                  float hNW, float hNE, float hSE, float hSW, float minY,
                                  double camX, double camY, double camZ,
                                  TextureAtlasSprite sprite,
                                  float r, float g, float b, int a, int sky, int block) {
        float u0 = sprite.getMinU();
        float u1 = sprite.getMaxU();
        float v0 = sprite.getMinV();
        float v1 = sprite.getMaxV();
        vert(buffer, x + 0.0D - camX, y + hNW - camY, z + 0.0D - camZ, u0, v0, r, g, b, a, sky, block);
        vert(buffer, x + 0.0D - camX, y + hSW - camY, z + 1.0D - camZ, u0, v1, r, g, b, a, sky, block);
        vert(buffer, x + 1.0D - camX, y + hSE - camY, z + 1.0D - camZ, u1, v1, r, g, b, a, sky, block);
        vert(buffer, x + 1.0D - camX, y + hNE - camY, z + 0.0D - camZ, u1, v0, r, g, b, a, sky, block);
        return true;
    }

    private static boolean emitDown(BufferBuilder buffer, double x, double y, double z,
                                    float minY, double camX, double camY, double camZ,
                                    TextureAtlasSprite sprite,
                                    float r, float g, float b, int a, int sky, int block) {
        float u0 = sprite.getMinU();
        float u1 = sprite.getMaxU();
        float v0 = sprite.getMinV();
        float v1 = sprite.getMaxV();
        double yy = y + minY - camY;
        vert(buffer, x + 0.0D - camX, yy, z + 1.0D - camZ, u0, v1, r, g, b, a, sky, block);
        vert(buffer, x + 0.0D - camX, yy, z + 0.0D - camZ, u0, v0, r, g, b, a, sky, block);
        vert(buffer, x + 1.0D - camX, yy, z + 0.0D - camZ, u1, v0, r, g, b, a, sky, block);
        vert(buffer, x + 1.0D - camX, yy, z + 1.0D - camZ, u1, v1, r, g, b, a, sky, block);
        return true;
    }

    private static boolean emitSide(BufferBuilder buffer, double x, double y, double z,
                                    EnumFacing face, float h0, float h1, float minY,
                                    double camX, double camY, double camZ,
                                    TextureAtlasSprite sprite,
                                    float r, float g, float b, int a, int sky, int block) {
        if (h0 <= minY && h1 <= minY) {
            return false;
        }
        float u0 = sprite.getMinU();
        float u1 = sprite.getMaxU();
        float v0 = sprite.getMinV();
        float v1 = sprite.getMaxV();
        float vTop0 = v0 + (v1 - v0) * (1.0F - MathHelper.clamp(h0, 0.0F, 1.0F));
        float vTop1 = v0 + (v1 - v0) * (1.0F - MathHelper.clamp(h1, 0.0F, 1.0F));
        float vBot = v0 + (v1 - v0) * (1.0F - MathHelper.clamp(minY, 0.0F, 1.0F));

        switch (face) {
            case NORTH:
                vert(buffer, x + 0.0D - camX, y + h0 - camY, z + 0.0D - camZ, u0, vTop0, r, g, b, a, sky, block);
                vert(buffer, x + 1.0D - camX, y + h1 - camY, z + 0.0D - camZ, u1, vTop1, r, g, b, a, sky, block);
                vert(buffer, x + 1.0D - camX, y + minY - camY, z + 0.0D - camZ, u1, vBot, r, g, b, a, sky, block);
                vert(buffer, x + 0.0D - camX, y + minY - camY, z + 0.0D - camZ, u0, vBot, r, g, b, a, sky, block);
                break;
            case SOUTH:
                vert(buffer, x + 1.0D - camX, y + h1 - camY, z + 1.0D - camZ, u0, vTop1, r, g, b, a, sky, block);
                vert(buffer, x + 0.0D - camX, y + h0 - camY, z + 1.0D - camZ, u1, vTop0, r, g, b, a, sky, block);
                vert(buffer, x + 0.0D - camX, y + minY - camY, z + 1.0D - camZ, u1, vBot, r, g, b, a, sky, block);
                vert(buffer, x + 1.0D - camX, y + minY - camY, z + 1.0D - camZ, u0, vBot, r, g, b, a, sky, block);
                break;
            case WEST:
                vert(buffer, x + 0.0D - camX, y + h0 - camY, z + 1.0D - camZ, u0, vTop0, r, g, b, a, sky, block);
                vert(buffer, x + 0.0D - camX, y + h1 - camY, z + 0.0D - camZ, u1, vTop1, r, g, b, a, sky, block);
                vert(buffer, x + 0.0D - camX, y + minY - camY, z + 0.0D - camZ, u1, vBot, r, g, b, a, sky, block);
                vert(buffer, x + 0.0D - camX, y + minY - camY, z + 1.0D - camZ, u0, vBot, r, g, b, a, sky, block);
                break;
            case EAST:
                vert(buffer, x + 1.0D - camX, y + h0 - camY, z + 0.0D - camZ, u0, vTop0, r, g, b, a, sky, block);
                vert(buffer, x + 1.0D - camX, y + h1 - camY, z + 1.0D - camZ, u1, vTop1, r, g, b, a, sky, block);
                vert(buffer, x + 1.0D - camX, y + minY - camY, z + 1.0D - camZ, u1, vBot, r, g, b, a, sky, block);
                vert(buffer, x + 1.0D - camX, y + minY - camY, z + 0.0D - camZ, u0, vBot, r, g, b, a, sky, block);
                break;
            default:
                return false;
        }
        return true;
    }

    private static void vert(BufferBuilder buffer,
                             double x, double y, double z,
                             float u, float v,
                             float r, float g, float b, int a,
                             int sky, int block) {
        buffer.pos(x, y, z)
                .color((int) (r * 255.0F), (int) (g * 255.0F), (int) (b * 255.0F), a)
                .tex((double) u, (double) v)
                .lightmap(sky, block)
                .endVertex();
    }
}
