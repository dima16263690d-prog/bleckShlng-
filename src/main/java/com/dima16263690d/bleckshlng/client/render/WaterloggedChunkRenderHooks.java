package com.dima16263690d.bleckshlng.client.render;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.RegionRenderCacheBuilder;
import net.minecraft.client.renderer.chunk.ChunkCompileTaskGenerator;
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

    /**
     * Fallback for a waterlogged-only chunk. Vanilla never starts a render
     * layer if no ordinary block belongs to it. At rebuild RETURN all vanilla
     * layers are finalized, so an unused translucent builder can be opened,
     * filled, sorted and finalized exactly once before the worker uploads it.
     *
     * If vanilla already started TRANSLUCENT, postRenderBlocks has handled the
     * fluid; this method deliberately does nothing.
     */
    public static void renderMissingTranslucentLayer(
            RenderChunk renderChunk,
            ChunkCompileTaskGenerator generator,
            float cameraX,
            float cameraY,
            float cameraZ) {
        if (renderChunk == null || generator == null
                || generator.getType() != ChunkCompileTaskGenerator.Type.REBUILD_CHUNK
                || generator.getStatus() != ChunkCompileTaskGenerator.Status.COMPILING
                || !hasWaterloggedFluid(renderChunk)) {
            return;
        }

        CompiledChunk compiledChunk = generator.getCompiledChunk();
        BlockRenderLayer layer = BlockRenderLayer.TRANSLUCENT;
        if (compiledChunk == null || compiledChunk.isLayerStarted(layer)) {
            return;
        }

        RegionRenderCacheBuilder cacheBuilder = generator.getRegionRenderCacheBuilder();
        if (cacheBuilder == null) {
            return;
        }

        BufferBuilder buffer = cacheBuilder.getWorldRendererByLayer(layer);
        if (buffer == null) {
            return;
        }

        BlockPos origin = renderChunk.getPosition();
        boolean began = false;
        try {
            // begin() is deliberately inside the guarded block. A third-party
            // renderer that left this builder open must not crash the client.
            buffer.begin(7, DefaultVertexFormats.BLOCK);
            began = true;
            buffer.setTranslation(-origin.getX(), -origin.getY(), -origin.getZ());

            if (!renderTranslucentLayer(renderChunk, layer, buffer)) {
                return;
            }

            compiledChunk.setLayerStarted(layer);
            markLayerUsed(compiledChunk, layer);
            buffer.sortVertexData(cameraX, cameraY, cameraZ);
            compiledChunk.setState(buffer.getVertexState());
        } catch (Throwable error) {
            LOGGER.error("Could not append waterlogged-only translucent layer for chunk {}",
                    origin, error);
        } finally {
            if (began) {
                try {
                    buffer.finishDrawing();
                } catch (Throwable finishError) {
                    LOGGER.error("Could not finish waterlogged translucent layer for chunk {}",
                            origin, finishError);
                }
            }
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
                    new FluidViewAccess(chunkAccess, dimension, world);

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
            // Render the virtual fluid state through the same dispatcher
            // path used by Fluidlogged-API. The host block remains in its
            // normal SOLID/CUTOUT layer and depth-occludes the parts of the
            // fluid mesh that lie inside its collision/rendered shape.
            WaterloggedFluidRenderer.renderIntoChunk(
                    buffer, fluidAccess, pos, fluid);
        }
    }
}
