package com.dima16263690d.bleckshlng.client.render;

import net.minecraft.block.Block;
import net.minecraft.block.BlockBreakable;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockFluidRenderer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.color.BlockColors;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

/**
 * 1.12.2 backend for the modern LiquidBlockRenderer surface algorithm.
 *
 * The geometry rules are adapted from the LiquidBlockRenderer in the
 * mod-1.21 source repository. The output is written into the BufferBuilder
 * supplied by BlockRendererDispatcher, so it remains part of the normal
 * chunk translucent pass and does not create a second render pass.
 *
 * The renderer intentionally handles native BlockLiquid states. Sidecar
 * waterlogged states continue to be drawn by WaterloggedFluidRenderer.
 */
public final class ModernLiquidBlockRenderer112 extends BlockFluidRenderer {
    private static final float MAX_FLUID_HEIGHT = 0.8888889F;
    private static final float FACE_EPSILON = 0.001F;
    private static final float UV_SHRINK = 0.001F;

    private final BlockColors blockColors;
    private TextureAtlasSprite[] modernLavaSprites;
    private TextureAtlasSprite[] modernWaterSprites;
    private TextureAtlasSprite modernWaterOverlay;

    public ModernLiquidBlockRenderer112(BlockColors blockColors) {
        super(blockColors);
        this.blockColors = blockColors;
        // BlockFluidRenderer calls initAtlasSprites() from its constructor.
        // Call it again after this instance has completed initialization.
        this.initAtlasSprites();
    }

    @Override
    protected void initAtlasSprites() {
        // This method is dynamically dispatched from the superclass
        // constructor, before subclass field initializers would normally run.
        if (modernLavaSprites == null) {
            modernLavaSprites = new TextureAtlasSprite[2];
        }
        if (modernWaterSprites == null) {
            modernWaterSprites = new TextureAtlasSprite[2];
        }

        TextureMap atlas = Minecraft.getMinecraft().getTextureMapBlocks();
        modernLavaSprites[0] = atlas.getAtlasSprite("minecraft:blocks/lava_still");
        modernLavaSprites[1] = atlas.getAtlasSprite("minecraft:blocks/lava_flow");
        modernWaterSprites[0] = atlas.getAtlasSprite("minecraft:blocks/water_still");
        modernWaterSprites[1] = atlas.getAtlasSprite("minecraft:blocks/water_flow");
        modernWaterOverlay = atlas.getAtlasSprite("minecraft:blocks/water_overlay");
    }

