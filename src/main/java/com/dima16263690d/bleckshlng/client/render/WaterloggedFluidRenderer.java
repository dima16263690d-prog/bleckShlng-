package com.dima16263690d.bleckshlng.client.render;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

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
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.lwjgl.opengl.GL11;

import com.dima16263690d.bleckshlng.fluid.geometry.FluidShapeCalculator;
import com.dima16263690d.bleckshlng.fluid.network.ClientFluidStateCache;
import com.dima16263690d.bleckshlng.fluid.state.FluidHeightCalculator;
import com.dima16263690d.bleckshlng.fluid.state.FluidStateBridge;
import com.dima16263690d.bleckshlng.fluid.state.FluidStateMapper;

/**
 * Renders the visible water surface in the free volume of legacy waterlogged
 * blocks. Surface and side patches are split at the host collision-box
 * boundaries, preserving narrow openings in stairs, slabs, fences and panes.
 *
 * This is a surface renderer; it does not replace vanilla fluid block models.
 */
@SideOnly(Side.CLIENT)
public final class WaterloggedFluidRenderer {
    private static final int CHUNK_RADIUS = 2;
    private static final double RENDER_DISTANCE_SQ = 40.0D * 40.0D;

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

                        double surfaceY = pos.getY() + (double) height - 0.002D;
                        hasVertices |= renderSurfaceMask(buffer, world, pos,
                                hostState, surfaceY, cameraX, cameraY, cameraZ, sprite);
                        hasVertices |= renderSideMask(buffer, world, pos, hostState,
                                entry.getValue(), height, cameraX, cameraY, cameraZ, sprite);
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
        int packedLight = world.getCombinedLight(pos, 0);
        int skyLight = (packedLight >> 16) & 0xFFFF;
        int blockLight = packedLight & 0xFFFF;
        List<AxisAlignedBB> boxes = FluidShapeCalculator.getCollisionBoxes(
                world, pos, state);
        List<Double> xEdges = coordinateEdges(boxes, null, pos, true);
        List<Double> zEdges = coordinateEdges(boxes, null, pos, false);
        float fluidHeight = (float) (surfaceY - pos.getY() + 0.002D);
        float uSpan = sprite.getMaxU() - sprite.getMinU();
        float vSpan = sprite.getMaxV() - sprite.getMinV();

