package com.dima16263690d.bleckshlng.client.render;

import java.lang.reflect.Field;
import java.util.Map;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.ChunkCache;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.dima16263690d.bleckshlng.fluid.geometry.WaterloggedBlockRegistry;
import com.dima16263690d.bleckshlng.fluid.network.ClientFluidStateCache;
import com.dima16263690d.bleckshlng.fluid.state.FluidStateBridge;

/**
 * Client hook called from RenderChunk.postRenderBlocks.
 *
 * The chunk's translucent BufferBuilder is still open at this point. We add
 * waterlogged quads there so they share chunk culling, sorting and upload with
 * ordinary translucent geometry instead of drawing a camera-relative overlay.
 */
@SideOnly(Side.CLIENT)
public final class WaterloggedChunkRenderHooks {
    private static final Logger LOGGER =
            LogManager.getLogger("BleckShlng/ChunkFluidRender");
    private static volatile Field chunkCacheField;
    private static volatile boolean cacheFieldResolved;
    private static volatile boolean warned;

    private WaterloggedChunkRenderHooks() {
    }

    public static void renderTranslucentLayer(RenderChunk renderChunk,
                                              BlockRenderLayer layer,
                                              BufferBuilder buffer) {
        if (renderChunk == null || buffer == null
                || layer != BlockRenderLayer.TRANSLUCENT) {
            return;
        }

        try {
            World world = renderChunk.getWorld();
            BlockPos origin = renderChunk.getPosition();
            if (world == null || origin == null) {
                return;
            }

            int dimension = world.provider.getDimension();
            int chunkX = origin.getX() >> 4;
            int chunkZ = origin.getZ() >> 4;
            Map<Long, FluidStateBridge> states =
                    ClientFluidStateCache.copyChunk(dimension, chunkX, chunkZ);
            if (states.isEmpty()) {
                return;
            }

            IBlockAccess chunkAccess = getChunkAccess(renderChunk);
            if (chunkAccess == null) {
                // Defensive fallback. Normal 1.12.2 chunk compilation exposes
                // its ChunkCache on RenderChunk; the fallback keeps rendering
                // functional if another transformer changes that field.
                chunkAccess = world;
            }

            FluidViewAccess fluidAccess =
                    new FluidViewAccess(chunkAccess, dimension);

            int minX = origin.getX();
            int minY = origin.getY();
            int minZ = origin.getZ();
            int maxX = minX + 16;
            int maxY = minY + 16;
            int maxZ = minZ + 16;

            for (Map.Entry<Long, FluidStateBridge> entry : states.entrySet()) {
                Long packed = entry.getKey();
                FluidStateBridge fluid = entry.getValue();
                if (packed == null || fluid == null
                        || fluid.getKind() != FluidStateBridge.FluidKind.WATER) {
                    continue;
                }

                BlockPos pos = BlockPos.fromLong(packed.longValue());
                if (pos.getX() < minX || pos.getX() >= maxX
                        || pos.getY() < minY || pos.getY() >= maxY
                        || pos.getZ() < minZ || pos.getZ() >= maxZ) {
                    continue;
                }

                IBlockStateAccess.render(fluidAccess, chunkAccess, pos, fluid, buffer);
            }
        } catch (Throwable error) {
            if (!warned) {
                warned = true;
                LOGGER.error("Waterlogged chunk meshing failed; check the first RenderChunk hook error",
                        error);
            }
        }
    }

    private static IBlockAccess getChunkAccess(RenderChunk renderChunk) {
        try {
            Field field = getChunkCacheField();
            if (field == null) {
                return null;
            }
            Object value = field.get(renderChunk);
            return value instanceof IBlockAccess ? (IBlockAccess) value : null;
        } catch (Throwable error) {
            if (!warned) {
                warned = true;
                LOGGER.warn("Could not read RenderChunk ChunkCache; falling back to World", error);
            }
            return null;
        }
    }

    private static Field getChunkCacheField() {
        if (cacheFieldResolved) {
            return chunkCacheField;
        }
        synchronized (WaterloggedChunkRenderHooks.class) {
            if (cacheFieldResolved) {
                return chunkCacheField;
            }
            try {
                for (Field field : RenderChunk.class.getDeclaredFields()) {
                    if (ChunkCache.class.isAssignableFrom(field.getType())) {
                        field.setAccessible(true);
                        chunkCacheField = field;
                        break;
                    }
                }
            } catch (Throwable error) {
                LOGGER.warn("Could not resolve RenderChunk ChunkCache field", error);
            }
            cacheFieldResolved = true;
            return chunkCacheField;
        }
    }

    /**
     * Small indirection keeps the hook free of client-world mutation logic.
     * The mesher reads the host shape from the rebuild cache and virtual fluid
     * states from FluidViewAccess.
     */
    private static final class IBlockStateAccess {
        private static void render(FluidViewAccess fluidAccess,
                                  IBlockAccess rawAccess,
                                  BlockPos pos,
                                  FluidStateBridge fluid,
                                  BufferBuilder buffer) {
            net.minecraft.block.state.IBlockState host =
                    rawAccess.getBlockState(pos);
            if (host == null || host.getMaterial().isLiquid()
                    || !WaterloggedBlockRegistry.canWaterlog(host.getBlock())) {
                return;
            }
            WaterloggedChunkMesher.tessellate(
                    fluidAccess, host, pos, fluid, buffer);
        }
    }
}
