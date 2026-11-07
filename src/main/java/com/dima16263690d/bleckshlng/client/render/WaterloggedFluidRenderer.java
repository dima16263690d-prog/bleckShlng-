package com.dima16263690d.bleckshlng.client.render;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import net.minecraft.block.Block;
import net.minecraft.block.BlockBreakable;
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
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.lwjgl.opengl.GL11;

import com.dima16263690d.bleckshlng.fluid.geometry.FluidShapeCalculator;
import com.dima16263690d.bleckshlng.fluid.network.ClientFluidStateCache;
import com.dima16263690d.bleckshlng.fluid.physics.FluidFlowVector;
import com.dima16263690d.bleckshlng.fluid.state.FluidHeightCalculator;
import com.dima16263690d.bleckshlng.fluid.state.FluidStateBridge;
import com.dima16263690d.bleckshlng.fluid.state.FluidStateMapper;

/**
 * Client renderer for fluids stored in the legacy waterlogging sidecar.
 *
 * The native water and lava blocks are rendered by ModernLiquidBlockRenderer112
 * in the normal chunk translucent pass. This renderer handles only fluid states
 * stored inside non-liquid host blocks.
 *
 * Geometry follows the modern LiquidBlockRenderer approach:
 * weighted corner heights, flow-oriented still/flow UVs, per-face occlusion,
 * water overlay beside glass/leaves, directional shading and combined lighting.
 * Collision-box boundaries split the surface and side faces so slabs, stairs,
 * panes, fences, walls and trapdoors do not receive a full-cube fluid overlay.
 */
@SideOnly(Side.CLIENT)
public final class WaterloggedFluidRenderer {
    private static final int CHUNK_RADIUS = 3;
    private static final double RENDER_DISTANCE_SQ = 64.0D * 64.0D;
    private static final double EPSILON = 0.001D;
    private static final double EDGE_EPSILON = 1.0E-5D;
    private static final float UV_SHRINK = 0.001F;

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

        int centerChunkX = MathHelper.floor(cameraX) >> 4;
        int centerChunkZ = MathHelper.floor(cameraZ) >> 4;
        int dimension = world.provider.getDimension();

        List<RenderCell> cells = new ArrayList<RenderCell>();
        for (int chunkX = centerChunkX - CHUNK_RADIUS;
             chunkX <= centerChunkX + CHUNK_RADIUS; chunkX++) {
            for (int chunkZ = centerChunkZ - CHUNK_RADIUS;
                 chunkZ <= centerChunkZ + CHUNK_RADIUS; chunkZ++) {
                Map<Long, FluidStateBridge> states =
                        ClientFluidStateCache.copyChunk(dimension, chunkX, chunkZ);
                if (states == null || states.isEmpty()) {
                    continue;
                }

                for (Map.Entry<Long, FluidStateBridge> entry : states.entrySet()) {
                    if (entry.getKey() == null || entry.getValue() == null) {
                        continue;
                    }

                    BlockPos pos = BlockPos.fromLong(entry.getKey().longValue());
                    if (!world.isBlockLoaded(pos)) {
                        continue;
                    }

                    double dx = pos.getX() + 0.5D - cameraX;
                    double dy = pos.getY() + 0.5D - cameraY;
                    double dz = pos.getZ() + 0.5D - cameraZ;
                    double distanceSq = dx * dx + dy * dy + dz * dz;
                    if (distanceSq > RENDER_DISTANCE_SQ) {
                        continue;
                    }

                    IBlockState host = world.getBlockState(pos);
                    if (host.getBlock() == Blocks.AIR
                            || host.getBlock() instanceof net.minecraft.block.BlockLiquid) {
                        continue;
                    }

                    cells.add(new RenderCell(pos, entry.getValue(), distanceSq));
                }
            }
        }

        if (cells.isEmpty()) {
            return;
        }

        // Translucent cell geometry must be submitted far-to-near.
        Collections.sort(cells, new Comparator<RenderCell>() {
            @Override
            public int compare(RenderCell first, RenderCell second) {
                return Double.compare(second.distanceSq, first.distanceSq);
            }
        });

        TextureMap atlas = mc.getTextureMapBlocks();
        TextureAtlasSprite[] waterSprites = new TextureAtlasSprite[] {
                atlas.getAtlasSprite("minecraft:blocks/water_still"),
                atlas.getAtlasSprite("minecraft:blocks/water_flow")
        };
        TextureAtlasSprite[] lavaSprites = new TextureAtlasSprite[] {
                atlas.getAtlasSprite("minecraft:blocks/lava_still"),
                atlas.getAtlasSprite("minecraft:blocks/lava_flow")
        };
        TextureAtlasSprite waterOverlay = atlas.getAtlasSprite("minecraft:blocks/water_overlay");

        mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);

        GlStateManager.pushMatrix();
        boolean bufferDrawing = false;
        BufferBuilder buffer = Tessellator.getInstance().getBuffer();

        try {
            GlStateManager.enableBlend();
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

            buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK);
            bufferDrawing = true;

            for (RenderCell cell : cells) {
                boolean lava = cell.fluid.getKind() == FluidStateBridge.FluidKind.LAVA;
                TextureAtlasSprite[] sprites = lava ? lavaSprites : waterSprites;
                if (sprites[0] == null || sprites[1] == null) {
                    continue;
                }

                tessellateCell(buffer, world, cell.pos, cell.fluid,
                        sprites, waterOverlay, cameraX, cameraY, cameraZ, mc);
            }

            // Draw even an empty buffer so BufferBuilder is always reset.
            Tessellator.getInstance().draw();
            bufferDrawing = false;
        } finally {
            if (bufferDrawing) {
                try {
                    Tessellator.getInstance().draw();
                } catch (Throwable ignored) {
                    buffer.reset();
                }
            }

            GlStateManager.disablePolygonOffset();
            GlStateManager.depthMask(true);
            GlStateManager.enableCull();
            GlStateManager.enableAlpha();
            GlStateManager.disableBlend();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.popMatrix();
        }
    }

    private static boolean tessellateCell(
            BufferBuilder buffer,
            World world,
            BlockPos pos,
            FluidStateBridge fluid,
            TextureAtlasSprite[] sprites,
            TextureAtlasSprite waterOverlay,
            double cameraX,
            double cameraY,
            double cameraZ,
            Minecraft mc) {
        IBlockState host = world.getBlockState(pos);
        float ownHeight = FluidHeightCalculator.getHeight(world, pos, fluid);
        if (ownHeight <= EDGE_EPSILON) {
            return false;
        }

        FluidStateBridge.FluidKind kind = fluid.getKind();
        float northWest = sampleCornerHeight(world, pos, kind, ownHeight, -1, -1);
        float northEast = sampleCornerHeight(world, pos, kind, ownHeight, 1, -1);
        float southEast = sampleCornerHeight(world, pos, kind, ownHeight, 1, 1);
        float southWest = sampleCornerHeight(world, pos, kind, ownHeight, -1, 1);

        net.minecraft.util.math.Vec3d flow =
                FluidFlowVector.calculate(world, pos, host);
        float[] topUv = getTopUV(sprites, flow);

        int color = kind == FluidStateBridge.FluidKind.LAVA
                ? 0xFFFFFF : getWaterColor(mc, world, pos);
        float red = ((color >> 16) & 0xFF) / 255.0F;
        float green = ((color >> 8) & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;

        int[] light = getLightColor(world, pos);
        List<AxisAlignedBB> boxes =
                FluidShapeCalculator.getCollisionBoxes(world, pos, host);

        boolean rendered = renderTopMask(buffer, world, pos, host,
                ownHeight, boxes, northWest, northEast, southEast, southWest,
                topUv, red, green, blue, light[0], light[1],
                cameraX, cameraY, cameraZ);

        rendered |= renderBottomMask(buffer, world, pos, host, kind, boxes,
                sprites[0], red, green, blue, light[0], light[1],
                cameraX, cameraY, cameraZ);

        rendered |= renderSideMask(buffer, world, pos, host, fluid, ownHeight,
                boxes, sprites[1], waterOverlay, red, green, blue,
                light[0], light[1], cameraX, cameraY, cameraZ);
        return rendered;
    }

    private static boolean renderTopMask(
            BufferBuilder buffer, World world, BlockPos pos, IBlockState host,
            float ownHeight, List<AxisAlignedBB> boxes,
            float northWest, float northEast, float southEast, float southWest,
            float[] uv, float red, float green, float blue,
            int skyLight, int blockLight,
            double cameraX, double cameraY, double cameraZ) {
        List<Double> xEdges = coordinateEdges(boxes, null, pos, true);
        List<Double> zEdges = coordinateEdges(boxes, null, pos, false);
        boolean wrote = false;

        for (int xIndex = 0; xIndex + 1 < xEdges.size(); xIndex++) {
            double x0 = xEdges.get(xIndex).doubleValue();
            double x1 = xEdges.get(xIndex + 1).doubleValue();
            if (x1 - x0 <= EDGE_EPSILON) {
                continue;
            }

            for (int zIndex = 0; zIndex + 1 < zEdges.size(); zIndex++) {
                double z0 = zEdges.get(zIndex).doubleValue();
                double z1 = zEdges.get(zIndex + 1).doubleValue();
                if (z1 - z0 <= EDGE_EPSILON) {
                    continue;
                }

                double centerX = pos.getX() + (x0 + x1) * 0.5D;
                double centerZ = pos.getZ() + (z0 + z1) * 0.5D;

                float tileNominalHeight = Math.max(
                        northWest, Math.max(northEast, Math.max(southEast, southWest)));
                tileNominalHeight = Math.min(1.0F, Math.max(ownHeight, tileNominalHeight));

                float tileHeight = FluidShapeCalculator.getFluidColumnHeight(
                        world, pos, host, centerX, centerZ, tileNominalHeight);
                float floor = getFluidColumnFloor(boxes, pos, centerX, centerZ);
                if (tileHeight <= floor + EDGE_EPSILON) {
                    continue;
                }

                double localX0 = x0 + EDGE_EPSILON;
                double localX1 = x1 - EDGE_EPSILON;
                double localZ0 = z0 + EDGE_EPSILON;
                double localZ1 = z1 - EDGE_EPSILON;

                float h00 = clampHeight(bilerpHeight(
                        northWest, northEast, southEast, southWest, localX0, localZ0),
                        floor, tileHeight);
                float h01 = clampHeight(bilerpHeight(
                        northWest, northEast, southEast, southWest, localX0, localZ1),
                        floor, tileHeight);
                float h11 = clampHeight(bilerpHeight(
                        northWest, northEast, southEast, southWest, localX1, localZ1),
                        floor, tileHeight);
                float h10 = clampHeight(bilerpHeight(
                        northWest, northEast, southEast, southWest, localX1, localZ0),
                        floor, tileHeight);

                double meanHeight = (h00 + h01 + h11 + h10) * 0.25D;
                if (meanHeight <= floor + EDGE_EPSILON
                        || !FluidShapeCalculator.containsFluidPoint(
                        world, pos, host, centerX, pos.getY() + meanHeight - EPSILON,
                        centerZ, tileHeight)) {
                    continue;
                }

                float[] uv00 = topUvAt(uv, localX0, localZ0);
                float[] uv01 = topUvAt(uv, localX0, localZ1);
                float[] uv11 = topUvAt(uv, localX1, localZ1);
                float[] uv10 = topUvAt(uv, localX1, localZ0);

                emitVertex(buffer, pos.getX() + localX0 - cameraX,
                        pos.getY() + h00 - EPSILON - cameraY,
                        pos.getZ() + localZ0 - cameraZ,
                        uv00[0], uv00[1], red, green, blue, 255, skyLight, blockLight);
                emitVertex(buffer, pos.getX() + localX0 - cameraX,
                        pos.getY() + h01 - EPSILON - cameraY,
                        pos.getZ() + localZ1 - cameraZ,
                        uv01[0], uv01[1], red, green, blue, 255, skyLight, blockLight);
                emitVertex(buffer, pos.getX() + localX1 - cameraX,
                        pos.getY() + h11 - EPSILON - cameraY,
                        pos.getZ() + localZ1 - cameraZ,
                        uv11[0], uv11[1], red, green, blue, 255, skyLight, blockLight);
                emitVertex(buffer, pos.getX() + localX1 - cameraX,
                        pos.getY() + h10 - EPSILON - cameraY,
                        pos.getZ() + localZ0 - cameraZ,
                        uv10[0], uv10[1], red, green, blue, 255, skyLight, blockLight);
                wrote = true;
            }
        }

        return wrote;
    }

    private static boolean renderBottomMask(
            BufferBuilder buffer, World world, BlockPos pos, IBlockState host,
            FluidStateBridge.FluidKind kind, List<AxisAlignedBB> boxes,
            TextureAtlasSprite still, float red, float green, float blue,
            int skyLight, int blockLight,
            double cameraX, double cameraY, double cameraZ) {
        if (isSameFluid(world, pos.down(), kind)) {
            return false;
        }

        List<Double> xEdges = coordinateEdges(boxes, null, pos, true);
        List<Double> zEdges = coordinateEdges(boxes, null, pos, false);
        boolean wrote = false;

        for (int xi = 0; xi + 1 < xEdges.size(); xi++) {
            double x0 = xEdges.get(xi).doubleValue();
            double x1 = xEdges.get(xi + 1).doubleValue();
            for (int zi = 0; zi + 1 < zEdges.size(); zi++) {
                double z0 = zEdges.get(zi).doubleValue();
                double z1 = zEdges.get(zi + 1).doubleValue();
                if (x1 - x0 <= EDGE_EPSILON || z1 - z0 <= EDGE_EPSILON) {
                    continue;
                }

                double cx = pos.getX() + (x0 + x1) * 0.5D;
                double cz = pos.getZ() + (z0 + z1) * 0.5D;
                if (getFluidColumnFloor(boxes, pos, cx, cz) > EDGE_EPSILON
                        || !FluidShapeCalculator.containsFluidPoint(
                        world, pos, host, cx, pos.getY() + EPSILON, cz, 1.0F)) {
                    continue;
                }

                float u0 = still.getMinU() + (float) x0 * (still.getMaxU() - still.getMinU());
                float u1 = still.getMinU() + (float) x1 * (still.getMaxU() - still.getMinU());
                float v0 = still.getMinV() + (float) z0 * (still.getMaxV() - still.getMinV());
                float v1 = still.getMinV() + (float) z1 * (still.getMaxV() - still.getMinV());
                float shade = 0.5F;
                double y = pos.getY() + EPSILON - cameraY;

                emitVertex(buffer, pos.getX() + x0 - cameraX, y,
                        pos.getZ() + z1 - cameraZ, u0, v1,
                        red * shade, green * shade, blue * shade, 255, skyLight, blockLight);
                emitVertex(buffer, pos.getX() + x0 - cameraX, y,
                        pos.getZ() + z0 - cameraZ, u0, v0,
                        red * shade, green * shade, blue * shade, 255, skyLight, blockLight);
                emitVertex(buffer, pos.getX() + x1 - cameraX, y,
                        pos.getZ() + z0 - cameraZ, u1, v0,
                        red * shade, green * shade, blue * shade, 255, skyLight, blockLight);
                emitVertex(buffer, pos.getX() + x1 - cameraX, y,
                        pos.getZ() + z1 - cameraZ, u1, v1,
                        red * shade, green * shade, blue * shade, 255, skyLight, blockLight);
                wrote = true;
            }
        }
        return wrote;
    }

    private static boolean renderSideMask(
            BufferBuilder buffer, World world, BlockPos pos, IBlockState host,
            FluidStateBridge currentFluid, float ownHeight,
            List<AxisAlignedBB> currentBoxes,
            TextureAtlasSprite flow, TextureAtlasSprite waterOverlay,
            float red, float green, float blue, int skyLight, int blockLight,
            double cameraX, double cameraY, double cameraZ) {
        boolean wrote = false;
        double baseX = pos.getX();
        double baseY = pos.getY();
        double baseZ = pos.getZ();

        for (EnumFacing direction : EnumFacing.Plane.HORIZONTAL) {
            BlockPos neighborPos = pos.offset(direction);
            if (!world.isBlockLoaded(neighborPos)) {
                continue;
            }

            IBlockState neighborState = world.getBlockState(neighborPos);
            FluidStateBridge neighborFluid = FluidStateMapper.fromWorld(
                    world, neighborPos, neighborState);
            boolean sameFluid = currentFluid.isSameKind(neighborFluid);
            float neighborHeight = sameFluid
                    ? FluidHeightCalculator.getHeight(world, neighborPos, neighborFluid)
                    : 1.0F;
            List<AxisAlignedBB> neighborBoxes =
                    FluidShapeCalculator.getCollisionBoxes(world, neighborPos, neighborState);

            boolean xAxis = direction.getAxis() == EnumFacing.Axis.Z;
            List<Double> uEdges = coordinateEdges(
                    currentBoxes, neighborBoxes, pos, xAxis);
            List<Double> yEdges = heightEdges(currentBoxes, neighborBoxes, pos,
                    Math.max(ownHeight, neighborHeight), ownHeight, neighborHeight);

            TextureAtlasSprite sideSprite = flow;
            if (currentFluid.getKind() == FluidStateBridge.FluidKind.WATER
                    && isWaterOverlayBlock(neighborState, world, neighborPos)) {
                sideSprite = waterOverlay;
            }

            for (int ui = 0; ui + 1 < uEdges.size(); ui++) {
                double u0 = uEdges.get(ui).doubleValue();
                double u1 = uEdges.get(ui + 1).doubleValue();
                if (u1 - u0 <= EDGE_EPSILON) {
                    continue;
                }
                double uCenter = (u0 + u1) * 0.5D;

                for (int yi = 0; yi + 1 < yEdges.size(); yi++) {
                    double y0 = yEdges.get(yi).doubleValue();
                    double y1 = yEdges.get(yi + 1).doubleValue();
                    if (y1 - y0 <= EDGE_EPSILON || y0 >= ownHeight) {
                        continue;
                    }

                    double yCenter = (y0 + y1) * 0.5D;
                    double currentX;
                    double currentZ;
                    double neighborX;
                    double neighborZ;

                    switch (direction) {
                        case NORTH:
                            currentX = baseX + uCenter;
                            currentZ = baseZ + EPSILON;
                            neighborX = currentX;
                            neighborZ = neighborPos.getZ() + 1.0D - EPSILON;
                            break;
                        case SOUTH:
                            currentX = baseX + uCenter;
                            currentZ = baseZ + 1.0D - EPSILON;
                            neighborX = currentX;
                            neighborZ = neighborPos.getZ() + EPSILON;
                            break;
                        case WEST:
                            currentX = baseX + EPSILON;
                            currentZ = baseZ + uCenter;
                            neighborX = neighborPos.getX() + 1.0D - EPSILON;
                            neighborZ = currentZ;
                            break;
                        case EAST:
                            currentX = baseX + 1.0D - EPSILON;
                            currentZ = baseZ + uCenter;
                            neighborX = neighborPos.getX() + EPSILON;
                            neighborZ = currentZ;
                            break;
                        default:
                            continue;
                    }

                    float localTop = getLocalSurfaceHeight(
                            world, pos, host, ownHeight, currentX, currentZ);
                    double currentY = baseY + yCenter;
                    if (yCenter < getFluidColumnFloor(
                            currentBoxes, pos, currentX, currentZ) - EDGE_EPSILON
                            || yCenter >= localTop - EDGE_EPSILON
                            || !FluidShapeCalculator.containsFluidPoint(
                            world, pos, host, currentX, currentY, currentZ, ownHeight)) {
                        continue;
                    }

                    boolean neighborFree = FluidShapeCalculator.containsFluidPoint(
                            world, neighborPos, neighborState, neighborX, currentY,
                            neighborZ, neighborHeight);
                    if (sameFluid && neighborFree
                            && yCenter < neighborHeight - EDGE_EPSILON) {
                        continue;
                    }
                    if (neighborState.getMaterial().blocksMovement() && !neighborFree) {
                        continue;
                    }

                    float u0Tex = sideSprite.getInterpolatedU(u0 * 8.0D);
                    float u1Tex = sideSprite.getInterpolatedU(u1 * 8.0D);
                    float v0Tex = sideSprite.getInterpolatedV(
                            (1.0D - MathHelper.clamp((float) y0, 0.0F, 1.0F)) * 8.0D);
                    float v1Tex = sideSprite.getInterpolatedV(
                            (1.0D - MathHelper.clamp((float) y1, 0.0F, 1.0F)) * 8.0D);
                    float shade = (direction.getAxis() == EnumFacing.Axis.Z) ? 0.8F : 0.6F;
                    float sideRed = red * shade;
                    float sideGreen = green * shade;
                    float sideBlue = blue * shade;

                    double x0;
                    double x1;
                    double z0;
                    double z1;
                    if (direction.getAxis() == EnumFacing.Axis.X) {
                        x0 = baseX + (direction == EnumFacing.WEST ? EPSILON : 1.0D - EPSILON);
                        x1 = x0;
                        z0 = baseZ + u0;
                        z1 = baseZ + u1;
                    } else {
                        x0 = baseX + u0;
                        x1 = baseX + u1;
                        z0 = baseZ + (direction == EnumFacing.NORTH ? EPSILON : 1.0D - EPSILON);
                        z1 = z0;
                    }

                    emitSideQuad(buffer, direction,
                            x0, x1, z0, z1, baseY + y0, baseY + y1,
                            u0Tex, u1Tex, v0Tex, v1Tex,
                            sideRed, sideGreen, sideBlue, skyLight, blockLight,
                            cameraX, cameraY, cameraZ);
                    wrote = true;
                }
            }
        }
        return wrote;
    }

    private static void emitSideQuad(
            BufferBuilder buffer, EnumFacing face,
            double x0, double x1, double z0, double z1,
            double y0, double y1,
            float u0, float u1, float v0, float v1,
            float red, float green, float blue,
            int skyLight, int blockLight,
            double cameraX, double cameraY, double cameraZ) {
        switch (face) {
            case NORTH:
                emitVertex(buffer, x0-cameraX, y0-cameraY, z0-cameraZ, u0, v0,
                        red, green, blue, 255, skyLight, blockLight);
                emitVertex(buffer, x1-cameraX, y0-cameraY, z1-cameraZ, u1, v0,
                        red, green, blue, 255, skyLight, blockLight);
                emitVertex(buffer, x1-cameraX, y1-cameraY, z1-cameraZ, u1, v1,
                        red, green, blue, 255, skyLight, blockLight);
                emitVertex(buffer, x0-cameraX, y1-cameraY, z0-cameraZ, u0, v1,
                        red, green, blue, 255, skyLight, blockLight);
                break;
            case SOUTH:
                emitVertex(buffer, x1-cameraX, y0-cameraY, z1-cameraZ, u1, v0,
                        red, green, blue, 255, skyLight, blockLight);
                emitVertex(buffer, x0-cameraX, y0-cameraY, z0-cameraZ, u0, v0,
                        red, green, blue, 255, skyLight, blockLight);
                emitVertex(buffer, x0-cameraX, y1-cameraY, z0-cameraZ, u0, v1,
                        red, green, blue, 255, skyLight, blockLight);
                emitVertex(buffer, x1-cameraX, y1-cameraY, z1-cameraZ, u1, v1,
                        red, green, blue, 255, skyLight, blockLight);
                break;
            case WEST:
                emitVertex(buffer, x0-cameraX, y0-cameraY, z1-cameraZ, u1, v0,
                        red, green, blue, 255, skyLight, blockLight);
                emitVertex(buffer, x1-cameraX, y0-cameraY, z0-cameraZ, u0, v0,
                        red, green, blue, 255, skyLight, blockLight);
                emitVertex(buffer, x1-cameraX, y1-cameraY, z0-cameraZ, u0, v1,
                        red, green, blue, 255, skyLight, blockLight);
                emitVertex(buffer, x0-cameraX, y1-cameraY, z1-cameraZ, u1, v1,
                        red, green, blue, 255, skyLight, blockLight);
                break;
            case EAST:
                emitVertex(buffer, x1-cameraX, y0-cameraY, z0-cameraZ, u0, v0,
                        red, green, blue, 255, skyLight, blockLight);
                emitVertex(buffer, x0-cameraX, y0-cameraY, z1-cameraZ, u1, v0,
                        red, green, blue, 255, skyLight, blockLight);
                emitVertex(buffer, x0-cameraX, y1-cameraY, z1-cameraZ, u1, v1,
                        red, green, blue, 255, skyLight, blockLight);
                emitVertex(buffer, x1-cameraX, y1-cameraY, z0-cameraZ, u0, v1,
                        red, green, blue, 255, skyLight, blockLight);
                break;
            default:
                break;
        }
    }

    private static float getLocalSurfaceHeight(
            World world, BlockPos pos, IBlockState host, float ownHeight,
            double worldX, double worldZ) {
        float cornerNW = sampleCornerHeight(world, pos,
                FluidStateBridge.FluidKind.WATER, ownHeight, -1, -1);
        float cornerNE = sampleCornerHeight(world, pos,
                FluidStateBridge.FluidKind.WATER, ownHeight, 1, -1);
        float cornerSE = sampleCornerHeight(world, pos,
                FluidStateBridge.FluidKind.WATER, ownHeight, 1, 1);
        float cornerSW = sampleCornerHeight(world, pos,
                FluidStateBridge.FluidKind.WATER, ownHeight, -1, 1);
        double localX = MathHelper.clamp((float) (worldX - pos.getX()), 0.0F, 1.0F);
        double localZ = MathHelper.clamp((float) (worldZ - pos.getZ()), 0.0F, 1.0F);
        float raw = bilerpHeight(cornerNW, cornerNE, cornerSE, cornerSW, localX, localZ);
        return FluidShapeCalculator.getFluidColumnHeight(
                world, pos, host, worldX, worldZ, raw);
    }

    private static float getWaterColor(Minecraft mc, World world, BlockPos pos) {
        try {
            int tint = mc.getBlockColors().colorMultiplier(
                    Blocks.WATER.getDefaultState(), world, pos, 0);
            return tint == -1 ? 0x3F76E4 : tint;
        } catch (Throwable ignored) {
            return 0x3F76E4;
        }
    }

    private static boolean isWaterOverlayBlock(
            IBlockState state, World world, BlockPos pos) {
        Block block = state.getBlock();
        return block instanceof BlockBreakable || block.isLeaves(state, world, pos);
    }

    private static boolean isSameFluid(
            World world, BlockPos pos, FluidStateBridge.FluidKind kind) {
        if (!world.isBlockLoaded(pos)) {
            return false;
        }
        FluidStateBridge state = FluidStateMapper.fromWorld(
                world, pos, world.getBlockState(pos));
        return state != null && state.getKind() == kind;
    }

    private static float fluidHeightAt(
            World world, BlockPos pos, FluidStateBridge.FluidKind kind) {
        if (!world.isBlockLoaded(pos)) {
            return 0.0F;
        }
        FluidStateBridge state = FluidStateMapper.fromWorld(
                world, pos, world.getBlockState(pos));
        return state != null && state.getKind() == kind
                ? FluidHeightCalculator.getHeight(world, pos, state) : 0.0F;
    }

    private static float sampleCornerHeight(
            World world, BlockPos pos, FluidStateBridge.FluidKind kind,
            float ownHeight, int ox, int oz) {
        EnumFacing xDirection = ox < 0 ? EnumFacing.WEST : EnumFacing.EAST;
        EnumFacing zDirection = oz < 0 ? EnumFacing.NORTH : EnumFacing.SOUTH;
        BlockPos xPos = pos.offset(xDirection);
        BlockPos zPos = pos.offset(zDirection);
        BlockPos diagonalPos = xPos.offset(zDirection);

        float xHeight = fluidHeightAt(world, xPos, kind);
        float zHeight = fluidHeightAt(world, zPos, kind);
        if (xHeight >= 1.0F || zHeight >= 1.0F) {
            return 1.0F;
        }

        float weightedHeight = 0.0F;
        float totalWeight = 0.0F;
        if (xHeight > 0.0F || zHeight > 0.0F) {
            float diagonalHeight = fluidHeightAt(world, diagonalPos, kind);
            if (diagonalHeight >= 1.0F) {
                return 1.0F;
            }
            float[] diagonal = addWeightedHeight(
                    weightedHeight, totalWeight, diagonalHeight);
            weightedHeight = diagonal[0];
            totalWeight = diagonal[1];
        }

        float[] center = addWeightedHeight(weightedHeight, totalWeight, ownHeight);
        weightedHeight = center[0];
        totalWeight = center[1];

        float[] x = addWeightedHeight(weightedHeight, totalWeight, xHeight);
        weightedHeight = x[0];
        totalWeight = x[1];

        float[] z = addWeightedHeight(weightedHeight, totalWeight, zHeight);
        weightedHeight = z[0];
        totalWeight = z[1];

        return totalWeight <= 0.0F ? 0.0F : weightedHeight / totalWeight;
    }

    private static float[] addWeightedHeight(
            float accumulatedHeight, float accumulatedWeight, float height) {
        if (height < 0.0F) {
            return new float[] { accumulatedHeight, accumulatedWeight };
        }
        float weight = height >= 0.8F ? 10.0F : 1.0F;
        return new float[] {
                accumulatedHeight + height * weight,
                accumulatedWeight + weight
        };
    }

    private static float getFluidColumnFloor(
            List<AxisAlignedBB> boxes, BlockPos pos, double worldX, double worldZ) {
        float floor = 0.0F;
        for (AxisAlignedBB box : boxes) {
            if (worldX <= box.minX + EDGE_EPSILON
                    || worldX >= box.maxX - EDGE_EPSILON
                    || worldZ <= box.minZ + EDGE_EPSILON
                    || worldZ >= box.maxZ - EDGE_EPSILON) {
                continue;
            }
            if (box.minY <= pos.getY() + EDGE_EPSILON) {
                floor = Math.max(floor, (float) (box.maxY - pos.getY()));
            }
        }
        return MathHelper.clamp(floor, 0.0F, 1.0F);
    }

    private static float clampHeight(float value, float floor, float ceiling) {
        return Math.max(floor, Math.min(ceiling, value));
    }

    private static float bilerpHeight(
            float northWest, float northEast, float southEast, float southWest,
            double x, double z) {
        float north = (float) (northWest + (northEast - northWest) * x);
        float south = (float) (southWest + (southEast - southWest) * x);
        return (float) (north + (south - north) * z);
    }

    private static float[] topUvAt(float[] uv, double x, double z) {
        float northU = lerp(uv[0], uv[6], (float) x);
        float northV = lerp(uv[1], uv[7], (float) x);
        float southU = lerp(uv[2], uv[4], (float) x);
        float southV = lerp(uv[3], uv[5], (float) x);
        return new float[] {
                lerp(northU, southU, (float) z),
                lerp(northV, southV, (float) z)
        };
    }

    private static float lerp(float first, float second, float delta) {
        return first + (second - first) * delta;
    }

    private static float[] getTopUV(
            TextureAtlasSprite[] sprites, net.minecraft.util.math.Vec3d direction) {
        float[] uv = new float[8];
        if (direction == null || (direction.x == 0.0D && direction.z == 0.0D)) {
            TextureAtlasSprite still = sprites[0];
            uv[0] = still.getInterpolatedU(0.0D);
            uv[1] = still.getInterpolatedV(0.0D);
            uv[2] = still.getInterpolatedU(0.0D);
            uv[3] = still.getInterpolatedV(16.0D);
            uv[4] = still.getInterpolatedU(16.0D);
            uv[5] = still.getInterpolatedV(16.0D);
            uv[6] = still.getInterpolatedU(16.0D);
            uv[7] = still.getInterpolatedV(0.0D);
        } else {
            TextureAtlasSprite flow = sprites[1];
            float angle = MathHelper.atan2((float) direction.z, (float) direction.x)
                    - ((float) Math.PI / 2.0F);
            float sin = MathHelper.sin(angle) * 0.25F;
            float cos = MathHelper.cos(angle) * 0.25F;
            uv[0] = flow.getInterpolatedU(8.0D + (-cos - sin) * 16.0D);
            uv[1] = flow.getInterpolatedV(8.0D + (-cos + sin) * 16.0D);
            uv[2] = flow.getInterpolatedU(8.0D + (-cos + sin) * 16.0D);
            uv[3] = flow.getInterpolatedV(8.0D + (cos + sin) * 16.0D);
            uv[4] = flow.getInterpolatedU(8.0D + (cos + sin) * 16.0D);
            uv[5] = flow.getInterpolatedV(8.0D + (cos - sin) * 16.0D);
            uv[6] = flow.getInterpolatedU(8.0D + (cos - sin) * 16.0D);
            uv[7] = flow.getInterpolatedV(8.0D + (-cos - sin) * 16.0D);
        }

        float centerU = (uv[0] + uv[2] + uv[4] + uv[6]) * 0.25F;
        float centerV = (uv[1] + uv[3] + uv[5] + uv[7]) * 0.25F;
        for (int i = 0; i < 8; i += 2) {
            uv[i] += (centerU - uv[i]) * UV_SHRINK;
            uv[i + 1] += (centerV - uv[i + 1]) * UV_SHRINK;
        }
        return uv;
    }

    private static int[] getLightColor(World world, BlockPos pos) {
        int current = world.getCombinedLight(pos, 0);
        int above = world.isBlockLoaded(pos.up())
                ? world.getCombinedLight(pos.up(), 0) : current;
        int sky = Math.max((current >> 16) & 0xFFFF, (above >> 16) & 0xFFFF);
        int block = Math.max(current & 0xFFFF, above & 0xFFFF);
        return new int[] { sky, block };
    }

    private static List<Double> coordinateEdges(
            List<AxisAlignedBB> first, List<AxisAlignedBB> second,
            BlockPos origin, boolean xAxis) {
        TreeSet<Double> edges = new TreeSet<Double>();
        edges.add(Double.valueOf(0.0D));
        edges.add(Double.valueOf(1.0D));
        addCoordinateEdges(edges, first, origin, xAxis);
        addCoordinateEdges(edges, second, origin, xAxis);
        return new ArrayList<Double>(edges);
    }

    private static void addCoordinateEdges(
            TreeSet<Double> edges, List<AxisAlignedBB> boxes,
            BlockPos origin, boolean xAxis) {
        if (boxes == null) {
            return;
        }
        for (AxisAlignedBB box : boxes) {
            double min = xAxis ? box.minX - origin.getX() : box.minZ - origin.getZ();
            double max = xAxis ? box.maxX - origin.getX() : box.maxZ - origin.getZ();
            if (min > EDGE_EPSILON && min < 1.0D - EDGE_EPSILON) {
                edges.add(Double.valueOf(min));
            }
            if (max > EDGE_EPSILON && max < 1.0D - EDGE_EPSILON) {
                edges.add(Double.valueOf(max));
            }
        }
    }

    private static List<Double> heightEdges(
            List<AxisAlignedBB> first, List<AxisAlignedBB> second,
            BlockPos origin, double maxHeight, double firstHeight, double secondHeight) {
        TreeSet<Double> edges = new TreeSet<Double>();
        edges.add(Double.valueOf(0.0D));
        edges.add(Double.valueOf(maxHeight));
        addHeightEdge(edges, firstHeight, maxHeight);
        addHeightEdge(edges, secondHeight, maxHeight);
        addVerticalEdges(edges, first, origin, maxHeight);
        addVerticalEdges(edges, second, origin, maxHeight);
        return new ArrayList<Double>(edges);
    }

    private static void addHeightEdge(TreeSet<Double> edges, double value, double maxHeight) {
        if (value > EDGE_EPSILON && value < maxHeight - EDGE_EPSILON) {
            edges.add(Double.valueOf(value));
        }
    }

    private static void addVerticalEdges(
            TreeSet<Double> edges, List<AxisAlignedBB> boxes,
            BlockPos origin, double maxHeight) {
        if (boxes == null) {
            return;
        }
        for (AxisAlignedBB box : boxes) {
            addHeightEdge(edges, box.minY - origin.getY(), maxHeight);
            addHeightEdge(edges, box.maxY - origin.getY(), maxHeight);
        }
    }

    private static void emitVertex(
            BufferBuilder buffer,
            double x, double y, double z,
            float u, float v,
            float red, float green, float blue, int alpha,
            int skyLight, int blockLight) {
        // DefaultVertexFormats.BLOCK in 1.12.2 has position, color, UV0 and
        // lightmap UV1. It has no normal element; do not call BufferBuilder.normal().
        buffer.pos(x, y, z)
                .color((int) (red * 255.0F), (int) (green * 255.0F),
                        (int) (blue * 255.0F), alpha)
                .tex((double) u, (double) v)
                .lightmap(skyLight, blockLight)
                .endVertex();
    }

    private static final class RenderCell {
        private final BlockPos pos;
        private final FluidStateBridge fluid;
        private final double distanceSq;

        private RenderCell(BlockPos pos, FluidStateBridge fluid, double distanceSq) {
            this.pos = pos;
            this.fluid = fluid;
            this.distanceSq = distanceSq;
        }
    }
}
