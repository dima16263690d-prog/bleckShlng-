package com.dima16263690d.bleckshlng.client.render;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.lang.reflect.Method;
import java.util.TreeSet;

import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.dima16263690d.bleckshlng.fluid.network.ClientFluidStateCache;
import com.dima16263690d.bleckshlng.fluid.core.FluidState;
import com.dima16263690d.bleckshlng.fluid.state.FluidStateBridge;
import com.dima16263690d.bleckshlng.fluid.state.FluidStateMapper;
import com.dima16263690d.bleckshlng.fluid.core.FluidKind;
import com.dima16263690d.bleckshlng.fluid.core.Fluids;
import com.dima16263690d.bleckshlng.fluid.core.ModernFluidEngine;
import com.dima16263690d.bleckshlng.fluid.core.FluidVector;
import com.dima16263690d.bleckshlng.fluid.render.FluidOcclusionRect;
import com.dima16263690d.bleckshlng.fluid.render.FluidOcclusionShape;
import com.dima16263690d.bleckshlng.fluid.render.FluidOcclusionShapeAdapter;

/**
 * Emits modern water/lava quads into the chunk BufferBuilder.
 * Called from WaterloggedChunkRenderHooks during the TRANSLUCENT chunk pass.
 */
@SideOnly(Side.CLIENT)
public final class WaterloggedChunkMesher {
    private WaterloggedChunkMesher() {
    }

    public static boolean tessellateFluid(IBlockAccess access, IBlockState state,
                                          BlockPos pos, BufferBuilder buffer) {
        if (access == null || state == null || pos == null || buffer == null
                || !(state.getBlock() instanceof BlockLiquid)) {
            return false;
        }
        FluidState fluid = fromLegacy(FluidStateMapper.fromBlockState(state));
        if (fluid == null) {
            return false;
        }
        int before = buffer.getVertexCount();
        tessellate(access, state, pos, fluid, buffer);
        return buffer.getVertexCount() > before;
    }

    /** Compatibility overload: legacy persistence is converted at the render boundary. */
    public static void tessellate(IBlockAccess access, BlockPos pos,
                                  FluidStateBridge fluid, BufferBuilder buffer) {
        if (access == null || pos == null) {
            return;
        }
        tessellate(access, access.getBlockState(pos), pos, fromLegacy(fluid), buffer);
    }

    /** Compatibility overload retaining the original host state. */
    public static void tessellate(IBlockAccess access, IBlockState host,
                                  BlockPos pos, FluidStateBridge fluid,
                                  BufferBuilder buffer) {
        tessellate(access, host, pos, fromLegacy(fluid), buffer);
    }

    public static void tessellate(IBlockAccess access, BlockPos pos,
                                  FluidState fluid, BufferBuilder buffer) {
        if (access == null || pos == null) {
            return;
        }
        tessellate(access, access.getBlockState(pos), pos, fluid, buffer);
    }

