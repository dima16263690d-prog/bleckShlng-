package com.dima16263690d.bleckshlng.block;

import java.util.Random;
import com.dima16263690d.bleckshlng.fluid.FluidAccess;
import com.dima16263690d.bleckshlng.fluid.FluidState;
import com.dima16263690d.bleckshlng.fluid.FlowingFluid;
import net.minecraft.block.Block;
import net.minecraft.block.BlockStaticLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/**
 * A liquid block whose behaviour is delegated to the ported 1.21 {@link FlowingFluid} engine.
 * Registered in place of the vanilla {@code water} / {@code lava} blocks.
 *
 * <p>As in modern Minecraft the engine writes this block for every fluid state, so both the source
 * and the settled (non-flowing) states are handled here; the engine is driven from the regular
 * block tick and the vanilla mixing rules (obsidian/cobblestone/stone) are preserved through
 * {@code BlockStaticLiquid} / {@code BlockDynamicLiquid}.</p>
 */
public class BlockStaticFluid extends BlockStaticLiquid {
    private final FlowingFluid fluid;

    public BlockStaticFluid(FlowingFluid fluid, Material material, String name, String translationKey) {
        super(material);
        this.fluid = fluid;
        this.setRegistryName("minecraft", name);
        this.setUnlocalizedName(translationKey);
        this.setHardness(100.0F);
        if (material == Material.WATER) {
            this.setLightOpacity(3);
        } else {
            this.setLightLevel(1.0F);
        }
        this.disableStats();
    }

    public FlowingFluid getFluid() {
        return this.fluid;
    }

    @Override
    public void onBlockAdded(World world, BlockPos pos, IBlockState state) {
        if (!this.checkForMixing(world, pos, state)) {
            world.scheduleUpdate(pos, this, this.fluid.getTickDelay(world));
        }
    }

    @Override
    public void updateTick(World world, BlockPos pos, IBlockState state, Random rand) {
        if (!world.isAreaLoaded(pos, 4)) {
            return;
        }
        if (this.checkForMixing(world, pos, state)) {
            return;
        }
        FluidState fluidState = FluidAccess.getFluidState(world, pos);
        this.fluid.tick(world, pos, state, fluidState);
    }

    @Override
    public void randomTick(World world, BlockPos pos, IBlockState state, Random rand) {
        if (!world.isAreaLoaded(pos, 4)) {
            return;
        }
        FluidState fluidState = FluidAccess.getFluidState(world, pos);
        if (!fluidState.isEmpty()) {
            this.fluid.randomTick(world, pos, fluidState, rand);
        }
    }

    @Override
    public void neighborChanged(IBlockState state, World world, BlockPos pos, Block blockIn, BlockPos fromPos) {
        if (!this.checkForMixing(world, pos, state)) {
            world.scheduleUpdate(pos, this, this.fluid.getTickDelay(world));
        }
    }

    @Override
    public void randomDisplayTick(IBlockState stateIn, World worldIn, BlockPos pos, Random rand) {
        FluidState fluidState = FluidAccess.getFluidState(worldIn, pos);
        if (!fluidState.isEmpty()) {
            this.fluid.animateTick(worldIn, pos, fluidState, rand);
        }
    }

    @Override
    public int tickRate(World worldIn) {
        return this.fluid.getTickDelay(worldIn);
    }

    @Override
    protected Vec3d getFlow(IBlockAccess world, BlockPos pos, IBlockState state) {
        FluidState fluidState = FluidAccess.getFluidState(world, pos);
        if (fluidState.isEmpty()) {
            return Vec3d.ZERO;
        }
        return this.fluid.getFlow(world, pos, fluidState);
    }
}
