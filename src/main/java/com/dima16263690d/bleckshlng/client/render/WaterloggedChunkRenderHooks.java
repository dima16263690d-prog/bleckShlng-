package com.dima16263690d.bleckshlng.client.render;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.chunk.CompiledChunk;
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
 * Client hook called from RenderChunk.rebuildChunk before vanilla finalizes
 * render layers. MixinBooter starts the translucent layer when necessary,
 * even when the chunk contains no ordinary translucent blocks.
 */
@SideOnly(Side.CLIENT)
public final class WaterloggedChunkRenderHooks {
    private static final Logger LOGGER =
            LogManager.getLogger("BleckShlng/ChunkFluidRender");
    private static volatile Field chunkCacheField;
    private static volatile boolean cacheFieldResolved;
    private static volatile boolean warned;
    private static final ThreadLocal<Boolean> WATERLOGGED_REBUILD_ACTIVE =
            new ThreadLocal<Boolean>();

    private static volatile Method compiledChunkSetLayerUsed;
    private static volatile boolean compiledChunkMethodResolved;

    private WaterloggedChunkRenderHooks() {
    }

    public static boolean hasWaterloggedFluid(RenderChunk renderChunk) {
        if (renderChunk == null) {
            return false;
        }
        try {
            World world = renderChunk.getWorld();
            BlockPos origin = renderChunk.getPosition();
            if (world == null || origin == null) {
                return false;
            }
            int dimension = world.provider.getDimension();
            int chunkX = origin.getX() >> 4;
            int chunkZ = origin.getZ() >> 4;
            return !ClientFluidStateCache.copyChunk(dimension, chunkX, chunkZ).isEmpty();
        } catch (Throwable error) {
            if (!warned) {
                warned = true;
                LOGGER.error("Could not inspect waterlogged states before chunk rebuild", error);
            }
            return false;
        }
    }

    /**
     * Marks the current chunk-compile worker while it is rebuilding a chunk
     * with waterlogged states. Transparency resort tasks must not append the
     * same sidecar quads a second time.
     */
    public static void beginWaterloggedRebuild(RenderChunk renderChunk) {
        WATERLOGGED_REBUILD_ACTIVE.remove();
        if (hasWaterloggedFluid(renderChunk)) {
            WATERLOGGED_REBUILD_ACTIVE.set(Boolean.TRUE);
        }
    }

    public static boolean isWaterloggedRebuildActive() {
        return Boolean.TRUE.equals(WATERLOGGED_REBUILD_ACTIVE.get());
    }

    public static void endWaterloggedRebuild() {
        WATERLOGGED_REBUILD_ACTIVE.remove();
    }

    /**
     * CompiledChunk#setLayerUsed is protected in Forge 1.12.2. Resolve the
     * mapped method once instead of widening vanilla visibility with an AT.
     */
    public static void markLayerUsed(CompiledChunk compiledChunk,
                                     BlockRenderLayer layer) {
        if (compiledChunk == null || layer == null) {
            return;
        }
        try {
            Method method = getCompiledChunkSetLayerUsed();
            if (method == null) {
                throw new NoSuchMethodException(
                        "CompiledChunk#setLayerUsed(BlockRenderLayer)");
            }
            method.invoke(compiledChunk, layer);
        } catch (Throwable error) {
            if (!warned) {
                warned = true;
                LOGGER.error("Could not mark the translucent fluid layer as used", error);
            }
        }
    }

    private static Method getCompiledChunkSetLayerUsed() {
        if (compiledChunkMethodResolved) {
            return compiledChunkSetLayerUsed;
        }
        synchronized (WaterloggedChunkRenderHooks.class) {
            if (compiledChunkMethodResolved) {
                return compiledChunkSetLayerUsed;
            }
            String[] names = {"setLayerUsed", "func_178486_a"};
            for (String name : names) {
                try {
                    Method method = CompiledChunk.class.getDeclaredMethod(
                            name, BlockRenderLayer.class);
                    method.setAccessible(true);
                    compiledChunkSetLayerUsed = method;
                    break;
                } catch (NoSuchMethodException ignored) {
                    // Try the other MCP/SRG name.
                } catch (Throwable error) {
                    LOGGER.warn("Could not resolve CompiledChunk#setLayerUsed", error);
                    break;
                }
            }
            compiledChunkMethodResolved = true;
            return compiledChunkSetLayerUsed;
        }
    }

    public static boolean renderTranslucentLayer(RenderChunk renderChunk,
                                                 BlockRenderLayer layer,
                                                 BufferBuilder buffer) {
        if (renderChunk == null || buffer == null
                || layer != BlockRenderLayer.TRANSLUCENT) {
            return false;
        }

        int verticesBefore = buffer.getVertexCount();
        try {
            World world = renderChunk.getWorld();
            BlockPos origin = renderChunk.getPosition();
            if (world == null || origin == null) {
                return false;
            }

            int dimension = world.provider.getDimension();
            int chunkX = origin.getX() >> 4;
            int chunkZ = origin.getZ() >> 4;
            Map<Long, FluidStateBridge> states =
                    ClientFluidStateCache.copyChunk(dimension, chunkX, chunkZ);
            if (states.isEmpty()) {
                return false;
            }

            IBlockAccess chunkAccess = getChunkAccess(renderChunk);
            if (chunkAccess == null) {
                // Defensive fallback for environments that replace RenderChunk's cache field.
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
            return buffer.getVertexCount() > verticesBefore;
        } catch (Throwable error) {
            if (!warned) {
                warned = true;
                LOGGER.error("Waterlogged chunk meshing failed; check the first RenderChunk hook error",
                        error);
            }
            return false;
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
            if (host.getBlock() instanceof net.minecraft.block.BlockSlab
                    && !((net.minecraft.block.BlockSlab) host.getBlock()).isDouble()) {
                // Slabs need a clipped vertical interval. Keep the explicit
                // cavity mesher for lower/upper slab halves.
                WaterloggedChunkMesher.tessellate(
                        fluidAccess, host, pos, fluid, buffer);
            } else {
                // Reuse the 1.12.2 vanilla fluid renderer for every other
                // supported host shape: flow UVs, sprites, tint and light.
                WaterloggedFluidRenderer.renderIntoChunk(
                        buffer, fluidAccess, pos, fluid);
            }
        }
    }
}