    /**
     * Host state is supplied separately because the fluid view intentionally
     * exposes a virtual water state at waterlogged neighbor positions.
     */
    public static void tessellate(IBlockAccess access, IBlockState host,
                                  BlockPos pos, FluidState fluid,
                                  BufferBuilder buffer) {
        if (access == null || host == null || pos == null
                || fluid == null || buffer == null) {
            return;
        }

        float ownHeight = fluidHeight(access, pos, fluid.getKind());
        if (ownHeight <= 0.0F) {
            ownHeight = fluid.getOwnHeight();
        }
        if (ownHeight <= 0.001F) {
            ownHeight = 0.8888889F;
        }

        // A bottom slab occupies y=0..0.5, leaving the upper half for water.
        // A top slab occupies y=0.5..1, leaving only the lower half. The
        // previous implementation applied the bottom-slab geometry to both,
        // which placed water above top slabs instead of beneath them.
        float minY = 0.0F;
        if (host.getBlock() instanceof BlockSlab
                && !((BlockSlab) host.getBlock()).isDouble()) {
            boolean top = host.getValue(BlockSlab.HALF)
                    == BlockSlab.EnumBlockHalf.TOP;
            if (top) {
                ownHeight = Math.min(ownHeight, 0.5F);
            } else {
                minY = 0.5F;
                ownHeight = Math.max(ownHeight, 0.8888889F);
            }
        }
        if (ownHeight <= minY + 0.001F) {
            return;
        }

        // LiquidBlockRenderer 1.18.2 corner order: north-west,
        // north-east, south-east, south-west. Solid non-fluid neighbours
        // contribute -1 and are excluded from the weighted average.
        float hNW = sampleCorner(access, pos, ownHeight, -1, -1, fluid.getKind());
        float hNE = sampleCorner(access, pos, ownHeight, 1, -1, fluid.getKind());
        float hSE = sampleCorner(access, pos, ownHeight, 1, 1, fluid.getKind());
        float hSW = sampleCorner(access, pos, ownHeight, -1, 1, fluid.getKind());
        // Keep the averaged corner heights exactly as LiquidBlockRenderer
        // 1.18.2 does. Do not cap them to a slab's own height: a neighboring
        // full/falling fluid column can legitimately raise one corner to 1.0.
        // The slab's collision boxes clip the hidden portion of the side mesh.
        hNW = Math.max(hNW, minY);
        hNE = Math.max(hNE, minY);
        hSE = Math.max(hSE, minY);
        hSW = Math.max(hSW, minY);

        Minecraft mc = Minecraft.getMinecraft();
        boolean lava = fluid.getKind() == FluidKind.LAVA;
        IBlockState fluidBlockState = (lava ? Blocks.LAVA : Blocks.WATER).getDefaultState();

        // Use the real animated block-atlas sprites, exactly as the vanilla
        // 1.12.2 BlockFluidRenderer does. TextureMap advances each sprite's
        // .mcmeta frames globally; chunk vertices keep stable atlas UVs, so
        // the animation continues without per-frame chunk rebuilds.
        TextureMap atlas = mc.getTextureMapBlocks();
        String stillName = lava
                ? "minecraft:blocks/lava_still" : "minecraft:blocks/water_still";
        String flowName = lava
                ? "minecraft:blocks/lava_flow" : "minecraft:blocks/water_flow";

        TextureAtlasSprite still = atlas.getAtlasSprite(stillName);
        TextureAtlasSprite flow = atlas.getAtlasSprite(flowName);
        if (still == null || "missingno".equals(still.getIconName())) {
            still = mc.getBlockRendererDispatcher()
                    .getBlockModelShapes().getTexture(fluidBlockState);
        }
        if (flow == null || "missingno".equals(flow.getIconName())) {
            flow = still;
        }
        if (still == null || "missingno".equals(still.getIconName())) {
            return;
        }

        int color = lava ? 0xFFFFFF : 0x3F76E4;
        if (!lava) {
            try {
                int tint = mc.getBlockColors().colorMultiplier(
                        fluidBlockState, access, pos, 0);
                if (tint != -1) {
                    color = tint;
                }
            } catch (Throwable ignored) {
            }
        }
        int r = color >> 16 & 255;
        int g = color >> 8 & 255;
        int b = color & 255;
        int a = 255;

        int[] fluidLight = getFluidLight(access, pos);
        int sky = fluidLight[0];
        int block = fluidLight[1];

        double x = pos.getX();
        double y = pos.getY();
        double z = pos.getZ();

        boolean topSlabHost = host.getBlock() instanceof BlockSlab
                && !((BlockSlab) host.getBlock()).isDouble()
                && host.getValue(BlockSlab.HALF) == BlockSlab.EnumBlockHalf.TOP;
        Vec3d flowVector = getFlowVector(access, pos, fluid);
        float flowX = (float) flowVector.x;
        float flowZ = (float) flowVector.z;
        boolean flowingSurface = flowX != 0.0F || flowZ != 0.0F;
        TextureAtlasSprite topSprite = flowingSurface ? flow : still;

        BlockPos abovePos = pos.up();
        IBlockState aboveState = access.getBlockState(abovePos);
        World shapeWorld = access instanceof FluidViewAccess
                ? ((FluidViewAccess) access).getWorld()
                : (access instanceof World ? (World) access : Minecraft.getMinecraft().world);
        FluidOcclusionShape aboveShape = FluidOcclusionShapeAdapter.getFaceShape(
                access, shapeWorld, abovePos, aboveState, EnumFacing.DOWN);
        boolean topOccluded =
                FluidOcclusionShapeAdapter.isFaceFullyCovered(aboveShape);
        boolean sameFluidAbove = isSameFluid(access, pos.up(), fluid.getKind());
        boolean renderTop = !topSlabHost && !topOccluded && !sameFluidAbove;
        if (renderTop) {
            // LiquidBlockRenderer 1.18.2 lowers the top vertices by 0.001
            // before emitting both the top quad and its adjoining side walls.
            hNW = Math.max(minY, hNW - 0.001F);
            hNE = Math.max(minY, hNE - 0.001F);
            hSE = Math.max(minY, hSE - 0.001F);
            hSW = Math.max(minY, hSW - 0.001F);
            // Forge 1.12.2 requires setSprite() for each sprite written
            // into a chunk BufferBuilder. UVs alone are insufficient for
            // animated/multi-texture chunk tracking.
            setSprite(buffer, topSprite);
            topQuad(buffer, x, y, z, hNW, hNE, hSE, hSW,
                    topSprite, flowX, flowZ, flowingSurface, r, g, b, a, sky, block, false);
            if (shouldRenderBackwardTopFace(access, abovePos, fluid.getKind())) {
                // LiquidBlockRenderer also emits the reverse winding so the
                // surface remains visible from below when the surrounding
                // 3x3 area above is not completely sealed.
                topQuad(buffer, x, y, z, hNW, hNE, hSE, hSW,
                        topSprite, flowX, flowZ, flowingSurface, r, g, b, a, sky, block, true);
            }
        }

        BlockPos belowPos = pos.down();
        IBlockState belowState = access.getBlockState(belowPos);
        FluidOcclusionShape belowShape = FluidOcclusionShapeAdapter.getFaceShape(
                access, shapeWorld, belowPos, belowState, EnumFacing.UP);
        boolean bottomOccluded =
                FluidOcclusionShapeAdapter.isFaceFullyCovered(belowShape);

        boolean renderBottom = minY <= 0.001F && !bottomOccluded
                && !isSameFluid(access, belowPos, fluid.getKind());
        float sideMinY = minY;
        if (renderBottom) {
            // The 1.18.2 renderer leaves a 0.001 inset on the bottom face
            // and starts adjoining side walls at the same height.
            sideMinY = Math.max(sideMinY, 0.001F);
            double yy = y + sideMinY;
            int[] bottomLight = getFluidLight(access, belowPos);
            setSprite(buffer, still);
            vert(buffer, x + 0, yy, z + 1, still.getMinU(), still.getMaxV(),
                    shade(r, 0.5F), shade(g, 0.5F), shade(b, 0.5F), a,
                    bottomLight[0], bottomLight[1]);
            vert(buffer, x + 0, yy, z + 0, still.getMinU(), still.getMinV(),
                    shade(r, 0.5F), shade(g, 0.5F), shade(b, 0.5F), a,
                    bottomLight[0], bottomLight[1]);
            vert(buffer, x + 1, yy, z + 0, still.getMaxU(), still.getMinV(),
                    shade(r, 0.5F), shade(g, 0.5F), shade(b, 0.5F), a,
                    bottomLight[0], bottomLight[1]);
            vert(buffer, x + 1, yy, z + 1, still.getMaxU(), still.getMaxV(),
                    shade(r, 0.5F), shade(g, 0.5F), shade(b, 0.5F), a,
                    bottomLight[0], bottomLight[1]);
        }

        side(buffer, access, host, pos, EnumFacing.NORTH, x, y, z, hNW, hNE, sideMinY, flow, r, g, b, a, fluid.getKind());
        side(buffer, access, host, pos, EnumFacing.SOUTH, x, y, z, hSW, hSE, sideMinY, flow, r, g, b, a, fluid.getKind());
        side(buffer, access, host, pos, EnumFacing.WEST, x, y, z, hNW, hSW, sideMinY, flow, r, g, b, a, fluid.getKind());
        side(buffer, access, host, pos, EnumFacing.EAST, x, y, z, hNE, hSE, sideMinY, flow, r, g, b, a, fluid.getKind());

        // BlockFluidRenderer 1.12.2 clears the active sprite after each liquid.
        setSprite(buffer, null);
    }


