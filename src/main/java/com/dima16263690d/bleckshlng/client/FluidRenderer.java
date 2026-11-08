package com.dima16263690d.bleckshlng.client;

import com.dima16263690d.bleckshlng.fluid.FluidAccess;
import com.dima16263690d.bleckshlng.fluid.FluidState;
import com.dima16263690d.bleckshlng.fluid.Fluids;
import com.dima16263690d.bleckshlng.fluid.FlowingFluid;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockFluidRenderer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.color.BlockColors;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Port of {@code net.minecraft.client.renderer.block.LiquidBlockRenderer} from Minecraft 1.21.
 *
 * <p>Unlike the legacy 1.12.2 renderer, the surface height of every corner is derived from the
 * ported 1.21 fluid amounts ({@link FluidState#getOwnHeight()}) and the flow direction is taken
 * from the ported {@link FlowingFluid#getFlow(IBlockAccess, BlockPos, FluidState)}, producing the
 * same smooth, slope-aware surface as modern Minecraft.</p>
 */
@SideOnly(Side.CLIENT)
public class FluidRenderer extends BlockFluidRenderer {
    private static final float MAX_FLUID_HEIGHT = 0.8888889F;
    private static final double OCCLUDE_EPSILON = 1.0E-7D;
    private final BlockColors blockColors;
    private final TextureAtlasSprite[] lavaIcons = new TextureAtlasSprite[2];
    private final TextureAtlasSprite[] waterIcons = new TextureAtlasSprite[2];
    private TextureAtlasSprite waterOverlay;

    public FluidRenderer(BlockColors blockColors) {
        super(blockColors);
        this.blockColors = blockColors;
        this.initAtlasSprites();
    }

    @Override
    protected void initAtlasSprites() {
        // Also invoked from the superclass constructor before our fields exist; ignore that call.
        if (this.lavaIcons == null) {
            return;
        }
        TextureMap textureMap = Minecraft.getMinecraft().getTextureMapBlocks();
        this.lavaIcons[0] = textureMap.getAtlasSprite("minecraft:blocks/lava_still");
        this.lavaIcons[1] = textureMap.getAtlasSprite("minecraft:blocks/lava_flow");
        this.waterIcons[0] = textureMap.getAtlasSprite("minecraft:blocks/water_still");
        this.waterIcons[1] = textureMap.getAtlasSprite("minecraft:blocks/water_flow");
        this.waterOverlay = textureMap.getAtlasSprite("minecraft:blocks/water_overlay");
    }

    private static boolean isNeighborSameFluid(FluidState state, FluidState neighbor) {
        return neighbor.getType().isSame(state.getType());
    }

    public static boolean shouldRenderFace(FluidState state, IBlockState blockState, EnumFacing face, FluidState neighbor) {
        return !blockState.isOpaqueCube() && !isNeighborSameFluid(state, neighbor);
    }

    @Override
    public boolean renderFluid(IBlockAccess world, IBlockState blockState, BlockPos pos, BufferBuilder buffer) {
        FluidState state = FluidAccess.getFluidState(world, pos);
        if (state.isEmpty()) {
            return false;
        }
        boolean isLava = state.getType() == Fluids.LAVA || state.getType() == Fluids.FLOWING_LAVA;
        TextureAtlasSprite[] sprites = isLava ? this.lavaIcons : this.waterIcons;
        int color = this.blockColors.colorMultiplier(blockState, world, pos, 0);
        float red = (float) (color >> 16 & 255) / 255.0F;
        float green = (float) (color >> 8 & 255) / 255.0F;
        float blue = (float) (color & 255) / 255.0F;

        IBlockState belowState = world.getBlockState(pos.down());
        FluidState belowFluid = FluidAccess.getFluidState(world, pos.down());
        IBlockState aboveState = world.getBlockState(pos.up());
        FluidState aboveFluid = FluidAccess.getFluidState(world, pos.up());
        IBlockState northState = world.getBlockState(pos.north());
        FluidState northFluid = FluidAccess.getFluidState(world, pos.north());
        IBlockState southState = world.getBlockState(pos.south());
        FluidState southFluid = FluidAccess.getFluidState(world, pos.south());
        IBlockState westState = world.getBlockState(pos.west());
        FluidState westFluid = FluidAccess.getFluidState(world, pos.west());
        IBlockState eastState = world.getBlockState(pos.east());
        FluidState eastFluid = FluidAccess.getFluidState(world, pos.east());

        float heightSelf = this.getHeight(world, state, pos, blockState, state);
        float cornerNW;
        float cornerNE;
        float cornerSE;
        float cornerSW;
        if (heightSelf >= 1.0F) {
            cornerNW = 1.0F;
            cornerNE = 1.0F;
            cornerSE = 1.0F;
            cornerSW = 1.0F;
        } else {
            float rawNorth = this.getHeight(world, state, pos.north(), northState, northFluid);
            float rawSouth = this.getHeight(world, state, pos.south(), southState, southFluid);
            float rawEast = this.getHeight(world, state, pos.east(), eastState, eastFluid);
            float rawWest = this.getHeight(world, state, pos.west(), westState, westFluid);
            cornerNW = this.calculateAverageHeight(world, state, heightSelf, rawNorth, rawWest, pos.north().west());
            cornerNE = this.calculateAverageHeight(world, state, heightSelf, rawNorth, rawEast, pos.north().east());
            cornerSE = this.calculateAverageHeight(world, state, heightSelf, rawSouth, rawEast, pos.south().east());
            cornerSW = this.calculateAverageHeight(world, state, heightSelf, rawSouth, rawWest, pos.south().west());
        }

        float topHeight = Math.min(Math.min(cornerNW, cornerNE), Math.min(cornerSE, cornerSW));
        boolean renderTop = !isNeighborSameFluid(state, aboveFluid)
                && !isFaceOccludedByNeighbor(EnumFacing.UP, topHeight, aboveState, world, pos.up());
        if (renderTop) {
            cornerNW -= 0.001F;
            cornerNE -= 0.001F;
            cornerSE -= 0.001F;
            cornerSW -= 0.001F;
        }

        boolean renderBottom = shouldRenderFace(state, blockState, EnumFacing.DOWN, belowFluid)
                && !isFaceOccludedByNeighbor(EnumFacing.DOWN, MAX_FLUID_HEIGHT, belowState, world, pos.down());
        boolean renderNorth = shouldRenderFace(state, blockState, EnumFacing.NORTH, northFluid)
                && !isFaceOccludedByNeighbor(EnumFacing.NORTH, Math.max(cornerNW, cornerNE), northState, world, pos.north());
        boolean renderSouth = shouldRenderFace(state, blockState, EnumFacing.SOUTH, southFluid)
                && !isFaceOccludedByNeighbor(EnumFacing.SOUTH, Math.max(cornerSW, cornerSE), southState, world, pos.south());
        boolean renderWest = shouldRenderFace(state, blockState, EnumFacing.WEST, westFluid)
                && !isFaceOccludedByNeighbor(EnumFacing.WEST, Math.max(cornerNW, cornerSW), westState, world, pos.west());
        boolean renderEast = shouldRenderFace(state, blockState, EnumFacing.EAST, eastFluid)
                && !isFaceOccludedByNeighbor(EnumFacing.EAST, Math.max(cornerNE, cornerSE), eastState, world, pos.east());

        if (!renderTop && !renderBottom && !renderNorth && !renderSouth && !renderWest && !renderEast) {
            return false;
        }

        float shadeDown = 0.5F;
        float shadeUp = 1.0F;
        float shadeNorthSouth = 0.8F;
        float shadeWestEast = 0.6F;

        double x = pos.getX();
        double y = pos.getY();
        double z = pos.getZ();
        float bottomOffset = renderBottom ? 0.001F : 0.0F;

        if (renderTop) {
            int light = blockState.getPackedLightmapCoords(world, pos);
            int lightU = light >> 16 & 65535;
            int lightV = light & 65535;
            float r = shadeUp * red;
            float g = shadeUp * green;
            float b = shadeUp * blue;
            float u0;
            float v0;
            float u1;
            float v1;
            float u2;
            float v2;
            float u3;
            float v3;
            Vec3d flow = this.flowVector(world, pos, state);
            if (flow.x == 0.0D && flow.z == 0.0D) {
                TextureAtlasSprite still = sprites[0];
                u0 = still.getInterpolatedU(0.0D);
                v0 = still.getInterpolatedV(0.0D);
                u1 = u0;
                v1 = still.getInterpolatedV(16.0D);
                u2 = still.getInterpolatedU(16.0D);
                v2 = v1;
                u3 = u2;
                v3 = v0;
            } else {
                TextureAtlasSprite flowSprite = sprites[1];
                float angle = (float) MathHelper.atan2(flow.z, flow.x) - (float) (Math.PI / 2.0D);
                float sin = MathHelper.sin(angle) * 0.25F;
                float cos = MathHelper.cos(angle) * 0.25F;
                u0 = flowSprite.getInterpolatedU((double) (8.0F + (-cos - sin) * 16.0F));
                v0 = flowSprite.getInterpolatedV((double) (8.0F + (-cos + sin) * 16.0F));
                u1 = flowSprite.getInterpolatedU((double) (8.0F + (-cos + sin) * 16.0F));
                v1 = flowSprite.getInterpolatedV((double) (8.0F + (cos + sin) * 16.0F));
                u2 = flowSprite.getInterpolatedU((double) (8.0F + (cos + sin) * 16.0F));
                v2 = flowSprite.getInterpolatedV((double) (8.0F + (cos - sin) * 16.0F));
                u3 = flowSprite.getInterpolatedU((double) (8.0F + (cos - sin) * 16.0F));
                v3 = flowSprite.getInterpolatedV((double) (8.0F + (-cos - sin) * 16.0F));
            }
            this.vertex(buffer, x, y + cornerNW, z, r, g, b, u0, v0, lightU, lightV);
            this.vertex(buffer, x, y + cornerSW, z + 1.0D, r, g, b, u1, v1, lightU, lightV);
            this.vertex(buffer, x + 1.0D, y + cornerSE, z + 1.0D, r, g, b, u2, v2, lightU, lightV);
            this.vertex(buffer, x + 1.0D, y + cornerNE, z, r, g, b, u3, v3, lightU, lightV);
            if (this.shouldRenderBackwardUpFace(world, pos.up(), state)) {
                this.vertex(buffer, x, y + cornerNW, z, r, g, b, u0, v0, lightU, lightV);
                this.vertex(buffer, x + 1.0D, y + cornerNE, z, r, g, b, u3, v3, lightU, lightV);
                this.vertex(buffer, x + 1.0D, y + cornerSE, z + 1.0D, r, g, b, u2, v2, lightU, lightV);
                this.vertex(buffer, x, y + cornerSW, z + 1.0D, r, g, b, u1, v1, lightU, lightV);
            }
        }

        if (renderBottom) {
            TextureAtlasSprite still = sprites[0];
            float u0 = still.getMinU();
            float u1 = still.getMaxU();
            float v0 = still.getMinV();
            float v1 = still.getMaxV();
            int light = blockState.getPackedLightmapCoords(world, pos.down());
            int lightU = light >> 16 & 65535;
            int lightV = light & 65535;
            float r = shadeDown * red;
            float g = shadeDown * green;
            float b = shadeDown * blue;
            this.vertex(buffer, x, y + bottomOffset, z + 1.0D, r, g, b, u0, v1, lightU, lightV);
            this.vertex(buffer, x, y + bottomOffset, z, r, g, b, u0, v0, lightU, lightV);
            this.vertex(buffer, x + 1.0D, y + bottomOffset, z, r, g, b, u1, v0, lightU, lightV);
            this.vertex(buffer, x + 1.0D, y + bottomOffset, z + 1.0D, r, g, b, u1, v1, lightU, lightV);
        }

        int light = blockState.getPackedLightmapCoords(world, pos);
        int lightU = light >> 16 & 65535;
        int lightV = light & 65535;

        for (EnumFacing face : EnumFacing.Plane.HORIZONTAL) {
            float topA;
            float topB;
            double xA;
            double xB;
            double zA;
            double zB;
            boolean render;
            BlockPos neighborPos;
            switch (face) {
                case NORTH:
                    topA = cornerNW;
                    topB = cornerNE;
                    xA = x;
                    xB = x + 1.0D;
                    zA = z + 0.001D;
                    zB = z + 0.001D;
                    render = renderNorth;
                    neighborPos = pos.north();
                    break;
                case SOUTH:
                    topA = cornerSW;
                    topB = cornerSE;
                    xA = x + 1.0D;
                    xB = x;
                    zA = z + 1.0D - 0.001D;
                    zB = z + 1.0D - 0.001D;
                    render = renderSouth;
                    neighborPos = pos.south();
                    break;
                case WEST:
                    topA = cornerNW;
                    topB = cornerSW;
                    xA = x + 0.001D;
                    xB = x + 0.001D;
                    zA = z;
                    zB = z + 1.0D;
                    render = renderWest;
                    neighborPos = pos.west();
                    break;
                default:
                    topA = cornerNE;
                    topB = cornerSE;
                    xA = x + 1.0D - 0.001D;
                    xB = x + 1.0D - 0.001D;
                    zA = z + 1.0D;
                    zB = z;
                    render = renderEast;
                    neighborPos = pos.east();
                    break;
            }

            if (render) {
                TextureAtlasSprite side = sprites[1];
                if (!isLava) {
                    Block neighborBlock = world.getBlockState(neighborPos).getBlock();
                    if (neighborBlock == Blocks.GLASS || neighborBlock == Blocks.STAINED_GLASS) {
                        side = this.waterOverlay;
                    }
                }
                float u0 = side.getInterpolatedU(0.0D);
                float u1 = side.getInterpolatedU(8.0D);
                float v0 = side.getInterpolatedV((double) ((1.0F - topA) * 16.0F * 0.5F));
                float v1 = side.getInterpolatedV((double) ((1.0F - topB) * 16.0F * 0.5F));
                float v2 = side.getInterpolatedV(8.0D);
                float shade = face.getAxis() == EnumFacing.Axis.Z ? shadeNorthSouth : shadeWestEast;
                float r = shade * red;
                float g = shade * green;
                float b = shade * blue;
                this.vertex(buffer, xA, y + topA, zA, r, g, b, u0, v0, lightU, lightV);
                this.vertex(buffer, xB, y + topB, zB, r, g, b, u1, v1, lightU, lightV);
                this.vertex(buffer, xB, y + bottomOffset, zB, r, g, b, u1, v2, lightU, lightV);
                this.vertex(buffer, xA, y + bottomOffset, zA, r, g, b, u0, v2, lightU, lightV);
                if (side != this.waterOverlay) {
                    this.vertex(buffer, xA, y + bottomOffset, zA, r, g, b, u0, v2, lightU, lightV);
                    this.vertex(buffer, xB, y + bottomOffset, zB, r, g, b, u1, v2, lightU, lightV);
                    this.vertex(buffer, xB, y + topB, zB, r, g, b, u1, v1, lightU, lightV);
                    this.vertex(buffer, xA, y + topA, zA, r, g, b, u0, v0, lightU, lightV);
                }
            }
        }

        return true;
    }

    private Vec3d flowVector(IBlockAccess world, BlockPos pos, FluidState state) {
        if (state.getType() instanceof FlowingFluid) {
            return ((FlowingFluid) state.getType()).getFlow(world, pos, state);
        }
        return Vec3d.ZERO;
    }

    /**
     * Port of {@code LiquidBlockRenderer.isFaceOccludedByNeighbor}. The neighbour's collision shape
     * decides whether the fluid face is hidden, using the legacy collision AABB in place of the
     * modern {@code VoxelShape} machinery.
     */
    private static boolean isFaceOccludedByNeighbor(EnumFacing face, float height, IBlockState neighborState,
            IBlockAccess world, BlockPos neighborPos) {
        AxisAlignedBB neighborBox = collisionBox(world, neighborPos, neighborState);
        if (neighborBox == null) {
            return false;
        }
        if (isFullBox(neighborBox)) {
            return face != EnumFacing.UP || height >= 1.0F;
        }
        return blockOccudes(new AxisAlignedBB(0.0D, 0.0D, 0.0D, 1.0D, (double) height, 1.0D), neighborBox, face);
    }

    /** Port of {@code Shapes.blockOccudes} for the fluid box against a neighbour shape. */
    private static boolean blockOccudes(AxisAlignedBB fluidBox, AxisAlignedBB neighborBox, EnumFacing face) {
        if (isFullBox(fluidBox) && isFullBox(neighborBox)) {
            return true;
        }
        EnumFacing.Axis axis = face.getAxis();
        boolean positive = face.getAxisDirection() == EnumFacing.AxisDirection.POSITIVE;
        AxisAlignedBB near = positive ? fluidBox : neighborBox;
        AxisAlignedBB far = positive ? neighborBox : fluidBox;
        if (axisMax(near, axis) < 1.0D - OCCLUDE_EPSILON) {
            return false;
        }
        if (axisMin(far, axis) > OCCLUDE_EPSILON) {
            return false;
        }
        // Occluded only when the fluid's face rectangle lies inside the neighbour's face rectangle.
        double[] inner = planeRect(fluidBox, axis);
        double[] outer = planeRect(neighborBox, axis);
        return inner[0] >= outer[0] - OCCLUDE_EPSILON && inner[1] <= outer[1] + OCCLUDE_EPSILON
                && inner[2] >= outer[2] - OCCLUDE_EPSILON && inner[3] <= outer[3] + OCCLUDE_EPSILON;
    }

    private static AxisAlignedBB collisionBox(IBlockAccess world, BlockPos pos, IBlockState state) {
        AxisAlignedBB box = state.getCollisionBoundingBox(world, pos);
        return box == null || box == Block.NULL_AABB ? null : box;
    }

    private static boolean isFullBox(AxisAlignedBB box) {
        return box != null
                && box.minX <= 0.0D && box.minY <= 0.0D && box.minZ <= 0.0D
                && box.maxX >= 1.0D && box.maxY >= 1.0D && box.maxZ >= 1.0D;
    }

    private static double[] planeRect(AxisAlignedBB box, EnumFacing.Axis axis) {
        switch (axis) {
            case X:
                return new double[] {box.minY, box.maxY, box.minZ, box.maxZ};
            case Z:
                return new double[] {box.minX, box.maxX, box.minY, box.maxY};
            case Y:
            default:
                return new double[] {box.minX, box.maxX, box.minZ, box.maxZ};
        }
    }

    private static double axisMin(AxisAlignedBB box, EnumFacing.Axis axis) {
        switch (axis) {
            case X:
                return box.minX;
            case Y:
                return box.minY;
            case Z:
            default:
                return box.minZ;
        }
    }

    private static double axisMax(AxisAlignedBB box, EnumFacing.Axis axis) {
        switch (axis) {
            case X:
                return box.maxX;
            case Y:
                return box.maxY;
            case Z:
            default:
                return box.maxZ;
        }
    }

    /** Port of {@code FluidState.shouldRenderBackwardUpFace} from Minecraft 1.21. */
    private boolean shouldRenderBackwardUpFace(IBlockAccess world, BlockPos center, FluidState state) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos pos = center.add(dx, 0, dz);
                FluidState neighbor = FluidAccess.getFluidState(world, pos);
                if (!neighbor.getType().isSame(state.getType()) && !world.getBlockState(pos).isOpaqueCube()) {
                    return true;
                }
            }
        }
        return false;
    }

    private float calculateAverageHeight(IBlockAccess world, FluidState state, float self, float a, float b, BlockPos cornerPos) {
        if (!(b >= 1.0F) && !(a >= 1.0F)) {
            float[] accumulator = new float[2];
            if (b > 0.0F || a > 0.0F) {
                float cornerHeight = this.getHeight(world, state, cornerPos);
                if (cornerHeight >= 1.0F) {
                    return 1.0F;
                }
                addWeightedHeight(accumulator, cornerHeight);
            }
            addWeightedHeight(accumulator, self);
            addWeightedHeight(accumulator, b);
            addWeightedHeight(accumulator, a);
            return accumulator[0] / accumulator[1];
        }
        return 1.0F;
    }

    private static void addWeightedHeight(float[] accumulator, float height) {
        if (height >= 0.8F) {
            accumulator[0] += height * 10.0F;
            accumulator[1] += 10.0F;
        } else if (height >= 0.0F) {
            accumulator[0] += height;
            accumulator[1]++;
        }
    }

    private float getHeight(IBlockAccess world, FluidState state, BlockPos pos) {
        IBlockState blockState = world.getBlockState(pos);
        return this.getHeight(world, state, pos, blockState, FluidAccess.getFluidState(world, pos));
    }

    private float getHeight(IBlockAccess world, FluidState state, BlockPos pos, IBlockState blockState, FluidState otherState) {
        if (state.getType().isSame(otherState.getType())) {
            return state.getType().isSame(FluidAccess.getFluidState(world, pos.up()).getType())
                    ? 1.0F
                    : otherState.getOwnHeight();
        }
        return !blockState.getMaterial().isSolid() ? 0.0F : -1.0F;
    }

    private void vertex(BufferBuilder buffer, double x, double y, double z, float r, float g, float b, float u, float v, int lightU, int lightV) {
        buffer.pos(x, y, z).color(r, g, b, 1.0F).tex((double) u, (double) v).lightmap(lightU, lightV).endVertex();
    }
}
