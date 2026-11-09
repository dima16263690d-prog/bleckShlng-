package com.dima16263690d.bleckshlng.mixin.client;

import java.util.Random;

import com.dima16263690d.bleckshlng.fluid.core.ModernFluidEngine;
import com.dima16263690d.bleckshlng.client.WaterloggedFluidAnimateHandler;

import net.minecraft.block.BlockLiquid;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Routes vanilla water/lava ambient particles and sounds through the modern
 * Fluid#animateTick implementation. The original method remains untouched
 * for modded BlockLiquid subclasses.
 */
@Mixin(value = BlockLiquid.class, remap = false)
public abstract class BlockLiquidAnimateMixin {
    @Inject(
            method = {"randomDisplayTick", "func_180655_c"},
            at = @At("HEAD"),
            cancellable = true,
            require = 0,
            remap = false
    )
    private void bleckshlng$animateModernFluid(
            World world, BlockPos pos, net.minecraft.block.state.IBlockState state,
            Random random, CallbackInfo callback) {
        if (state == null || world == null || pos == null || random == null) {
            return;
        }
        if (state.getBlock() != Blocks.WATER
                && state.getBlock() != Blocks.FLOWING_WATER
                && state.getBlock() != Blocks.LAVA
                && state.getBlock() != Blocks.FLOWING_LAVA) {
            return;
        }

        if (ModernFluidEngine.animateTick(world, pos, random)) {
            // 1.18.2 samples Fluid#getDripParticle separately from animateTick.
            if (random.nextInt(10) == 0) {
                WaterloggedFluidAnimateHandler.trySpawnDrippingFluid(world, pos);
            }
            callback.cancel();
        }
    }
}