    @Override
    public boolean renderFluid(IBlockAccess blockAccess, IBlockState state,
                               BlockPos pos, BufferBuilder builder) {
        if (blockAccess == null || state == null || pos == null || builder == null
                || !(state.getBlock() instanceof BlockLiquid)) {
            return false;
        }

        Material material = state.getMaterial();
        final boolean lava = material == Material.LAVA;
        if (!lava && material != Material.WATER) {
            return false;
        }

        TextureAtlasSprite[] sprites = lava ? modernLavaSprites : modernWaterSprites;
        if (sprites == null || sprites[0] == null || sprites[1] == null) {
            return false;
        }

        int tint = lava ? 0xFFFFFF
                : this.blockColors.colorMultiplier(state, blockAccess, pos, 0);
        if (tint == -1) {
            tint = 0xFFFFFF;
        }
        float red = ((tint >> 16) & 0xFF) / 255.0F;
        float green = ((tint >> 8) & 0xFF) / 255.0F;
        float blue = (tint & 0xFF) / 255.0F;

        BlockPos downPos = pos.down();
        BlockPos upPos = pos.up();
        BlockPos northPos = pos.north();
        BlockPos southPos = pos.south();
        BlockPos westPos = pos.west();
        BlockPos eastPos = pos.east();

        IBlockState downState = blockAccess.getBlockState(downPos);
        IBlockState upState = blockAccess.getBlockState(upPos);
        IBlockState northState = blockAccess.getBlockState(northPos);
        IBlockState southState = blockAccess.getBlockState(southPos);
        IBlockState westState = blockAccess.getBlockState(westPos);
        IBlockState eastState = blockAccess.getBlockState(eastPos);

        boolean renderTop = !isSameFluid(material, upState);
        boolean renderBottom = shouldRenderFace(
                blockAccess, state, pos, material, EnumFacing.DOWN);
        boolean renderNorth = shouldRenderFace(
                blockAccess, state, pos, material, EnumFacing.NORTH);
        boolean renderSouth = shouldRenderFace(
                blockAccess, state, pos, material, EnumFacing.SOUTH);
        boolean renderWest = shouldRenderFace(
                blockAccess, state, pos, material, EnumFacing.WEST);
        boolean renderEast = shouldRenderFace(
                blockAccess, state, pos, material, EnumFacing.EAST);

        if (!renderTop && !renderBottom && !renderNorth
                && !renderSouth && !renderWest && !renderEast) {
            return false;
        }

        float ownHeight = getHeight(blockAccess, material, pos);
        float northHeight = getHeight(blockAccess, material, northPos);
        float southHeight = getHeight(blockAccess, material, southPos);
        float eastHeight = getHeight(blockAccess, material, eastPos);
        float westHeight = getHeight(blockAccess, material, westPos);

        // Four independently weighted corner heights. Full-height neighbours
        // dominate the corner, while almost-full cells receive the same
        // increased weight used by the modern renderer.
        float northWest;
        float northEast;
        float southEast;
        float southWest;

        if (ownHeight >= 1.0F) {
            northWest = northEast = southEast = southWest = 1.0F;
        } else {
            northWest = calculateAverageHeight(blockAccess, material,
                    ownHeight, northHeight, westHeight, pos.north().west());
            northEast = calculateAverageHeight(blockAccess, material,
                    ownHeight, northHeight, eastHeight, pos.north().east());
            southEast = calculateAverageHeight(blockAccess, material,
                    ownHeight, southHeight, eastHeight, pos.south().east());
            southWest = calculateAverageHeight(blockAccess, material,
                    ownHeight, southHeight, westHeight, pos.south().west());
        }

        double x = (double) pos.getX();
        double y = (double) pos.getY();
        double z = (double) pos.getZ();
        float bottomOffset = renderBottom ? FACE_EPSILON : 0.0F;
        boolean rendered = false;

        // TOP FACE
        float minimumCornerHeight = Math.min(
                Math.min(northWest, northEast),
                Math.min(southEast, southWest));

        if (renderTop && !isFaceOccludedByNeighbor(
                blockAccess, upPos, upState, EnumFacing.UP, minimumCornerHeight)) {
            northWest -= FACE_EPSILON;
            northEast -= FACE_EPSILON;
            southEast -= FACE_EPSILON;
            southWest -= FACE_EPSILON;

            float flowAngle = BlockLiquid.getSlopeAngle(
                    blockAccess, pos, material, state);
            float[] uv = getTopUV(sprites, flowAngle);

            int light = getLightColor(blockAccess, pos);
            emit(builder, x, y + (double) northWest, z,
                    red, green, blue, uv[0], uv[1], light);
            emit(builder, x, y + (double) southWest, z + 1.0D,
                    red, green, blue, uv[2], uv[3], light);
            emit(builder, x + 1.0D, y + (double) southEast, z + 1.0D,
                    red, green, blue, uv[4], uv[5], light);
            emit(builder, x + 1.0D, y + (double) northEast, z,
                    red, green, blue, uv[6], uv[7], light);
            rendered = true;

            // Preserve the backward-facing surface when the block above
            // exposes the underside of this fluid surface.
            BlockLiquid liquid = (BlockLiquid) state.getBlock();
            if (liquid.shouldRenderSides(blockAccess, upPos)) {
                emit(builder, x, y + (double) northWest, z,
                        red, green, blue, uv[0], uv[1], light);
                emit(builder, x + 1.0D, y + (double) northEast, z,
                        red, green, blue, uv[6], uv[7], light);
                emit(builder, x + 1.0D, y + (double) southEast, z + 1.0D,
                        red, green, blue, uv[4], uv[5], light);
                emit(builder, x, y + (double) southWest, z + 1.0D,
                        red, green, blue, uv[2], uv[3], light);
            }
        }

        // BOTTOM FACE
        if (renderBottom && !isFaceOccludedByNeighbor(
                blockAccess, downPos, downState, EnumFacing.DOWN, MAX_FLUID_HEIGHT)) {
            TextureAtlasSprite still = sprites[0];
            float u0 = still.getMinU();
            float u1 = still.getMaxU();
            float v0 = still.getMinV();
            float v1 = still.getMaxV();
            int light = downState.getPackedLightmapCoords(blockAccess, downPos);
            float shade = 0.5F;

            emit(builder, x, y + (double) bottomOffset, z + 1.0D,
                    red * shade, green * shade, blue * shade, u0, v1, light);
            emit(builder, x, y + (double) bottomOffset, z,
                    red * shade, green * shade, blue * shade, u0, v0, light);
            emit(builder, x + 1.0D, y + (double) bottomOffset, z,
                    red * shade, green * shade, blue * shade, u1, v0, light);
            emit(builder, x + 1.0D, y + (double) bottomOffset, z + 1.0D,
                    red * shade, green * shade, blue * shade, u1, v1, light);
            rendered = true;
        }

        // HORIZONTAL SIDE FACES
        int sideLight = getLightColor(blockAccess, pos);
        rendered |= renderSide(blockAccess, builder, material, lava,
                pos, northPos, northState, EnumFacing.NORTH,
                northWest, northEast,
                x, x + 1.0D, z + FACE_EPSILON, z + FACE_EPSILON,
                bottomOffset, sprites, red, green, blue, sideLight,
                renderNorth);
        rendered |= renderSide(blockAccess, builder, material, lava,
                pos, southPos, southState, EnumFacing.SOUTH,
                southEast, southWest,
                x + 1.0D, x, z + 1.0D - FACE_EPSILON,
                z + 1.0D - FACE_EPSILON,
                bottomOffset, sprites, red, green, blue, sideLight,
                renderSouth);
        rendered |= renderSide(blockAccess, builder, material, lava,
                pos, westPos, westState, EnumFacing.WEST,
                southWest, northWest,
                x + FACE_EPSILON, x + FACE_EPSILON,
                z + 1.0D, z,
                bottomOffset, sprites, red, green, blue, sideLight,
                renderWest);
        rendered |= renderSide(blockAccess, builder, material, lava,
                pos, eastPos, eastState, EnumFacing.EAST,
                northEast, southEast,
                x + 1.0D - FACE_EPSILON,
                x + 1.0D - FACE_EPSILON,
                z, z + 1.0D,
                bottomOffset, sprites, red, green, blue, sideLight,
                renderEast);

        return rendered;
    }

