package com.dima16263690d.bleckshlng.fluid;

import java.util.Random;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.Item;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/**
 * Port of {@code net.minecraft.world.level.material.LavaFluid} from Minecraft 1.21.
 * Nether behaviour is detected through {@link World#provider#doesWaterVaporize()}.
 */
public abstract class LavaFluid extends FlowingFluid {
    public static final float MIN_LEVEL_CUTOFF = 0.44444445F;

    protected LavaFluid(String name) {
        super(name);
    }

    @Override
    public Item getBucket() {
        return Items.LAVA_BUCKET;
    }

    @Override
    public boolean isSame(Fluid other) {
        return other == Fluids.LAVA || other == Fluids.FLOWING_LAVA;
    }

    @Override
    public boolean canBeReplacedWith(FluidState state, IBlockAccess world, BlockPos pos, Fluid other, EnumFacing dir) {
        return state.getHeight(world, pos) >= MIN_LEVEL_CUTOFF && Fluids.WATER.isSame(other);
    }

    @Override
    public int getTickDelay(World world) {
        return world.provider.doesWaterVaporize() ? 10 : 30;
    }

    @Override
    public int getSpreadDelay(World world, BlockPos pos, FluidState state, FluidState newState) {
        int delay = this.getTickDelay(world);
        if (!state.isEmpty() && !newState.isEmpty() && !state.isFalling() && !newState.isFalling()
                && newState.getHeight(world, pos) > state.getHeight(world, pos)
                && world.rand.nextInt(4) != 0) {
            delay *= 4;
        }
        return delay;
    }

    @Override
    protected int getDropOff(World world) {
        return world.provider.doesWaterVaporize() ? 1 : 2;
    }

    @Override
    protected int getSlopeFindDistance(World world) {
        return world.provider.doesWaterVaporize() ? 4 : 2;
    }

    @Override
    protected boolean canConvertToSource(World world) {
        return true;
    }

    @Override
    protected Fluid getFlowingFluid() {
        return Fluids.FLOWING_LAVA;
    }

    @Override
    protected Fluid getSourceFluid() {
        return Fluids.LAVA;
    }

    @Override
    protected void beforeDestroyingBlock(World world, BlockPos pos, IBlockState state) {
        this.fizz(world, pos);
    }

    @Override
    protected void spreadTo(World world, BlockPos pos, IBlockState blockState, EnumFacing direction, FluidState state) {
        if (direction == EnumFacing.DOWN && Fluids.WATER.isSame(FluidAccess.getFluidState(world, pos).getType())) {
            if (world.getBlockState(pos).getBlock() instanceof BlockLiquid) {
                world.setBlockState(pos, Blocks.STONE.getDefaultState(), 3);
            }
            this.fizz(world, pos);
            return;
        }
        super.spreadTo(world, pos, blockState, direction, state);
    }

    @Override
    public IBlockState createLegacyBlock(FluidState state) {
        int level = state.getLegacyLevel();
        net.minecraft.block.Block block = level == 0 ? Blocks.LAVA : Blocks.FLOWING_LAVA;
        return block.getDefaultState().withProperty(BlockLiquid.LEVEL, level);
    }

    @Override
    public float getExplosionResistance() {
        return 100.0F;
    }

    @Override
    public boolean isRandomlyTicking() {
        return true;
    }

    private void fizz(World world, BlockPos pos) {
        world.playEvent(1501, pos, 0);
    }

    @Override
    public void animateTick(World world, BlockPos pos, FluidState state, Random rand) {
        BlockPos above = pos.up();
        if (world.isAirBlock(above) && !world.getBlockState(above).isOpaqueCube()) {
            if (rand.nextInt(100) == 0) {
                double x = pos.getX() + rand.nextDouble();
                double y = pos.getY() + 1.0D;
                double z = pos.getZ() + rand.nextDouble();
                world.spawnParticle(EnumParticleTypes.LAVA, x, y, z, 0.0D, 0.0D, 0.0D);
                world.playSound(x, y, z, SoundEvents.BLOCK_LAVA_POP, SoundCategory.BLOCKS,
                        0.2F + rand.nextFloat() * 0.2F, 0.9F + rand.nextFloat() * 0.15F, false);
            }
            if (rand.nextInt(200) == 0) {
                world.playSound(pos.getX(), pos.getY(), pos.getZ(), SoundEvents.BLOCK_LAVA_AMBIENT, SoundCategory.BLOCKS,
                        0.2F + rand.nextFloat() * 0.2F, 0.9F + rand.nextFloat() * 0.15F, false);
            }
        }
    }

    @Override
    public void randomTick(World world, BlockPos pos, FluidState state, Random rand) {
        if (!world.getGameRules().getBoolean("doFireTick")) {
            return;
        }
        int rolls = rand.nextInt(3);
        if (rolls > 0) {
            BlockPos cursor = pos;
            for (int i = 0; i < rolls; i++) {
                cursor = cursor.add(rand.nextInt(3) - 1, 1, rand.nextInt(3) - 1);
                if (!world.isBlockLoaded(cursor)) {
                    return;
                }
                if (world.isAirBlock(cursor)) {
                    if (this.hasFlammableNeighbours(world, cursor)) {
                        world.setBlockState(cursor, Blocks.FIRE.getDefaultState(), 3);
                        return;
                    }
                } else if (world.getBlockState(cursor).getMaterial().blocksMovement()) {
                    return;
                }
            }
        } else {
            for (int i = 0; i < 3; i++) {
                BlockPos cursor = pos.add(rand.nextInt(3) - 1, 0, rand.nextInt(3) - 1);
                if (!world.isBlockLoaded(cursor)) {
                    return;
                }
                if (world.isAirBlock(cursor.up()) && this.isFlammable(world, cursor)) {
                    world.setBlockState(cursor.up(), Blocks.FIRE.getDefaultState(), 3);
                }
            }
        }
    }

    private boolean hasFlammableNeighbours(World world, BlockPos pos) {
        for (EnumFacing direction : EnumFacing.values()) {
            if (this.isFlammable(world, pos.offset(direction))) {
                return true;
            }
        }
        return false;
    }

    private boolean isFlammable(World world, BlockPos pos) {
        return world.isBlockLoaded(pos) && world.getBlockState(pos).getMaterial().getCanBurn();
    }

    public static class Flowing extends LavaFluid {
        public Flowing() {
            super("flowing_lava");
        }

        @Override
        protected boolean isSourceState() {
            return false;
        }

        @Override
        public int getAmount(FluidState state) {
            return state.getAmount();
        }

        @Override
        public boolean isSource(FluidState state) {
            return false;
        }
    }

    public static class Source extends LavaFluid {
        public Source() {
            super("lava");
        }

        @Override
        protected boolean isSourceState() {
            return true;
        }

        @Override
        public int getAmount(FluidState state) {
            return FluidState.AMOUNT_FULL;
        }

        @Override
        public boolean isSource(FluidState state) {
            return true;
        }
    }
}