        // Use the collision-box boundaries instead of a fixed 4x4 mask.
        // This keeps thin panes, fence posts, walls and stair sub-shapes from
        // falling between sample points.
        for (int x = 0; x + 1 < xEdges.size(); x++) {
            double x0 = xEdges.get(x).doubleValue();
            double x1 = xEdges.get(x + 1).doubleValue();
            if (x1 - x0 <= 1.0E-5D) continue;

            for (int z = 0; z + 1 < zEdges.size(); z++) {
                double z0 = zEdges.get(z).doubleValue();
                double z1 = zEdges.get(z + 1).doubleValue();
                if (z1 - z0 <= 1.0E-5D) continue;

                double centerX = pos.getX() + (x0 + x1) * 0.5D;
                double centerZ = pos.getZ() + (z0 + z1) * 0.5D;
                if (!FluidShapeCalculator.containsFluidPoint(
                        world, pos, state, centerX, surfaceY, centerZ,
                        fluidHeight)) {
                    continue;
                }

                float u0 = sprite.getMinU() + (float) x0 * uSpan;
                float u1 = sprite.getMinU() + (float) x1 * uSpan;
                float v0 = sprite.getMinV() + (float) z0 * vSpan;
                float v1 = sprite.getMinV() + (float) z1 * vSpan;

                addVertex(buffer, pos.getX() + x0 - cameraX,
                        surfaceY - cameraY, pos.getZ() + z0 - cameraZ,
                        u0, v0, 0.28F, 0.52F, 0.92F, 0.62F);
                addVertex(buffer, pos.getX() + x0 - cameraX,
                        surfaceY - cameraY, pos.getZ() + z1 - cameraZ,
                        u0, v1, 0.28F, 0.52F, 0.92F, 0.62F, skyLight, blockLight);
                addVertex(buffer, pos.getX() + x1 - cameraX,
                        surfaceY - cameraY, pos.getZ() + z1 - cameraZ,
                        u1, v1, 0.28F, 0.52F, 0.92F, 0.62F, skyLight, blockLight);
                addVertex(buffer, pos.getX() + x1 - cameraX,
                        surfaceY - cameraY, pos.getZ() + z0 - cameraZ,
                        u1, v0, 0.28F, 0.52F, 0.92F, 0.62F, skyLight, blockLight);
                wrote = true;
            }
        }
        return wrote;
    }

    /**
     * Renders the vertical water faces exposed by the host block's collision
     * shape. This is required for 1.13-style waterlogging: water occupies all
     * free volumes and is visible through every non-solid face.
     */
    private static boolean renderSideMask(BufferBuilder buffer, World world,
                                          BlockPos pos, IBlockState state,
                                          FluidStateBridge currentFluid, float height,
                                          double cameraX, double cameraY, double cameraZ,
                                          TextureAtlasSprite sprite) {
        if (height <= 0.0F) return false;

        boolean wrote = false;
        int packedLight = world.getCombinedLight(pos, 0);
        int skyLight = (packedLight >> 16) & 0xFFFF;
        int blockLight = packedLight & 0xFFFF;
        double xBase = pos.getX();
        double yBase = pos.getY();
        double zBase = pos.getZ();
        float uSpan = sprite.getMaxU() - sprite.getMinU();
        float vSpan = sprite.getMaxV() - sprite.getMinV();
        List<AxisAlignedBB> currentBoxes = FluidShapeCalculator.getCollisionBoxes(
                world, pos, state);

        for (EnumFacing direction : EnumFacing.Plane.HORIZONTAL) {
            BlockPos neighborPos = pos.offset(direction);
            if (!world.isBlockLoaded(neighborPos)) continue;

            IBlockState neighborState = world.getBlockState(neighborPos);
            FluidStateBridge neighborFluid = FluidStateMapper.fromWorld(
                    world, neighborPos, neighborState);
            boolean sameNeighborFluid = neighborFluid != null
                    && neighborFluid.getKind() == currentFluid.getKind();
            float neighborHeight = sameNeighborFluid
                    ? FluidHeightCalculator.getHeight(world, neighborPos, neighborFluid)
                    : 1.0F;

            List<AxisAlignedBB> neighborBoxes =
                    FluidShapeCalculator.getCollisionBoxes(world, neighborPos, neighborState);
            boolean useX = direction.getAxis() == EnumFacing.Axis.Z;
            List<Double> uEdges = coordinateEdges(
                    currentBoxes, neighborBoxes, pos, useX);
            List<Double> yEdges = heightEdges(currentBoxes, neighborBoxes, pos,
                    Math.max(height, neighborHeight), height, neighborHeight);

            for (int u = 0; u + 1 < uEdges.size(); u++) {
                double u0 = uEdges.get(u).doubleValue();
                double u1 = uEdges.get(u + 1).doubleValue();
                if (u1 - u0 <= 1.0E-5D) continue;
                double uCenter = (u0 + u1) * 0.5D;

                for (int y = 0; y + 1 < yEdges.size(); y++) {
                    double y0 = yEdges.get(y).doubleValue();
                    double y1 = yEdges.get(y + 1).doubleValue();
                    if (y1 - y0 <= 1.0E-5D) continue;
                    double yCenter = (y0 + y1) * 0.5D;
                    double currentX;
                    double currentZ;
                    double neighborX;
                    double neighborZ;

                    switch (direction) {
                        case NORTH:
                            currentX = xBase + uCenter;
                            currentZ = zBase + 0.001D;
                            neighborX = currentX;
                            neighborZ = neighborPos.getZ() + 0.999D;
                            break;
                        case SOUTH:
                            currentX = xBase + uCenter;
                            currentZ = zBase + 0.999D;
                            neighborX = currentX;
                            neighborZ = neighborPos.getZ() + 0.001D;
                            break;
                        case WEST:
                            currentX = xBase + 0.001D;
                            currentZ = zBase + uCenter;
                            neighborX = neighborPos.getX() + 0.999D;
                            neighborZ = currentZ;
                            break;
                        case EAST:
                            currentX = xBase + 0.999D;
                            currentZ = zBase + uCenter;
                            neighborX = neighborPos.getX() + 0.001D;
                            neighborZ = currentZ;
                            break;
                        default:
                            continue;
                    }

                    double currentY = yBase + yCenter;
                    if (!FluidShapeCalculator.containsFluidPoint(
                            world, pos, state, currentX, currentY, currentZ, height)) {
                        continue;
                    }

                    boolean neighborFree = FluidShapeCalculator.containsFluidPoint(
                            world, neighborPos, neighborState, neighborX, currentY,
                            neighborZ, neighborHeight);
                    if (sameNeighborFluid
                            && currentY < neighborPos.getY() + (double) neighborHeight
                            && neighborFree) {
                        continue;
                    }
                    if (neighborState.getMaterial().blocksMovement() && !neighborFree) {
                        continue;
                    }

                    float texU0 = sprite.getMinU() + (float) u0 * uSpan;
                    float texU1 = sprite.getMinU() + (float) u1 * uSpan;
                    float texV0 = sprite.getMinV()
                            + (float) (y0 / Math.max(0.001F, height)) * vSpan;
                    float texV1 = sprite.getMinV()
                            + (float) (y1 / Math.max(0.001F, height)) * vSpan;
                    double x0;
                    double x1;
                    double z0;
                    double z1;

                    if (direction.getAxis() == EnumFacing.Axis.X) {
                        x0 = xBase + (direction == EnumFacing.WEST ? 0.0D : 1.0D);
                        x1 = x0;
                        z0 = zBase + u0;
                        z1 = zBase + u1;
                    } else {
                        x0 = xBase + u0;
                        x1 = xBase + u1;
                        z0 = zBase + (direction == EnumFacing.NORTH ? 0.0D : 1.0D);
                        z1 = z0;
                    }

                    switch (direction) {
                        case NORTH:
                            addSideVertex(buffer, x0-cameraX, yBase+y0-cameraY,
                                    z0-cameraZ, texU0, texV0, direction, skyLight, blockLight);
                            addSideVertex(buffer, x1-cameraX, yBase+y0-cameraY,
                                    z1-cameraZ, texU1, texV0, direction);
                            addSideVertex(buffer, x1-cameraX, yBase+y1-cameraY,
                                    z1-cameraZ, texU1, texV1, direction);
                            addSideVertex(buffer, x0-cameraX, yBase+y1-cameraY,
                                    z0-cameraZ, texU0, texV1, direction);
                            break;
                        case SOUTH:
                            addSideVertex(buffer, x1-cameraX, yBase+y0-cameraY,
                                    z1-cameraZ, texU1, texV0, direction);
                            addSideVertex(buffer, x0-cameraX, yBase+y0-cameraY,
                                    z0-cameraZ, texU0, texV0, direction);
                            addSideVertex(buffer, x0-cameraX, yBase+y1-cameraY,
                                    z0-cameraZ, texU0, texV1, direction);
                            addSideVertex(buffer, x1-cameraX, yBase+y1-cameraY,
                                    z1-cameraZ, texU1, texV1, direction);
                            break;
                        case WEST:
                            addSideVertex(buffer, x0-cameraX, yBase+y0-cameraY,
                                    z1-cameraZ, texU1, texV0, direction);
                            addSideVertex(buffer, x1-cameraX, yBase+y0-cameraY,
                                    z0-cameraZ, texU0, texV0, direction);
                            addSideVertex(buffer, x1-cameraX, yBase+y1-cameraY,
                                    z0-cameraZ, texU0, texV1, direction);
                            addSideVertex(buffer, x0-cameraX, yBase+y1-cameraY,
                                    z1-cameraZ, texU1, texV1, direction);
                            break;
                        case EAST:
                            addSideVertex(buffer, x1-cameraX, yBase+y0-cameraY,
                                    z0-cameraZ, texU0, texV0, direction);
                            addSideVertex(buffer, x0-cameraX, yBase+y0-cameraY,
                                    z1-cameraZ, texU1, texV0, direction);
                            addSideVertex(buffer, x0-cameraX, yBase+y1-cameraY,
                                    z1-cameraZ, texU1, texV1, direction);
                            addSideVertex(buffer, x1-cameraX, yBase+y1-cameraY,
                                    z0-cameraZ, texU0, texV1, direction);
                            break;
                        default:
                            break;
                    }
                    wrote = true;
                }
            }
        }
        return wrote;
    }

    private static List<Double> coordinateEdges(List<AxisAlignedBB> first,
                                                  List<AxisAlignedBB> second,
                                                  BlockPos origin, boolean xAxis) {
        TreeSet<Double> edges = new TreeSet<Double>();
        edges.add(Double.valueOf(0.0D));
        edges.add(Double.valueOf(1.0D));
        addCoordinateEdges(edges, first, origin, xAxis);
        addCoordinateEdges(edges, second, origin, xAxis);
        return new ArrayList<Double>(edges);
    }

    private static void addCoordinateEdges(TreeSet<Double> edges,
                                           List<AxisAlignedBB> boxes,
                                           BlockPos origin, boolean xAxis) {
        if (boxes == null) return;
        for (AxisAlignedBB box : boxes) {
            double min = xAxis ? box.minX - origin.getX() : box.minZ - origin.getZ();
            double max = xAxis ? box.maxX - origin.getX() : box.maxZ - origin.getZ();
            if (min > 1.0E-5D && min < 1.0D - 1.0E-5D) {
                edges.add(Double.valueOf(min));
            }
            if (max > 1.0E-5D && max < 1.0D - 1.0E-5D) {
                edges.add(Double.valueOf(max));
            }
        }
    }

    private static List<Double> heightEdges(List<AxisAlignedBB> first,
                                             List<AxisAlignedBB> second,
                                             BlockPos origin, double maxHeight,
                                             double firstHeight, double secondHeight) {
        TreeSet<Double> edges = new TreeSet<Double>();
        edges.add(Double.valueOf(0.0D));
        edges.add(Double.valueOf(maxHeight));
        addHeightEdge(edges, firstHeight, maxHeight);
        addHeightEdge(edges, secondHeight, maxHeight);
        addVerticalEdges(edges, first, origin, maxHeight);
        addVerticalEdges(edges, second, origin, maxHeight);
        return new ArrayList<Double>(edges);
    }

    private static void addHeightEdge(TreeSet<Double> edges, double value,
                                      double maxHeight) {
        if (value > 1.0E-5D && value < maxHeight - 1.0E-5D) {
            edges.add(Double.valueOf(value));
        }
    }

    private static void addVerticalEdges(TreeSet<Double> edges,
                                         List<AxisAlignedBB> boxes,
                                         BlockPos origin, double maxHeight) {
        if (boxes == null) return;
        for (AxisAlignedBB box : boxes) {
            addHeightEdge(edges, box.minY - origin.getY(), maxHeight);
            addHeightEdge(edges, box.maxY - origin.getY(), maxHeight);
        }
    }
    private static void addSideVertex(BufferBuilder buffer, double x, double y, double z,
                                      float u, float v, EnumFacing normal,
                                      int skyLight, int blockLight) {
        buffer.pos(x, y, z)
                .color(0.28F, 0.52F, 0.92F, 0.62F)
                .tex(u, v)
                .lightmap(skyLight, blockLight)
                .normal((float) normal.getFrontOffsetX(),
                        (float) normal.getFrontOffsetY(),
                        (float) normal.getFrontOffsetZ())
                .endVertex();
    }

    private static void addVertex(BufferBuilder buffer, double x, double y, double z,
                                  float u, float v, float red, float green,
                                  float blue, float alpha, int skyLight, int blockLight) {
        buffer.pos(x,y,z)
                .color(red,green,blue,alpha)
                .tex(u,v)
                .lightmap(skyLight, blockLight)
                .normal(0.0F,1.0F,0.0F)
                .endVertex();
    }
}