    private boolean renderSide(
            IBlockAccess blockAccess,
            BufferBuilder builder,
            Material material,
            boolean lava,
            BlockPos currentPos,
            BlockPos neighborPos,
            IBlockState neighborState,
            EnumFacing direction,
            float firstHeight,
            float secondHeight,
            double firstX,
            double secondX,
            double firstZ,
            double secondZ,
            float bottomOffset,
            TextureAtlasSprite[] sprites,
            float red,
            float green,
            float blue,
            int light,
            boolean sideVisible) {
        if (!sideVisible || isFaceOccludedByNeighbor(
                blockAccess, neighborPos, neighborState,
                direction, Math.max(firstHeight, secondHeight))) {
            return false;
        }

        TextureAtlasSprite sideSprite = sprites[1];
        if (!lava && shouldUseWaterOverlay(neighborState, blockAccess, neighborPos)) {
            sideSprite = modernWaterOverlay;
        }
        if (sideSprite == null) {
            sideSprite = sprites[1];
        }

        float u0 = sideSprite.getInterpolatedU(0.0D);
        float u1 = sideSprite.getInterpolatedU(8.0D);
        float vFirst = sideSprite.getInterpolatedV(
                (double) ((1.0F - firstHeight) * 8.0F));
        float vSecond = sideSprite.getInterpolatedV(
                (double) ((1.0F - secondHeight) * 8.0F));
        float vBottom = sideSprite.getInterpolatedV(8.0D);

        float shade = direction.getAxis() == EnumFacing.Axis.Z ? 0.8F : 0.6F;
        float r = red * shade;
        float g = green * shade;
        float b = blue * shade;

        emit(builder, firstX, (double) currentPos.getY() + (double) firstHeight,
                firstZ, r, g, b, u0, vFirst, light);
        emit(builder, secondX, (double) currentPos.getY() + (double) secondHeight,
                secondZ, r, g, b, u1, vSecond, light);
        emit(builder, secondX, (double) currentPos.getY() + (double) bottomOffset,
                secondZ, r, g, b, u1, vBottom, light);
        emit(builder, firstX, (double) currentPos.getY() + (double) bottomOffset,
                firstZ, r, g, b, u0, vBottom, light);

        // Like the modern renderer, non-overlay side textures are emitted
        // backwards as well. Water overlay is intentionally single-sided.
        if (sideSprite != modernWaterOverlay) {
            emit(builder, firstX, (double) currentPos.getY() + (double) bottomOffset,
                    firstZ, r, g, b, u0, vBottom, light);
            emit(builder, secondX, (double) currentPos.getY() + (double) bottomOffset,
                    secondZ, r, g, b, u1, vBottom, light);
            emit(builder, secondX, (double) currentPos.getY() + (double) secondHeight,
                    secondZ, r, g, b, u1, vSecond, light);
            emit(builder, firstX, (double) currentPos.getY() + (double) firstHeight,
                    firstZ, r, g, b, u0, vFirst, light);
        }

        return true;
    }

