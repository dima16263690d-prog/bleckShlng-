package com.dima16263690d.bleckshlng.fluid.api;

/**
 * Internal fluid API surface for BleckShlng (1.12.2).
 *
 * Architecture (1x1 cell model, inspired by 1.13+ FluidState):
 *
 *   FluidStateBridge     – immutable fluid in one cell (kind, amount, source)
 *   FluidWorldView       – read fluid from vanilla BlockLiquid OR waterlog sidecar
 *   FluidFaceMask        – which faces of a cell to draw (merge with neighbours)
 *   ChunkFluidData       – server persistence for waterlogged cells
 *   ClientFluidStateCache– client mirror for render
 *   FluidSpreadEngine    – 1.18-style flow + waterlog spread
 *   WaterloggedFluidHandler – tryWaterlog / pickupWater
 *   WaterloggedFluidRenderer – WorldLast draw of waterlogged cells only
 *
 * What is intentionally NOT a full 1.21 port:
 *   - No replacement of BlockDynamicLiquid registry
 *   - No translucent chunk mesher (would need Fluidlogged-API scale ASM)
 *   - Render of waterlogged cells is overlay; vanilla liquids stay vanilla mesh
 *
 * Expand here, not with ad-hoc patches in unrelated classes.
 */
public final class FluidAPI {
    private FluidAPI() {
    }
}
