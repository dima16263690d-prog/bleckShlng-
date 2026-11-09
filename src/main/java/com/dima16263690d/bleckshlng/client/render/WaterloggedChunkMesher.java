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
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.dima16263690d.bleckshlng.fluid.state.FluidHeightCalculator;
import com.dima16263690d.bleckshlng.fluid.state.FluidStateBridge;
import com.dima16263690d.bleckshlng.fluid.state.FluidStateMapper;

/**
 * Emits water quads into the chunk BufferBuilder (same pass as vanilla fluids).
 * Called from FluidCoreHooks.afterRenderBlock during chunk rebuild.
 */
@SideOnly(Side.CLIENT)
public final class WaterloggedChunkMesher {
    private WaterloggedChunkMesher() {
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
                || fluid == null || buffer == null
                || fluid.getKind() != FluidStateBridge.FluidKind.WATER) {
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

        float hNW = sampleCorner(access, pos, ownHeight, -1, -1);
        float hNE = sampleCorner(access, pos, ownHeight, 1, -1);
        float hSE = sampleCorner(access, pos, ownHeight, 1, 1);
        float hSW = sampleCorner(access, pos, ownHeight, -1, 1);
        hNW = Math.min(ownHeight, Math.max(hNW, minY));
        hNE = Math.min(ownHeight, Math.max(hNE, minY));
        hSE = Math.min(ownHeight, Math.max(hSE, minY));
        hSW = Math.min(ownHeight, Math.max(hSW, minY));

        Minecraft mc = Minecraft.getMinecraft();
        TextureAtlasSprite still = mc.getBlockRendererDispatcher()
                .getBlockModelShapes().getTexture(Blocks.WATER.getDefaultState());
        TextureAtlasSprite flow = still;
        try {
            TextureAtlasSprite f = mc.getTextureMapBlocks()
                    .getAtlasSprite("minecraft:blocks/water_flow");
            if (f != null && !"missingno".equals(f.getIconName())) {
                flow = f;
            }
        } catch (Throwable ignored) {
        }
        if (still == null) {
            return;
        }

        int color = 0x3F76E4;
        try {
            int tint = mc.getBlockColors().colorMultiplier(
                    Blocks.WATER.getDefaultState(),
                    access, pos, 0);
            if (tint != -1) {
                color = tint;
            }
        } catch (Throwable ignored) {
        }
        int r = color >> 16 & 255;
        int g = color >> 8 & 255;
        int b = color & 255;
        int a = 255;

        int packed = access.getCombinedLight(pos, 0);
        int sky = packed >> 16 & 65535;
        int block = packed & 65535;

        double x = pos.getX();
        double y = pos.getY();
        double z = pos.getZ();

        if (!isSameFluid(access, pos.up()) || ownHeight < 1.0F) {
            vert(buffer, x + 0, y + hNW, z + 0, still.getMinU(), still.getMinV(), r, g, b, a, sky, block);
            vert(buffer, x + 0, y + hSW, z + 1, still.getMinU(), still.getMaxV(), r, g, b, a, sky, block);
            vert(buffer, x + 1, y + hSE, z + 1, still.getMaxU(), still.getMaxV(), r, g, b, a, sky, block);
            vert(buffer, x + 1, y + hNE, z + 0, still.getMaxU(), still.getMinV(), r, g, b, a, sky, block);
        }

        if (minY <= 0.001F && !isSameFluid(access, pos.down())) {
            double yy = y + minY;
            vert(buffer, x + 0, yy, z + 1, still.getMinU(), still.getMaxV(), r, g, b, a, sky, block);
            vert(buffer, x + 0, yy, z + 0, still.getMinU(), still.getMinV(), r, g, b, a, sky, block);
            vert(buffer, x + 1, yy, z + 0, still.getMaxU(), still.getMinV(), r, g, b, a, sky, block);
            vert(buffer, x + 1, yy, z + 1, still.getMaxU(), still.getMaxV(), r, g, b, a, sky, block);
        }

        side(buffer, access, pos, EnumFacing.NORTH, x, y, z, hNW, hNE, minY, flow, r, g, b, a, sky, block);
        side(buffer, access, pos, EnumFacing.SOUTH, x, y, z, hSW, hSE, minY, flow, r, g, b, a, sky, block);
        side(buffer, access, pos, EnumFacing.WEST, x, y, z, hNW, hSW, minY, flow, r, g, b, a, sky, block);
        side(buffer, access, pos, EnumFacing.EAST, x, y, z, hNE, hSE, minY, flow, r, g, b, a, sky, block);
    }