    private static boolean shouldUseWaterOverlay(
            IBlockState neighborState, IBlockAccess blockAccess, BlockPos neighborPos) {
        Block neighbor = neighborState.getBlock();
        return neighbor instanceof BlockBreakable
                || neighbor.isLeaves(neighborState, blockAccess, neighborPos);
    }

    private static boolean shouldRenderFace(
            IBlockAccess blockAccess,
            IBlockState ownState,
            BlockPos ownPos,
            Material material,
            EnumFacing direction) {
        if (isFaceOccludedBySelf(blockAccess, ownState, ownPos, direction)) {
            return false;
        }

        IBlockState neighbor = blockAccess.getBlockState(ownPos.offset(direction));
        if (isSameFluid(material, neighbor)) {
            return false;
        }

        return ownState.shouldSideBeRendered(blockAccess, ownPos, direction);
    }

    private static boolean isFaceOccludedBySelf(
            IBlockAccess blockAccess, IBlockState state,
            BlockPos pos, EnumFacing face) {
        return state.getBlock().isSideSolid(state, blockAccess, pos, face);
    }

    private static boolean isFaceOccludedByNeighbor(
            IBlockAccess blockAccess,
            BlockPos neighborPos,
            IBlockState neighborState,
            EnumFacing direction,
            float fluidHeight) {
        // A full block above only hides a surface that actually reaches the
        // top of the fluid cell. Side and bottom faces use full-face coverage.
        if (direction == EnumFacing.UP && fluidHeight < 1.0F) {
            return false;
        }

        return neighborState.getBlock().isSideSolid(
                neighborState, blockAccess, neighborPos, direction.getOpposite());
    }

    private static boolean isSameFluid(Material material, IBlockState state) {
        return state != null
                && state.getBlock() instanceof BlockLiquid
                && state.getMaterial() == material;
    }

    private static float getHeight(
            IBlockAccess blockAccess, Material material, BlockPos pos) {
        IBlockState state = blockAccess.getBlockState(pos);
        if (isSameFluid(material, state)) {
            IBlockState above = blockAccess.getBlockState(pos.up());
            if (isSameFluid(material, above)) {
                return 1.0F;
            }

            int level = state.getValue(BlockLiquid.LEVEL).intValue();
            return 1.0F - BlockLiquid.getLiquidHeightPercent(level);
        }

        return state.getMaterial().blocksMovement() ? -1.0F : 0.0F;
    }

