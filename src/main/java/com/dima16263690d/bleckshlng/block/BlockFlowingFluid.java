package com.dima16263690d.bleckshlng.block;

import java.util.Random;
import com.dima16263690d.bleckshlng.fluid.FluidAccess;
import com.dima16263690d.bleckshlng.fluid.FluidState;
import com.dima16263690d.bleckshlng.fluid.FlowingFluid;
import net.minecraft.block.Block;
import net.minecraft.block.BlockDynamicLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/**
 * A flowing liquid block whose entire update logic is delegated to the ported 1.21
 * {@link FlowingFluid} engine instead of the legacy {@code BlockDynamicLiquid} algorithm.
 *
 * <p>Registered in place of the vanilla {@code flowing_water} / {@code flowing_lava} blocks so the
 * ported physics drive the vanilla fluids.</p>
 */
public class BlockFlowingFluid extends BlockDynamicLiquid {
    private final FlowingFluid fluid;

    public BlockFlowingFluid(FlowingFluid fluid, Material material, String name, String translationKey) {
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
        this.setTickRandomly(true);
    }

    public FlowingFluid getFluid() {
        return this.fluid;
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
    public void neighborChanged(IBlockState state, World world, BlockPos pos, Block blockIn, BlockPos fromPos) {
        if (!this.checkForMixing(world, pos, state)) {
            world.scheduleUpdate(pos, this, this.fluid.getTickDelay(world));
        }
    }

    @Override
    public void randomDisplayTick(IBlockState stateIn, World worldIn, BlockPos pos, Random rand) {
        FluidState fluidState = FluidAccess.getFluidState(worldIn, pos);
        this.fluid.animateTick(worldIn, pos, fluidState, rand);
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