    private static void side(BufferBuilder buffer, IBlockAccess access, BlockPos pos,
                             EnumFacing face, double x, double y, double z,
                             float h0, float h1, float minY, TextureAtlasSprite sprite,
                             int r, int g, int b, int a, int sky, int block) {
        if (isSameFluid(access, pos.offset(face))) {
            return;
        }
        if (h0 <= minY && h1 <= minY) {
            return;
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
                vert(buffer, x + 0, y + h0, z + 0, u0, vTop0, r, g, b, a, sky, block);
                vert(buffer, x + 1, y + h1, z + 0, u1, vTop1, r, g, b, a, sky, block);
                vert(buffer, x + 1, y + minY, z + 0, u1, vBot, r, g, b, a, sky, block);
                vert(buffer, x + 0, y + minY, z + 0, u0, vBot, r, g, b, a, sky, block);
                break;
            case SOUTH:
                vert(buffer, x + 1, y + h1, z + 1, u0, vTop1, r, g, b, a, sky, block);
                vert(buffer, x + 0, y + h0, z + 1, u1, vTop0, r, g, b, a, sky, block);
                vert(buffer, x + 0, y + minY, z + 1, u1, vBot, r, g, b, a, sky, block);
                vert(buffer, x + 1, y + minY, z + 1, u0, vBot, r, g, b, a, sky, block);
                break;
            case WEST:
                vert(buffer, x + 0, y + h0, z + 1, u0, vTop0, r, g, b, a, sky, block);
                vert(buffer, x + 0, y + h1, z + 0, u1, vTop1, r, g, b, a, sky, block);
                vert(buffer, x + 0, y + minY, z + 0, u1, vBot, r, g, b, a, sky, block);
                vert(buffer, x + 0, y + minY, z + 1, u0, vBot, r, g, b, a, sky, block);
                break;
            case EAST:
                vert(buffer, x + 1, y + h0, z + 0, u0, vTop0, r, g, b, a, sky, block);
                vert(buffer, x + 1, y + h1, z + 1, u1, vTop1, r, g, b, a, sky, block);
                vert(buffer, x + 1, y + minY, z + 1, u1, vBot, r, g, b, a, sky, block);
                vert(buffer, x + 1, y + minY, z + 0, u0, vBot, r, g, b, a, sky, block);
                break;
            default:
                break;
        }
    }

    private static float sampleCorner(IBlockAccess access, BlockPos pos,
                                      float own, int ox, int oz) {
        float sum = own;
        int n = 1;
        BlockPos[] samples = new BlockPos[] {
                pos.add(ox < 0 ? -1 : 0, 0, oz < 0 ? -1 : 0),
                pos.add(ox > 0 ? 1 : 0, 0, oz < 0 ? -1 : 0),
                pos.add(ox < 0 ? -1 : 0, 0, oz > 0 ? 1 : 0),
                pos.add(ox > 0 ? 1 : 0, 0, oz > 0 ? 1 : 0)
        };
        for (int i = 0; i < samples.length; i++) {
            float h = fluidHeight(access, samples[i]);
            if (h > 0.0F) {
                sum += h;
                n++;
            }
        }
        return sum / (float) n;
    }

    private static float fluidHeight(IBlockAccess access, BlockPos pos) {
        if (access instanceof FluidViewAccess) {
            return ((FluidViewAccess) access).getFluidSurfaceHeight(pos);
        }
        IBlockState state = access.getBlockState(pos);
        if (state.getMaterial() == Material.WATER && state.getBlock() instanceof BlockLiquid) {
            int level = state.getValue(BlockLiquid.LEVEL).intValue();
            return level >= 8 ? 1.0F : (float) (8 - level) / 9.0F;
        }
        if (access instanceof World) {
            FluidStateBridge fluid = FluidStateMapper.fromWorld((World) access, pos, state);
            if (fluid != null && fluid.getKind() == FluidStateBridge.FluidKind.WATER) {
                return FluidHeightCalculator.getHeight((World) access, pos, fluid);
            }
        }
        return 0.0F;
    }

    private static boolean isSameFluid(IBlockAccess access, BlockPos pos) {
        IBlockState state = access.getBlockState(pos);
        if (state.getMaterial() == Material.WATER) {
            return true;
        }
        if (access instanceof World) {
            FluidStateBridge fluid = FluidStateMapper.fromWorld((World) access, pos, state);
            return fluid != null && fluid.getKind() == FluidStateBridge.FluidKind.WATER;
        }
        return false;
    }

    private static void vert(BufferBuilder buffer,
                             double x, double y, double z,
                             float u, float v,
                             int r, int g, int b, int a, int sky, int block) {
        buffer.pos(x, y, z).color(r, g, b, a).tex(u, v).lightmap(sky, block).endVertex();
    }
}
