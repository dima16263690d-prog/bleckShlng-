package com.dima16263690d.bleckshlng.client.render;

import java.lang.reflect.Method;

import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
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
import com.dima16263690d.bleckshlng.fluid.render.FluidOcclusionShapeAdapter;
import com.dima16263690d.bleckshlng.fluid.render.FluidUvUtils;

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
            // The sidecar may have been evicted from the client cache between
            // chunk collection and tessellation. Fall back to the exact
            // FluidState amount, never to a fabricated source height.
            ownHeight = fluid.getOwnHeight();
        }

        // LiquidBlockRenderer 1.18.2 uses the FluidState height unchanged.
        // The host slab/stair geometry hides the part of the fluid that lies
        // inside the block; the renderer must not shift or clamp the fluid.
        final float minY = 0.0F;
        if (ownHeight <= 0.001F) {
            return;
        }

        // Exact LiquidBlockRenderer rule: if this fluid column reaches the
        // cell ceiling (same fluid above), all four corners are 1.0. Averaging
        // them with lower neighbours creates the false pyramid/diamond tops
        // visible on falling water and lava.
        float hNW;
        float hNE;
        float hSE;
        float hSW;
        if (ownHeight >= 1.0F) {
            hNW = 1.0F;
            hNE = 1.0F;
            hSE = 1.0F;
            hSW = 1.0F;
        } else {
            hNW = sampleCorner(access, pos, ownHeight, -1, -1, fluid.getKind());
            hNE = sampleCorner(access, pos, ownHeight, 1, -1, fluid.getKind());
            hSE = sampleCorner(access, pos, ownHeight, 1, 1, fluid.getKind());
            hSW = sampleCorner(access, pos, ownHeight, -1, 1, fluid.getKind());
        }

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

        Vec3d flowVector = getFlowVector(access, pos, fluid);
        float flowX = (float) flowVector.x;
        float flowZ = (float) flowVector.z;
        boolean flowingSurface = flowX != 0.0F || flowZ != 0.0F;
        TextureAtlasSprite topSprite = flowingSurface ? flow : still;

        BlockPos abovePos = pos.up();
        IBlockState aboveState = access.getBlockState(abovePos);
        // Face occlusion reads the same IBlockAccess snapshot as this chunk mesh.
        float minCornerHeight = Math.min(Math.min(hNW, hNE), Math.min(hSE, hSW));
        boolean sameFluidAbove = isSameFluid(access, abovePos, fluid.getKind());
        boolean topOccluded = FluidOcclusionShapeAdapter.isFaceOccludedByState(
                access, null, abovePos, aboveState,
                EnumFacing.UP, minCornerHeight);
        // A source inside an upper slab reaches 8/9 height, but the host's
        // upper half hides that surface. Do not emit a coplanar water top at
        // y=0.5: it would z-fight with the slab underside and flicker.
        boolean topCoveredByHost =
                FluidOcclusionShapeAdapter.isFluidSurfaceBuriedBySlab(
                        access, pos, host, minCornerHeight);
        boolean renderTop = !sameFluidAbove && !topOccluded && !topCoveredByHost;
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
                    topSprite, still, flowX, flowZ, flowingSurface,
                    r, g, b, a, sky, block, false);
            if (shouldRenderBackwardTopFace(access, abovePos, fluid.getKind())) {
                // LiquidBlockRenderer also emits the reverse winding so the
                // surface remains visible from below when the surrounding
                // 3x3 area above is not completely sealed.
                topQuad(buffer, x, y, z, hNW, hNE, hSE, hSW,
                        topSprite, still, flowX, flowZ, flowingSurface,
                        r, g, b, a, sky, block, true);
            }
        }

        BlockPos belowPos = pos.down();
        IBlockState belowState = access.getBlockState(belowPos);
        boolean sameFluidBelow = isSameFluid(access, belowPos, fluid.getKind());
        boolean bottomSelfOccluded = FluidOcclusionShapeAdapter.isFaceOccludedByState(
                access, null, pos, host, EnumFacing.UP, 1.0F);
        boolean bottomOccluded = FluidOcclusionShapeAdapter.isFaceOccludedByState(
                access, null, belowPos, belowState,
                EnumFacing.DOWN, 0.8888889F);

        // LiquidBlockRenderer.shouldRenderFace(DOWN) checks the host's
        // opposite face and whether the neighbour contains the same fluid.
        boolean renderBottom = !bottomSelfOccluded
                && !sameFluidBelow && !bottomOccluded;
        float sideMinY = renderBottom ? 0.001F : 0.0F;
        if (renderBottom) {
            // The 1.18.2 renderer leaves a 0.001 inset on the bottom face
            // and starts adjoining side walls at the same height.
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
        if (h0 <= minY && h1 <= minY) {
            return;
        }

        BlockPos neighborPos = pos.offset(face);
        IBlockState neighborState = access.getBlockState(neighborPos);
        // Neighbor occlusion uses the same chunk snapshot.

        // Exact LiquidBlockRenderer.shouldRenderFace() checks:
        // 1) the host does not occlude its own opposite face;
        // 2) the adjacent cell does not contain the same fluid.
        if (isSameFluid(access, neighborPos, kind)
                || FluidOcclusionShapeAdapter.isFaceOccludedByState(
                        access, null, pos, host, face.getOpposite(), 1.0F)) {
            return;
        }

        // The original renderer tests the adjacent block's occlusion shape
        // against the liquid side prism up to max(h0,h1). It does not split
        // the quad using collision boxes; block depth hides partial overlap.
        if (FluidOcclusionShapeAdapter.isFaceOccludedByState(
                access, null, neighborPos, neighborState,
                face, Math.max(h0, h1))) {
            return;
        }

        // 1.18.2 uses the water overlay only for leaves and half-transparent
        // blocks. Glass/ice are the closest legacy 1.12.2 equivalents.
        boolean overlay = kind == FluidKind.WATER
                && (neighborState.getBlock() instanceof net.minecraft.block.BlockLeaves
                || neighborState.getMaterial() == Material.GLASS
                || neighborState.getMaterial() == Material.ICE);

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

        float faceShade = face == EnumFacing.NORTH || face == EnumFacing.SOUTH
                ? 0.8F : 0.6F;
        int[] sideLight = getFluidLight(access, pos);

        setSprite(buffer, sideSprite);
        emitVisibleSideStrip(buffer, face, x, y, z, 0.0D, 1.0D,
                h0, h1, minY, Double.POSITIVE_INFINITY,
                sideSprite, shade(r, faceShade), shade(g, faceShade),
                shade(b, faceShade), a,
                sideLight[0], sideLight[1], !overlay);
    }

    /**
     * Linear interpolation of the two fluid surface heights along one
     * horizontal block edge. LiquidBlockRenderer uses the same interpolation
     * when emitting the side quad between adjacent corner heights.
     */
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
        // LiquidBlockRenderer uses a fixed V=8 bottom texture coordinate;
        // its 0.001 geometric inset does not shift the texture vertically.
        float texVBottom = sprite.getInterpolatedV(8.0F);

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
                                 TextureAtlasSprite sprite, TextureAtlasSprite stillSprite,
                                 float flowX, float flowZ, boolean flowing,
                                 int r, int g, int b, int a,
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

        }

        // LiquidBlockRenderer applies the center inset to still and flowing
        // top textures alike. The inset amount is calculated from the still sprite.
        float inset = FluidUvUtils.getInset(
                stillSprite.getIconWidth(), stillSprite.getIconHeight(),
                stillSprite.getMinU(), stillSprite.getMaxU(),
                stillSprite.getMinV(), stillSprite.getMaxV());
        float[] topUv = FluidUvUtils.applyCenterInset(
                new float[] {uNW, vNW, uSW, vSW, uSE, vSE, uNE, vNE}, inset);
        uNW = topUv[0]; vNW = topUv[1];
        uSW = topUv[2]; vSW = topUv[3];
        uSE = topUv[4]; vSE = topUv[5];
        uNE = topUv[6]; vNE = topUv[7];

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
        // Flow is recalculated from the same chunk snapshot as geometry and lighting.

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