    private static float calculateAverageHeight(
            IBlockAccess blockAccess,
            Material material,
            float center,
            float firstSide,
            float secondSide,
            BlockPos diagonalPos) {
        if (firstSide >= 1.0F || secondSide >= 1.0F) {
            return 1.0F;
        }

        float weightedHeight = 0.0F;
        float totalWeight = 0.0F;

        if (firstSide > 0.0F || secondSide > 0.0F) {
            float diagonal = getHeight(blockAccess, material, diagonalPos);
            if (diagonal >= 1.0F) {
                return 1.0F;
            }
            float[] weighted = addWeightedHeight(
                    weightedHeight, totalWeight, diagonal);
            weightedHeight = weighted[0];
            totalWeight = weighted[1];
        }

        float[] weightedCenter = addWeightedHeight(
                weightedHeight, totalWeight, center);
        weightedHeight = weightedCenter[0];
        totalWeight = weightedCenter[1];

        float[] weightedFirst = addWeightedHeight(
                weightedHeight, totalWeight, firstSide);
        weightedHeight = weightedFirst[0];
        totalWeight = weightedFirst[1];

        float[] weightedSecond = addWeightedHeight(
                weightedHeight, totalWeight, secondSide);
        weightedHeight = weightedSecond[0];
        totalWeight = weightedSecond[1];

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

    private static float[] getTopUV(
            TextureAtlasSprite[] sprites, float flowAngle) {
        float[] uv = new float[8];

        if (flowAngle < -999.0F) {
            TextureAtlasSprite still = sprites[0];
            float u0 = still.getInterpolatedU(0.0D);
            float u1 = still.getInterpolatedU(16.0D);
            float v0 = still.getInterpolatedV(0.0D);
            float v1 = still.getInterpolatedV(16.0D);

            uv[0] = u0; uv[1] = v0; // north-west
            uv[2] = u0; uv[3] = v1; // south-west
            uv[4] = u1; uv[5] = v1; // south-east
            uv[6] = u1; uv[7] = v0; // north-east
        } else {
            TextureAtlasSprite flow = sprites[1];
            float sin = net.minecraft.util.math.MathHelper.sin(flowAngle) * 0.25F;
            float cos = net.minecraft.util.math.MathHelper.cos(flowAngle) * 0.25F;

            uv[0] = flow.getInterpolatedU(8.0D + (-cos - sin) * 16.0D);
            uv[1] = flow.getInterpolatedV(8.0D + (-cos + sin) * 16.0D);
            uv[2] = flow.getInterpolatedU(8.0D + (-cos + sin) * 16.0D);
            uv[3] = flow.getInterpolatedV(8.0D + (cos + sin) * 16.0D);
            uv[4] = flow.getInterpolatedU(8.0D + (cos + sin) * 16.0D);
            uv[5] = flow.getInterpolatedV(8.0D + (cos - sin) * 16.0D);
            uv[6] = flow.getInterpolatedU(8.0D + (cos - sin) * 16.0D);
            uv[7] = flow.getInterpolatedV(8.0D + (-cos - sin) * 16.0D);
        }

        // Pull the four UV corners slightly toward the center to avoid atlas
        // bleeding at the edges of rotated flowing textures.
        float centerU = (uv[0] + uv[2] + uv[4] + uv[6]) * 0.25F;
        float centerV = (uv[1] + uv[3] + uv[5] + uv[7]) * 0.25F;
        for (int i = 0; i < 8; i += 2) {
            uv[i] += (centerU - uv[i]) * UV_SHRINK;
            uv[i + 1] += (centerV - uv[i + 1]) * UV_SHRINK;
        }

        return uv;
    }

    private static int getLightColor(IBlockAccess blockAccess, BlockPos pos) {
        IBlockState current = blockAccess.getBlockState(pos);
        IBlockState above = blockAccess.getBlockState(pos.up());
        int currentLight = current.getPackedLightmapCoords(blockAccess, pos);
        int aboveLight = above.getPackedLightmapCoords(blockAccess, pos.up());

        int blockLight = Math.max(currentLight & 0xFF, aboveLight & 0xFF);
        int skyLight = Math.max(
                (currentLight >> 16) & 0xFF,
                (aboveLight >> 16) & 0xFF);
        return (skyLight << 16) | blockLight;
    }

    private static void emit(
            BufferBuilder builder,
            double x, double y, double z,
            float red, float green, float blue,
            float u, float v,
            int packedLight) {
        int skyLight = (packedLight >> 16) & 0xFFFF;
        int blockLight = packedLight & 0xFFFF;
        builder.pos(x, y, z)
                .color(red, green, blue, 1.0F)
                .tex(u, v)
                .lightmap(skyLight, blockLight)
                .endVertex();
    }
}