    /**
     * OptiFine adds BufferBuilder#setSprite for animated/multi-texture tracking.
     * The stock Forge 1.12.2 BufferBuilder does not expose that method, so keep
     * the renderer compatible with both environments without a hard dependency.
     * In vanilla Forge the atlas UV coordinates are sufficient for rendering.
     */
    private static final Method BUFFER_SET_SPRITE = findBufferSetSprite();

    private static Method findBufferSetSprite() {
        try {
            return BufferBuilder.class.getMethod("setSprite", TextureAtlasSprite.class);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static void setSprite(BufferBuilder buffer, TextureAtlasSprite sprite) {
        if (BUFFER_SET_SPRITE == null) {
            return;
        }
        try {
            BUFFER_SET_SPRITE.invoke(buffer, sprite);
        } catch (ReflectiveOperationException ignored) {
            // Sprite tracking is an optional OptiFine optimization.
        }
    }

    private static void side(BufferBuilder buffer, IBlockAccess access,
                             IBlockState host, BlockPos pos,
                             EnumFacing face, double x, double y, double z,
                             float h0, float h1, float minY, TextureAtlasSprite sprite,
                             int r, int g, int b, int a,
                             FluidKind kind) {
        if (isSameFluid(access, pos.offset(face), kind)) {
            return;
        }
        if (h0 <= minY && h1 <= minY) {
            return;
        }

        BlockPos neighborPos = pos.offset(face);
        IBlockState neighborState = access.getBlockState(neighborPos);
        // HalfTransparentBlock in 1.18.2 maps to the glass/ice
        // materials in 1.12.2; LeavesBlock remains a separate case.
        // Portals also inherit BlockBreakable in legacy code, so do not
        // classify every BlockBreakable as a water-overlay surface.
        boolean overlay = kind == FluidKind.WATER
                && (neighborState.getBlock() instanceof net.minecraft.block.BlockLeaves
                || neighborState.getMaterial() == Material.GLASS
                || neighborState.getMaterial() == Material.ICE);
        List<SideOcclusionRect> occlusions =
                collectSideOcclusions(access, host, pos, neighborState,
                        neighborPos, face, minY, overlay);

        TextureAtlasSprite sideSprite = sprite;
        if (overlay) {
            TextureAtlasSprite waterOverlay = Minecraft.getMinecraft().getTextureMapBlocks()
                    .getAtlasSprite("minecraft:blocks/water_overlay");
            if (waterOverlay != null && !"missingno".equals(waterOverlay.getIconName())) {
                sideSprite = waterOverlay;
            } else {
                overlay = false;
            }
        }

        float shade = face == EnumFacing.NORTH || face == EnumFacing.SOUTH
                ? 0.8F : 0.6F;
        int[] sideLight = getFluidLight(access, pos);
        int sky = sideLight[0];
        int block = sideLight[1];

        // Match vanilla BlockFluidRenderer: bind the sprite before
        // writing this face so Forge can track it in chunk buffers.
        setSprite(buffer, sideSprite);
        emitClippedSide(buffer, face, x, y, z, h0, h1, minY,
                sideSprite, occlusions, r, g, b, a, sky, block, shade, !overlay);
    }

    /**
     * Returns the actual collision rectangles touching this shared face.
     * The old implementation only checked whether the whole face was covered.
     * A slab covers only half of its side, so that boolean test left the entire
     * water quad visible and produced a blue square around submerged slabs.
     */
    private static List<SideOcclusionRect> collectSideOcclusions(
            IBlockAccess access, IBlockState host, BlockPos pos,
            IBlockState neighbor, BlockPos neighborPos,
            EnumFacing face, float minY, boolean preserveNeighborOverlay) {
        List<SideOcclusionRect> result = new ArrayList<SideOcclusionRect>();
        World world = null;
        if (access instanceof FluidViewAccess) {
            world = ((FluidViewAccess) access).getWorld();
        } else if (access instanceof World) {
            world = (World) access;
        } else {
            world = Minecraft.getMinecraft().world;
        }

        if (world != null && world.isBlockLoaded(pos) && world.isBlockLoaded(neighborPos)) {
            IBlockState neighborShape = access instanceof FluidViewAccess
                    ? ((FluidViewAccess) access).getOriginalBlockState(neighborPos)
                    : neighbor;

            addSideOcclusions(result,
                    FluidOcclusionShapeAdapter.getFaceShape(
                            access, world, pos, host, face), minY);

            if (!preserveNeighborOverlay) {
                addSideOcclusions(result,
                        FluidOcclusionShapeAdapter.getFaceShape(
                                access, world, neighborPos, neighborShape,
                                face.getOpposite()), minY);
            }
            return result;
        }

        // No live World is available during an unusual chunk-rebuild context.
        // Use the platform face contract only; do not substitute collision boxes.
        if (host.getBlock().getBlockFaceShape(access, host, pos, face)
                == BlockFaceShape.SOLID) {
            result.add(new SideOcclusionRect(0.0D, 1.0D, minY, 1.0D));
        }
        if (!preserveNeighborOverlay
                && neighbor.getBlock().getBlockFaceShape(
                        access, neighbor, neighborPos, face.getOpposite())
                == BlockFaceShape.SOLID) {
            result.add(new SideOcclusionRect(0.0D, 1.0D, minY, 1.0D));
        }
        return result;
    }

    private static void addSideOcclusions(List<SideOcclusionRect> target,
                                          FluidOcclusionShape shape,
                                          float minY) {
        if (shape == null || shape.isEmpty()) {
            return;
        }
        for (FluidOcclusionRect rectangle : shape.getRectangles()) {
            double minV = Math.max((double) minY,
                    Math.min(1.0D, rectangle.getMinV()));
            double maxV = Math.max((double) minY,
                    Math.min(1.0D, rectangle.getMaxV()));
            if (rectangle.getMaxU() - rectangle.getMinU() > 1.0E-5D
                    && maxV - minV > 1.0E-5D) {
                target.add(new SideOcclusionRect(
                        rectangle.getMinU(), rectangle.getMaxU(), minV, maxV));
            }
        }
    }

    /**
     * The old BlockState#canOcclude adapter now consults an independent
     * face-occlusion shape, never the block collision shape.
     */
    private static boolean canOccludeFluidFace(IBlockAccess access, BlockPos pos,
                                               IBlockState state, EnumFacing face) {
        World world = access instanceof FluidViewAccess
                ? ((FluidViewAccess) access).getWorld()
                : (access instanceof World ? (World) access : null);
        return !FluidOcclusionShapeAdapter.getFaceShape(
                access, world, pos, state, face).isEmpty();
    }

    private static boolean touchesFace(AxisAlignedBB box, BlockPos pos, EnumFacing face) {
        final double epsilon = 1.0E-5D;
        switch (face) {
            case DOWN: return box.minY <= pos.getY() + epsilon;
            case UP: return box.maxY >= pos.getY() + 1.0D - epsilon;
            case NORTH: return box.minZ <= pos.getZ() + epsilon;
            case SOUTH: return box.maxZ >= pos.getZ() + 1.0D - epsilon;
            case WEST: return box.minX <= pos.getX() + epsilon;
            case EAST: return box.maxX >= pos.getX() + 1.0D - epsilon;
            default: return false;
        }
    }

    private static void emitClippedSide(BufferBuilder buffer, EnumFacing face,
                                        double x, double y, double z,
                                        float h0, float h1, float minY,
                                        TextureAtlasSprite sprite,
                                        List<SideOcclusionRect> occlusions,
                                        int r, int g, int b, int a,
                                        int sky, int block, float shade,
                                        boolean doubleSided) {
        TreeSet<Double> uEdges = new TreeSet<Double>();
        uEdges.add(Double.valueOf(0.0D));
        uEdges.add(Double.valueOf(1.0D));
        for (SideOcclusionRect rect : occlusions) {
            uEdges.add(Double.valueOf(rect.minU));
            uEdges.add(Double.valueOf(rect.maxU));
            addSurfaceCrossing(uEdges, rect.minY, h0, h1, rect.minU, rect.maxU);
            addSurfaceCrossing(uEdges, rect.maxY, h0, h1, rect.minU, rect.maxU);
        }

        List<Double> edges = new ArrayList<Double>(uEdges);
        int sr = shade(r, shade);
        int sg = shade(g, shade);
        int sb = shade(b, shade);

        for (int i = 0; i + 1 < edges.size(); i++) {
            double u0 = edges.get(i).doubleValue();
            double u1 = edges.get(i + 1).doubleValue();
            if (u1 - u0 <= 1.0E-5D) {
                continue;
            }
            double middleU = (u0 + u1) * 0.5D;
            double middleHeight = interpolateHeight(h0, h1, middleU);
            if (middleHeight <= minY + 1.0E-5D) {
                continue;
            }

            List<VerticalInterval> blocked = new ArrayList<VerticalInterval>();
            for (SideOcclusionRect rect : occlusions) {
                if (middleU > rect.minU - 1.0E-5D
                        && middleU < rect.maxU + 1.0E-5D) {
                    double low = Math.max((double) minY, rect.minY);
                    double high = Math.min(middleHeight, rect.maxY);
                    if (high - low > 1.0E-5D) {
                        blocked.add(new VerticalInterval(low, high));
                    }
                }
            }
            Collections.sort(blocked, new Comparator<VerticalInterval>() {
                @Override
                public int compare(VerticalInterval first, VerticalInterval second) {
                    return Double.compare(first.minY, second.minY);
                }
            });

            List<VerticalInterval> merged = new ArrayList<VerticalInterval>();
            for (VerticalInterval interval : blocked) {
                if (merged.isEmpty()) {
                    merged.add(new VerticalInterval(interval.minY, interval.maxY));
                } else {
                    VerticalInterval previous = merged.get(merged.size() - 1);
                    if (interval.minY > previous.maxY + 1.0E-5D) {
                        merged.add(new VerticalInterval(interval.minY, interval.maxY));
                    } else if (interval.maxY > previous.maxY) {
                        previous.maxY = interval.maxY;
                    }
                }
            }

            double cursor = minY;
            for (VerticalInterval interval : merged) {
                if (interval.minY > cursor + 1.0E-5D) {
                    emitVisibleSideStrip(buffer, face, x, y, z, u0, u1,
                            h0, h1, cursor, interval.minY, sprite,
                            sr, sg, sb, a, sky, block, doubleSided);
                }
                cursor = Math.max(cursor, interval.maxY);
            }
            emitVisibleSideStrip(buffer, face, x, y, z, u0, u1,
                    h0, h1, cursor, Double.POSITIVE_INFINITY, sprite,
                    sr, sg, sb, a, sky, block, doubleSided);
        }
    }

    private static void addSurfaceCrossing(TreeSet<Double> edges, double height,
                                           float h0, float h1,
                                           double minU, double maxU) {
        double delta = (double) h1 - (double) h0;
        if (Math.abs(delta) <= 1.0E-7D) {
            return;
        }
        double u = (height - (double) h0) / delta;
        if (u > minU + 1.0E-5D && u < maxU - 1.0E-5D
                && u > 1.0E-5D && u < 1.0D - 1.0E-5D) {
            edges.add(Double.valueOf(u));
        }
    }

    private static double interpolateHeight(float h0, float h1, double u) {
        return (double) h0 + ((double) h1 - (double) h0) * u;
    }

    private static void emitVisibleSideStrip(BufferBuilder buffer, EnumFacing face,
                                             double x, double y, double z,
                                             double u0, double u1,
                                             float h0, float h1,
                                             double bottom, double topLimit,
                                             TextureAtlasSprite sprite,
                                             int r, int g, int b, int a,
                                             int sky, int block,
                                             boolean doubleSided) {
        double top0 = Math.min(interpolateHeight(h0, h1, u0), topLimit);
        double top1 = Math.min(interpolateHeight(h0, h1, u1), topLimit);
        if (top0 < bottom) top0 = bottom;
        if (top1 < bottom) top1 = bottom;
        if (top0 - bottom <= 1.0E-5D && top1 - bottom <= 1.0E-5D) {
            return;
        }

        boolean reverseU = face == EnumFacing.SOUTH || face == EnumFacing.WEST;
        float texU0 = sprite.getInterpolatedU((float) ((reverseU ? 1.0D - u0 : u0) * 8.0D));
        float texU1 = sprite.getInterpolatedU((float) ((reverseU ? 1.0D - u1 : u1) * 8.0D));
        float texVTop0 = sprite.getInterpolatedV((float) ((1.0D - top0) * 8.0D));
        float texVTop1 = sprite.getInterpolatedV((float) ((1.0D - top1) * 8.0D));
        float texVBottom = sprite.getInterpolatedV((float) ((1.0D - bottom) * 8.0D));

        boolean reverseOrder = face == EnumFacing.SOUTH || face == EnumFacing.WEST;
        if (!reverseOrder) {
            sideVertex(buffer, face, x, y, z, u0, top0, texU0, texVTop0, r, g, b, a, sky, block);
            sideVertex(buffer, face, x, y, z, u1, top1, texU1, texVTop1, r, g, b, a, sky, block);
            sideVertex(buffer, face, x, y, z, u1, bottom, texU1, texVBottom, r, g, b, a, sky, block);
            sideVertex(buffer, face, x, y, z, u0, bottom, texU0, texVBottom, r, g, b, a, sky, block);
        } else {
            sideVertex(buffer, face, x, y, z, u1, top1, texU1, texVTop1, r, g, b, a, sky, block);
            sideVertex(buffer, face, x, y, z, u0, top0, texU0, texVTop0, r, g, b, a, sky, block);
            sideVertex(buffer, face, x, y, z, u0, bottom, texU0, texVBottom, r, g, b, a, sky, block);
            sideVertex(buffer, face, x, y, z, u1, bottom, texU1, texVBottom, r, g, b, a, sky, block);
        }

        if (doubleSided) {
            if (!reverseOrder) {
                sideVertex(buffer, face, x, y, z, u0, bottom, texU0, texVBottom, r, g, b, a, sky, block);
                sideVertex(buffer, face, x, y, z, u1, bottom, texU1, texVBottom, r, g, b, a, sky, block);
                sideVertex(buffer, face, x, y, z, u1, top1, texU1, texVTop1, r, g, b, a, sky, block);
                sideVertex(buffer, face, x, y, z, u0, top0, texU0, texVTop0, r, g, b, a, sky, block);
            } else {
                sideVertex(buffer, face, x, y, z, u1, bottom, texU1, texVBottom, r, g, b, a, sky, block);
                sideVertex(buffer, face, x, y, z, u0, bottom, texU0, texVBottom, r, g, b, a, sky, block);
                sideVertex(buffer, face, x, y, z, u0, top0, texU0, texVTop0, r, g, b, a, sky, block);
                sideVertex(buffer, face, x, y, z, u1, top1, texU1, texVTop1, r, g, b, a, sky, block);
            }
        }
    }

    private static void sideVertex(BufferBuilder buffer, EnumFacing face,
                                  double x, double y, double z,
                                  double u, double height,
                                  float texU, float texV,
                                  int r, int g, int b, int a,
                                  int sky, int block) {
        double px;
        double pz;
        switch (face) {
            // Match LiquidBlockRenderer 1.18.2: move each vertical face
            // 0.001 blocks into the fluid cell to prevent z-fighting with
            // the neighboring block's side plane.
            case NORTH:
                px = x + u;
                pz = z + 0.001D;
                break;
            case SOUTH:
                px = x + u;
                pz = z + 1.0D - 0.001D;
                break;
            case WEST:
                px = x + 0.001D;
                pz = z + u;
                break;
            case EAST:
                px = x + 1.0D - 0.001D;
                pz = z + u;
                break;
            default:
                return;
        }
        vert(buffer, px, y + height, pz, texU, texV,
                r, g, b, a, sky, block);
    }

    private static final class SideOcclusionRect {
        private final double minU;
        private final double maxU;
        private final double minY;
        private final double maxY;

        private SideOcclusionRect(double minU, double maxU, double minY, double maxY) {
            this.minU = minU;
            this.maxU = maxU;
            this.minY = minY;
            this.maxY = maxY;
        }
    }

    private static final class VerticalInterval {
        private final double minY;
        private double maxY;

        private VerticalInterval(double minY, double maxY) {
            this.minY = minY;
            this.maxY = maxY;
        }
    }

    private static int[] getFluidLight(IBlockAccess access, BlockPos pos) {
        int current = access.getCombinedLight(pos, 0);
        int above = access.getCombinedLight(pos.up(), 0);
        int sky = Math.max(current >> 16 & 65535, above >> 16 & 65535);
        int block = Math.max(current & 65535, above & 65535);
        return new int[] {sky, block};
    }

    /**
     * 1.18.2 top-face UV projection. Flowing surfaces use the flow sprite
     * and orient its UVs from the fluid flow vector, rather than rotating a
     * square still texture.
     */
    private static void topQuad(BufferBuilder buffer, double x, double y, double z,
                                 float hNW, float hNE, float hSE, float hSW,
                                 TextureAtlasSprite sprite, float flowX, float flowZ,
                                 boolean flowing, int r, int g, int b, int a,
                                 int sky, int block, boolean reverseWinding) {
        float uNW;
        float vNW;
        float uSW;
        float vSW;
        float uSE;
        float vSE;
        float uNE;
        float vNE;

        if (!flowing) {
            uNW = sprite.getMinU();
            vNW = sprite.getMinV();
            uSW = sprite.getMinU();
            vSW = sprite.getMaxV();
            uSE = sprite.getMaxU();
            vSE = sprite.getMaxV();
            uNE = sprite.getMaxU();
            vNE = sprite.getMinV();
        } else {
            float angle = (float) Math.atan2(flowZ, flowX) - (float) (Math.PI / 2.0D);
            float sin = (float) Math.sin(angle) * 0.25F;
            float cos = (float) Math.cos(angle) * 0.25F;
            uNW = sprite.getInterpolatedU(8.0F + (-cos - sin) * 16.0F);
            vNW = sprite.getInterpolatedV(8.0F + (-cos + sin) * 16.0F);
            uSW = sprite.getInterpolatedU(8.0F + (-cos + sin) * 16.0F);
            vSW = sprite.getInterpolatedV(8.0F + (cos + sin) * 16.0F);
            uSE = sprite.getInterpolatedU(8.0F + (cos + sin) * 16.0F);
            vSE = sprite.getInterpolatedV(8.0F + (cos - sin) * 16.0F);
            uNE = sprite.getInterpolatedU(8.0F + (cos - sin) * 16.0F);
            vNE = sprite.getInterpolatedV(8.0F + (-cos - sin) * 16.0F);

            float centerU = (uNW + uSW + uSE + uNE) * 0.25F;
            float centerV = (vNW + vSW + vSE + vNE) * 0.25F;
            float spriteWidth = (float) sprite.getIconWidth()
                    / Math.max(1.0E-6F, sprite.getMaxU() - sprite.getMinU());
            float spriteHeight = (float) sprite.getIconHeight()
                    / Math.max(1.0E-6F, sprite.getMaxV() - sprite.getMinV());
            float shrink = 4.0F / Math.max(spriteWidth, spriteHeight);
            uNW = centerU + (uNW - centerU) * (1.0F - shrink);
            uSW = centerU + (uSW - centerU) * (1.0F - shrink);
            uSE = centerU + (uSE - centerU) * (1.0F - shrink);
            uNE = centerU + (uNE - centerU) * (1.0F - shrink);
            vNW = centerV + (vNW - centerV) * (1.0F - shrink);
            vSW = centerV + (vSW - centerV) * (1.0F - shrink);
            vSE = centerV + (vSE - centerV) * (1.0F - shrink);
            vNE = centerV + (vNE - centerV) * (1.0F - shrink);
        }

        if (!reverseWinding) {
            vert(buffer, x, y + hNW, z, uNW, vNW, r, g, b, a, sky, block);
            vert(buffer, x, y + hSW, z + 1, uSW, vSW, r, g, b, a, sky, block);
            vert(buffer, x + 1, y + hSE, z + 1, uSE, vSE, r, g, b, a, sky, block);
            vert(buffer, x + 1, y + hNE, z, uNE, vNE, r, g, b, a, sky, block);
        } else {
            vert(buffer, x, y + hNW, z, uNW, vNW, r, g, b, a, sky, block);
            vert(buffer, x + 1, y + hNE, z, uNE, vNE, r, g, b, a, sky, block);
            vert(buffer, x + 1, y + hSE, z + 1, uSE, vSE, r, g, b, a, sky, block);
            vert(buffer, x, y + hSW, z + 1, uSW, vSW, r, g, b, a, sky, block);
        }
    }

    private static boolean shouldRenderBackwardTopFace(
            IBlockAccess access, BlockPos abovePos,
            FluidKind kind) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos samplePos = abovePos.add(dx, 0, dz);
                IBlockState state = access.getBlockState(samplePos);
                FluidState neighbor = getFluidState(access, samplePos, state);
                if (neighbor != null && neighbor.getKind() == kind) {
                    continue;
                }
                if (state.isOpaqueCube()) {
                    continue;
                }
                return true;
            }
        }
        return false;
    }

    private static Vec3d getFlowVector(IBlockAccess access, BlockPos pos,
                                       FluidState current) {
        // Use the same FlowingFluid#getFlow implementation as simulation
        // and entity acceleration. Chunk rebuilds still receive FluidViewAccess
        // so waterlogged FluidState remains separate from the host BlockState.
        World world = access instanceof FluidViewAccess
                ? ((FluidViewAccess) access).getWorld()
                : (access instanceof World ? (World) access : null);
        if (world != null && world.isBlockLoaded(pos)) {
            FluidVector modern = ModernFluidEngine.getFlow(world, pos);
            return new Vec3d(modern.getX(), modern.getY(), modern.getZ());
        }

        double x = 0.0D;
        double z = 0.0D;
        float currentHeight = current.getOwnHeight();
        for (EnumFacing direction : EnumFacing.Plane.HORIZONTAL) {
            BlockPos neighborPos = pos.offset(direction);
            IBlockState neighborBlock = access.getBlockState(neighborPos);
            FluidState neighbor = getFluidState(access, neighborPos, neighborBlock);
            if (neighbor != null && !current.isSameKind(neighbor)) {
                continue;
            }

            float neighborHeight = neighbor == null ? 0.0F : neighbor.getOwnHeight();
            float difference = 0.0F;
            if (neighborHeight == 0.0F && !neighborBlock.getMaterial().blocksMovement()) {
                BlockPos belowPos = neighborPos.down();
                IBlockState belowBlock = access.getBlockState(belowPos);
                FluidState below = getFluidState(access, belowPos, belowBlock);
                if (below != null && current.isSameKind(below)) {
                    neighborHeight = below.getOwnHeight();
                    difference = currentHeight - (neighborHeight - 0.8888889F);
                }
            } else if (neighborHeight > 0.0F) {
                difference = currentHeight - neighborHeight;
            }

            x += direction.getFrontOffsetX() * (double) difference;
            z += direction.getFrontOffsetZ() * (double) difference;
        }

        Vec3d vector = new Vec3d(x, 0.0D, z);
        if (current.isFalling()) {
            for (EnumFacing direction : EnumFacing.Plane.HORIZONTAL) {
                BlockPos sidePos = pos.offset(direction);
                IBlockState side = access.getBlockState(sidePos);
                FluidState sideFluid = getFluidState(access, sidePos, side);
                if (sideFluid != null && current.isSameKind(sideFluid)) {
                    continue;
                }
                if (side.getMaterial() == Material.ICE) {
                    continue;
                }

                boolean solidFace = side.isSideSolid(access, sidePos, direction);
                BlockPos sideAbovePos = sidePos.up();
                IBlockState sideAbove = access.getBlockState(sideAbovePos);
                FluidState aboveFluid = getFluidState(access, sideAbovePos, sideAbove);
                if (aboveFluid != null && current.isSameKind(aboveFluid)) {
                    continue;
                }
                solidFace |= sideAbove.isSideSolid(access, sideAbovePos, direction);
                if (solidFace) {
                    vector = vector.normalize().addVector(0.0D, -6.0D, 0.0D);
                    break;
                }
            }
        }
        return vector.normalize();
    }

    private static FluidState getFluidState(IBlockAccess access, BlockPos pos,
                                                   IBlockState state) {
        if (access instanceof FluidViewAccess) {
            FluidStateBridge cached = ClientFluidStateCache.get(
                    ((FluidViewAccess) access).getDimension(), pos.toLong());
            if (cached != null) {
                return fromLegacy(cached);
            }
        }
        if (access instanceof World) {
            return fromLegacy(FluidStateMapper.fromWorld((World) access, pos, state));
        }
        return state.getMaterial() == Material.WATER
                || state.getMaterial() == Material.LAVA
                ? fromLegacy(FluidStateMapper.fromBlockState(state)) : null;
    }

    private static FluidState fromLegacy(FluidStateBridge legacy) {
        if (legacy == null) {
            return null;
        }
        if (legacy.getKind() == FluidStateBridge.FluidKind.WATER) {
            return legacy.isSource()
                    ? Fluids.waterSource()
                    : Fluids.WATER.flowingState(legacy.getAmount(), legacy.isFalling());
        }
        return legacy.isSource()
                ? Fluids.lavaSource()
                : Fluids.LAVA.flowingState(legacy.getAmount(), legacy.isFalling());
    }

    private static int shade(int channel, float factor) {
        return Math.min(255, Math.max(0, Math.round((float) channel * factor)));
    }

    private static float sampleCorner(IBlockAccess access, BlockPos pos,
                                      float own, int ox, int oz,
                                      FluidKind kind) {
        BlockPos north = pos.add(0, 0, -1);
        BlockPos south = pos.add(0, 0, 1);
        BlockPos east = pos.add(1, 0, 0);
        BlockPos west = pos.add(-1, 0, 0);
        float hNorth = fluidHeight(access, north, kind);
        float hSouth = fluidHeight(access, south, kind);
        float hEast = fluidHeight(access, east, kind);
        float hWest = fluidHeight(access, west, kind);
        BlockPos diagonal = pos.add(ox, 0, oz);
        float hDiagonal = fluidHeight(access, diagonal, kind);
        float first = ox < 0 ? hWest : hEast;
        float second = oz < 0 ? hNorth : hSouth;

        if (first >= 1.0F || second >= 1.0F) {
            return 1.0F;
        }

        float[] weighted = new float[2];
        if (first > 0.0F || second > 0.0F) {
            if (hDiagonal >= 1.0F) {
                return 1.0F;
            }
            addWeightedHeight(weighted, hDiagonal);
        }
        addWeightedHeight(weighted, own);
        addWeightedHeight(weighted, first);
        addWeightedHeight(weighted, second);
        return weighted[1] == 0.0F ? own : weighted[0] / weighted[1];
    }

    private static void addWeightedHeight(float[] weighted, float height) {
        if (height >= 0.8F) {
            weighted[0] += height * 10.0F;
            weighted[1] += 10.0F;
        } else if (height >= 0.0F) {
            weighted[0] += height;
            weighted[1] += 1.0F;
        }
    }

    private static float fluidHeight(IBlockAccess access, BlockPos pos,
                                      FluidKind kind) {
        IBlockState state = access.getBlockState(pos);
        FluidState fluid = getFluidState(access, pos, state);
        if (fluid != null && fluid.getKind() == kind) {
            // FluidViewAccess applies host-specific clipping (for example,
            // water stored below a top slab). Do not return 1.0 here before
            // that geometry-aware height has been resolved.
            if (access instanceof FluidViewAccess) {
                return ((FluidViewAccess) access).getFluidSurfaceHeight(pos);
            }
            if (isSameFluid(access, pos.up(), kind)) {
                return 1.0F;
            }
            if (access instanceof World) {
                return fluid.getOwnHeight();
            }
            return fluid.getOwnHeight();
        }
        // LiquidBlockRenderer#getHeight 1.18.2 distinguishes solid material
        // from merely movement-blocking material: solid -> -1, non-solid -> 0.
        return state.getMaterial().isSolid() ? -1.0F : 0.0F;
    }

    private static boolean isSameFluid(IBlockAccess access, BlockPos pos,
                                       FluidKind kind) {
        IBlockState state = access.getBlockState(pos);
        FluidState fluid = getFluidState(access, pos, state);
        return fluid != null && fluid.getKind() == kind;
    }

    private static void vert(BufferBuilder buffer,
                             double x, double y, double z,
                             float u, float v,
                             int r, int g, int b, int a, int sky, int block) {
        // DefaultVertexFormats.BLOCK in Forge 1.12.2 has no NORMAL element.
        // Calling BufferBuilder.normal() here wraps its element cursor to
        // POSITION and overwrites this vertex's coordinates with the normal.
        // Vanilla fluid rendering uses the BLOCK format without normal().
        buffer.pos(x, y, z).color(r, g, b, a).tex(u, v)
                .lightmap(sky, block).endVertex();
    }
}
