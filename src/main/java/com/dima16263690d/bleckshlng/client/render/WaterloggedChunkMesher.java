package com.dima16263690d.bleckshlng.client.render;

import net.minecraft.block.BlockLiquid;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.dima16263690d.bleckshlng.fluid.geometry.FluidShapeCalculator;
import com.dima16263690d.bleckshlng.fluid.network.ClientFluidStateCache;
import com.dima16263690d.bleckshlng.fluid.state.FluidHeightCalculator;
import com.dima16263690d.bleckshlng.fluid.state.FluidStateBridge;
import com.dima16263690d.bleckshlng.fluid.state.FluidStateMapper;


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
        FluidStateBridge fluid = FluidStateMapper.fromBlockState(state);
        if (fluid == null) {
            return false;
        }
        int before = buffer.getVertexCount();
        tessellate(access, state, pos, fluid, buffer);
        return buffer.getVertexCount() > before;
    }

    public static void tessellate(IBlockAccess access, BlockPos pos,
                                  FluidStateBridge fluid, BufferBuilder buffer) {
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
                                  BlockPos pos, FluidStateBridge fluid,
                                  BufferBuilder buffer) {
        if (access == null || host == null || pos == null
                || fluid == null || buffer == null) {
            return;
        }

        float ownHeight = fluid.getOwnHeight();
        if (access instanceof FluidViewAccess) {
            ownHeight = ((FluidViewAccess) access).getFluidSurfaceHeight(pos);
        } else if (access instanceof World) {
            ownHeight = FluidHeightCalculator.getHeight((World) access, pos, fluid);
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
        hNW = Math.min(ownHeight, Math.max(hNW, minY));
        hNE = Math.min(ownHeight, Math.max(hNE, minY));
        hSE = Math.min(ownHeight, Math.max(hSE, minY));
        hSW = Math.min(ownHeight, Math.max(hSW, minY));

        Minecraft mc = Minecraft.getMinecraft();
        boolean lava = fluid.getKind() == FluidStateBridge.FluidKind.LAVA;
        IBlockState fluidBlockState = (lava ? Blocks.LAVA : Blocks.WATER).getDefaultState();
        TextureAtlasSprite still = mc.getBlockRendererDispatcher()
                .getBlockModelShapes().getTexture(fluidBlockState);
        TextureAtlasSprite flow = still;
        try {
            TextureAtlasSprite f = mc.getTextureMapBlocks().getAtlasSprite(
                    lava ? "minecraft:blocks/lava_flow" : "minecraft:blocks/water_flow");
            if (f != null && !"missingno".equals(f.getIconName())) {
                flow = f;
            }
        } catch (Throwable ignored) {
        }
        if (still == null) {
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
        boolean flowingSurface = flowX * flowX + flowZ * flowZ > 1.0E-4F;
        TextureAtlasSprite topSprite = flowingSurface ? flow : still;

        boolean topOccluded = false;
        BlockPos abovePos = pos.up();
        IBlockState aboveState = access.getBlockState(abovePos);
        if (access instanceof FluidViewAccess) {
            World world = ((FluidViewAccess) access).getWorld();
            if (world != null && world.isBlockLoaded(abovePos)) {
                IBlockState above = world.getBlockState(abovePos);
                topOccluded = FluidShapeCalculator.isFaceFullyCovered(
                        world, abovePos, above, EnumFacing.DOWN);
            } else {
                topOccluded = aboveState.isSideSolid(access, abovePos, EnumFacing.DOWN);
            }
        } else {
            topOccluded = aboveState.isSideSolid(access, abovePos, EnumFacing.DOWN);
        }
        if (!topSlabHost && !topOccluded && !isSameFluid(access, pos.up(), fluid.getKind())) {
            topQuad(buffer, x, y, z, hNW, hNE, hSE, hSW,
                    topSprite, flowX, flowZ, flowingSurface, r, g, b, a, sky, block);
        }

        boolean bottomOccluded = false;
        BlockPos belowPos = pos.down();
        IBlockState belowState = access.getBlockState(belowPos);
        if (access instanceof FluidViewAccess) {
            World world = ((FluidViewAccess) access).getWorld();
            if (world != null && world.isBlockLoaded(belowPos)) {
                IBlockState below = world.getBlockState(belowPos);
                bottomOccluded = FluidShapeCalculator.isFaceFullyCovered(
                        world, belowPos, below, EnumFacing.UP);
            } else {
                bottomOccluded = belowState.isSideSolid(access, belowPos, EnumFacing.UP);
            }
        } else {
            bottomOccluded = belowState.isSideSolid(access, belowPos, EnumFacing.UP);
        }
        if (minY <= 0.001F && !bottomOccluded && !isSameFluid(access, pos.down(), fluid.getKind())) {
            double yy = y + minY;
            int bottomLight = access.getCombinedLight(pos.down(), 0);
            vert(buffer, x + 0, yy, z + 1, still.getMinU(), still.getMaxV(),
                    shade(r, 0.5F), shade(g, 0.5F), shade(b, 0.5F), a,
                    bottomLight >> 16 & 65535, bottomLight & 65535);
            vert(buffer, x + 0, yy, z + 0, still.getMinU(), still.getMinV(),
                    shade(r, 0.5F), shade(g, 0.5F), shade(b, 0.5F), a,
                    bottomLight >> 16 & 65535, bottomLight & 65535);
            vert(buffer, x + 1, yy, z + 0, still.getMaxU(), still.getMinV(),
                    shade(r, 0.5F), shade(g, 0.5F), shade(b, 0.5F), a,
                    bottomLight >> 16 & 65535, bottomLight & 65535);
            vert(buffer, x + 1, yy, z + 1, still.getMaxU(), still.getMaxV(),
                    shade(r, 0.5F), shade(g, 0.5F), shade(b, 0.5F), a,
                    bottomLight >> 16 & 65535, bottomLight & 65535);
        }

        side(buffer, access, host, pos, EnumFacing.NORTH, x, y, z, hNW, hNE, minY, flow, r, g, b, a, fluid.getKind());
        side(buffer, access, host, pos, EnumFacing.SOUTH, x, y, z, hSW, hSE, minY, flow, r, g, b, a, fluid.getKind());
        side(buffer, access, host, pos, EnumFacing.WEST, x, y, z, hNW, hSW, minY, flow, r, g, b, a, fluid.getKind());
        side(buffer, access, host, pos, EnumFacing.EAST, x, y, z, hNE, hSE, minY, flow, r, g, b, a, fluid.getKind());
    }

    private static void side(BufferBuilder buffer, IBlockAccess access,
                             IBlockState host, BlockPos pos,
                             EnumFacing face, double x, double y, double z,
                             float h0, float h1, float minY, TextureAtlasSprite sprite,
                             int r, int g, int b, int a,
                             FluidStateBridge.FluidKind kind) {
        if (isSameFluid(access, pos.offset(face), kind)) {
            return;
        }
        if (h0 <= minY && h1 <= minY) {
            return;
        }
        BlockPos neighborPos = pos.offset(face);
        IBlockState neighborState = access.getBlockState(neighborPos);
        if (access instanceof FluidViewAccess) {
            World world = ((FluidViewAccess) access).getWorld();
            if (world != null && world.isBlockLoaded(pos)) {
                float maxHeight = Math.max(h0, h1);
                if (FluidShapeCalculator.isFaceRectangleCovered(
                        world, pos, host, face, minY, maxHeight)) {
                    return;
                }
                if (world.isBlockLoaded(neighborPos)) {
                    IBlockState neighbor = world.getBlockState(neighborPos);
                    if (FluidShapeCalculator.isFaceRectangleCovered(
                            world, neighborPos, neighbor, face.getOpposite(),
                            minY, maxHeight)) {
                        return;
                    }
                }
            } else if (host.isSideSolid(access, pos, face)
                    || neighborState.isSideSolid(access, neighborPos, face.getOpposite())) {
                return;
            }
        } else if (host.isSideSolid(access, pos, face)
                || neighborState.isSideSolid(access, neighborPos, face.getOpposite())) {
            return;
        }
        TextureAtlasSprite sideSprite = sprite;
        boolean overlay = kind == FluidStateBridge.FluidKind.WATER
                && (neighborState.getBlock() instanceof net.minecraft.block.BlockLeaves
                || neighborState.getMaterial() == Material.GLASS);
        if (overlay) {
            TextureAtlasSprite waterOverlay = Minecraft.getMinecraft().getTextureMapBlocks()
                    .getAtlasSprite("minecraft:blocks/water_overlay");
            if (waterOverlay != null && !"missingno".equals(waterOverlay.getIconName())) {
                sideSprite = waterOverlay;
            } else {
                overlay = false;
            }
        }

        float u0 = sideSprite.getInterpolatedU(0.0D);
        float u1 = sideSprite.getInterpolatedU(8.0D);
        float vTop0 = sideSprite.getInterpolatedV(
                (1.0F - MathHelper.clamp(h0, 0.0F, 1.0F)) * 8.0F);
        float vTop1 = sideSprite.getInterpolatedV(
                (1.0F - MathHelper.clamp(h1, 0.0F, 1.0F)) * 8.0F);
        float vBot = sideSprite.getInterpolatedV(8.0D);

        float shade = face == EnumFacing.NORTH || face == EnumFacing.SOUTH
                ? 0.8F : 0.6F;
        int[] sideLight = getFluidLight(access, pos);
        int sky = sideLight[0];
        int block = sideLight[1];
        int sr = shade(r, shade);
        int sg = shade(g, shade);
        int sb = shade(b, shade);
        switch (face) {
            case NORTH:
                vert(buffer, x + 0, y + h0, z + 0, u0, vTop0, sr, sg, sb, a, sky, block);
                vert(buffer, x + 1, y + h1, z + 0, u1, vTop1, sr, sg, sb, a, sky, block);
                vert(buffer, x + 1, y + minY, z + 0, u1, vBot, sr, sg, sb, a, sky, block);
                vert(buffer, x + 0, y + minY, z + 0, u0, vBot, sr, sg, sb, a, sky, block);
                break;
            case SOUTH:
                vert(buffer, x + 1, y + h1, z + 1, u0, vTop1, sr, sg, sb, a, sky, block);
                vert(buffer, x + 0, y + h0, z + 1, u1, vTop0, sr, sg, sb, a, sky, block);
                vert(buffer, x + 0, y + minY, z + 1, u1, vBot, sr, sg, sb, a, sky, block);
                vert(buffer, x + 1, y + minY, z + 1, u0, vBot, sr, sg, sb, a, sky, block);
                break;
            case WEST:
                vert(buffer, x + 0, y + h0, z + 1, u0, vTop0, sr, sg, sb, a, sky, block);
                vert(buffer, x + 0, y + h1, z + 0, u1, vTop1, sr, sg, sb, a, sky, block);
                vert(buffer, x + 0, y + minY, z + 0, u1, vBot, sr, sg, sb, a, sky, block);
                vert(buffer, x + 0, y + minY, z + 1, u0, vBot, sr, sg, sb, a, sky, block);
                break;
            case EAST:
                vert(buffer, x + 1, y + h0, z + 0, u0, vTop0, sr, sg, sb, a, sky, block);
                vert(buffer, x + 1, y + h1, z + 1, u1, vTop1, sr, sg, sb, a, sky, block);
                vert(buffer, x + 1, y + minY, z + 1, u1, vBot, sr, sg, sb, a, sky, block);
                vert(buffer, x + 1, y + minY, z + 0, u0, vBot, sr, sg, sb, a, sky, block);
                break;
            default:
                break;
        }

        // The 1.18.2 renderer emits the reverse side for ordinary fluid
        // walls so translucent water remains visible from both directions.
        // Overlay faces intentionally stay single-sided.
        if (!overlay) {
            switch (face) {
                case NORTH:
                    vert(buffer, x + 0, y + minY, z + 0, u0, vBot, sr, sg, sb, a, sky, block);
                    vert(buffer, x + 1, y + minY, z + 0, u1, vBot, sr, sg, sb, a, sky, block);
                    vert(buffer, x + 1, y + h1, z + 0, u1, vTop1, sr, sg, sb, a, sky, block);
                    vert(buffer, x + 0, y + h0, z + 0, u0, vTop0, sr, sg, sb, a, sky, block);
                    break;
                case SOUTH:
                    vert(buffer, x + 1, y + minY, z + 1, u0, vBot, sr, sg, sb, a, sky, block);
                    vert(buffer, x + 0, y + minY, z + 1, u1, vBot, sr, sg, sb, a, sky, block);
                    vert(buffer, x + 0, y + h0, z + 1, u1, vTop0, sr, sg, sb, a, sky, block);
                    vert(buffer, x + 1, y + h1, z + 1, u0, vTop1, sr, sg, sb, a, sky, block);
                    break;
                case WEST:
                    vert(buffer, x + 0, y + minY, z + 1, u0, vBot, sr, sg, sb, a, sky, block);
                    vert(buffer, x + 0, y + minY, z + 0, u1, vBot, sr, sg, sb, a, sky, block);
                    vert(buffer, x + 0, y + h1, z + 0, u1, vTop1, sr, sg, sb, a, sky, block);
                    vert(buffer, x + 0, y + h0, z + 1, u0, vTop0, sr, sg, sb, a, sky, block);
                    break;
                case EAST:
                    vert(buffer, x + 1, y + minY, z + 0, u0, vBot, sr, sg, sb, a, sky, block);
                    vert(buffer, x + 1, y + minY, z + 1, u1, vBot, sr, sg, sb, a, sky, block);
                    vert(buffer, x + 1, y + h1, z + 1, u1, vTop1, sr, sg, sb, a, sky, block);
                    vert(buffer, x + 1, y + h0, z + 0, u0, vTop0, sr, sg, sb, a, sky, block);
                    break;
                default:
                    break;
            }
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
                                 int sky, int block) {
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

        vert(buffer, x, y + hNW, z, uNW, vNW, r, g, b, a, sky, block);
        vert(buffer, x, y + hSW, z + 1, uSW, vSW, r, g, b, a, sky, block);
        vert(buffer, x + 1, y + hSE, z + 1, uSE, vSE, r, g, b, a, sky, block);
        vert(buffer, x + 1, y + hNE, z, uNE, vNE, r, g, b, a, sky, block);
    }

    private static Vec3d getFlowVector(IBlockAccess access, BlockPos pos,
                                       FluidStateBridge current) {
        double x = 0.0D;
        double z = 0.0D;
        float currentHeight = current.getOwnHeight();
        for (EnumFacing direction : EnumFacing.Plane.HORIZONTAL) {
            BlockPos neighborPos = pos.offset(direction);
            IBlockState neighborBlock = access.getBlockState(neighborPos);
            FluidStateBridge neighbor = getFluidState(access, neighborPos, neighborBlock);
            if (neighbor != null && !current.isSameKind(neighbor)) {
                continue;
            }

            float neighborHeight = neighbor == null ? 0.0F : neighbor.getOwnHeight();
            float difference = 0.0F;
            if (neighborHeight == 0.0F && !neighborBlock.getMaterial().blocksMovement()) {
                BlockPos belowPos = neighborPos.down();
                IBlockState belowBlock = access.getBlockState(belowPos);
                FluidStateBridge below = getFluidState(access, belowPos, belowBlock);
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
                IBlockState side = access.getBlockState(pos.offset(direction));
                if (side.getMaterial().blocksMovement()) {
                    vector = vector.normalize().addVector(0.0D, -6.0D, 0.0D);
                    break;
                }
            }
        }
        return vector.normalize();
    }

    private static FluidStateBridge getFluidState(IBlockAccess access, BlockPos pos,
                                                   IBlockState state) {
        if (access instanceof FluidViewAccess) {
            FluidStateBridge cached = ClientFluidStateCache.get(
                    ((FluidViewAccess) access).getDimension(), pos.toLong());
            if (cached != null) {
                return cached;
            }
        }
        return state.getMaterial() == Material.WATER
                || state.getMaterial() == Material.LAVA
                ? FluidStateMapper.fromBlockState(state) : null;
    }

    private static int shade(int channel, float factor) {
        return Math.min(255, Math.max(0, Math.round((float) channel * factor)));
    }

    private static float sampleCorner(IBlockAccess access, BlockPos pos,
                                      float own, int ox, int oz,
                                      FluidStateBridge.FluidKind kind) {
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
                                      FluidStateBridge.FluidKind kind) {
        IBlockState state = access.getBlockState(pos);
        FluidStateBridge fluid = getFluidState(access, pos, state);
        if (fluid != null && fluid.getKind() == kind) {
            if (isSameFluid(access, pos.up(), kind)) {
                return 1.0F;
            }
            if (access instanceof FluidViewAccess) {
                return ((FluidViewAccess) access).getFluidSurfaceHeight(pos);
            }
            return fluid.getOwnHeight();
        }
        return state.getMaterial().blocksMovement() ? -1.0F : 0.0F;
    }

    private static boolean isSameFluid(IBlockAccess access, BlockPos pos,
                                       FluidStateBridge.FluidKind kind) {
        IBlockState state = access.getBlockState(pos);
        FluidStateBridge fluid = getFluidState(access, pos, state);
        return fluid != null && fluid.getKind() == kind;
    }

    private static void vert(BufferBuilder buffer,
                             double x, double y, double z,
                             float u, float v,
                             int r, int g, int b, int a, int sky, int block) {
        buffer.pos(x, y, z).color(r, g, b, a).tex(u, v).lightmap(sky, block).endVertex();
    }
}
