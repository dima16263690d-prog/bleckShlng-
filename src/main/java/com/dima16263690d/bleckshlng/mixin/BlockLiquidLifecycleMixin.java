package com.dima16263690d.bleckshlng.mixin;

import com.dima16263690d.bleckshlng.fluid.core.ModernFluidEngine;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ports the 1.18.2 LiquidBlock placement/neighbor lifecycle into the
 * version-independent fluid engine. Only native vanilla liquids are handled
 * by ModernFluidEngine; modded fluid blocks keep their own lifecycle.
 */
@Mixin(BlockLiquid.class)
public abstract class BlockLiquidLifecycleMixin {
    @Inject(method = "onBlockAdded", at = @At("HEAD"), require = 0)
    private void bleckshlng$onFluidBlockAdded(
            World world, BlockPos pos, IBlockState state,
            CallbackInfo callback) {
        if (state == null || world == null || pos == null
                || (state.getBlock() != net.minecraft.init.Blocks.WATER
                && state.getBlock() != net.minecraft.init.Blocks.LAVA)) {
            return;
        }
        ModernFluidEngine.onBlockAdded(world, pos);
    }

    @Inject(method = "neighborChanged", at = @At("HEAD"), require = 0)
    private void bleckshlng$onFluidNeighborChanged(
            IBlockState state, World world, BlockPos pos,
            Block neighborBlock, BlockPos neighborPos,
            CallbackInfo callback) {
        if (state == null || world == null || pos == null
                || (state.getBlock() != net.minecraft.init.Blocks.WATER
                && state.getBlock() != net.minecraft.init.Blocks.LAVA)) {
            return;
        }
        ModernFluidEngine.onNeighborChanged(world, pos);
    }
}
