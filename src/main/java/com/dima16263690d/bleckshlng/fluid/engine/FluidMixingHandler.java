package com.dima16263690d.bleckshlng.fluid.engine;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraft.world.World;
import com.dima16263690d.bleckshlng.fluid.geometry.FluidShapeCalculator;
import com.dima16263690d.bleckshlng.fluid.state.FluidStateBridge;
import com.dima16263690d.bleckshlng.fluid.state.FluidStateMapper;

/**
 * Water/lava reactions adapted to the legacy block registry.
 */
public final class FluidMixingHandler {
    private FluidMixingHandler() {
    }

    /**
     * Handles side/upper water contact with lava, matching the 1.12.2
     * source rules: source lava becomes obsidian; low-level flowing lava
     * becomes cobblestone.
     */
    public static boolean reactAt(World world, BlockPos pos, IBlockState state) {
        if (world == null || world.isRemote || state.getMaterial() != Material.LAVA) {
            return false;
        }

        boolean touchingWater = false;
        // LiquidBlock.POSSIBLE_FLOW_DIRECTIONS is DOWN + horizontal, and
        // checks the opposite positions: UP + horizontal (not below).
        for (EnumFacing side : EnumFacing.values()) {
            if (side == EnumFacing.DOWN) {
                continue;
            }

            BlockPos neighbor = pos.offset(side);
            if (!world.isBlockLoaded(neighbor)
                    || !FluidShapeCalculator.canPassThroughWall(world, pos, side)) {
                continue;
            }

            IBlockState neighborState = world.getBlockState(neighbor);
            if (neighborState.getMaterial() == Material.WATER) {
                touchingWater = true;
                break;
            }

            // In Java 1.13.2 a waterlogged source is still a source of water
            // for lava contact and mixing, even though its host block remains.
            FluidStateBridge neighborFluid = FluidStateMapper.fromWorld(
                    world, neighbor, neighborState);
            if (neighborFluid != null
                    && neighborFluid.getKind() == FluidStateBridge.FluidKind.WATER) {
                touchingWater = true;
                break;
            }
        }

        if (!touchingWater) {
            return reactBasalt(world, pos);
        }

        int level = state.getBlock() instanceof BlockLiquid
                ? state.getValue(BlockLiquid.LEVEL).intValue() : 0;

        // In 1.18.2 a lava source becomes obsidian; every flowing state,
        // regardless of its legacy metadata level, becomes cobblestone.
        world.setBlockState(pos, (level == 0
                ? Blocks.OBSIDIAN : Blocks.COBBLESTONE).getDefaultState(), 3);
        FluidShapeCalculator.invalidate(world, pos);
        playExtinguishEffects(world, pos);
        return true;
    }

    /**
     * Water entering a lava block reacts with that lava before replacing it.
     * Source lava becomes obsidian; flowing lava at level 4 or stronger
     * (legacy metadata 1..4) becomes cobblestone.
     */
    public static boolean reactWaterIntoLava(World world, BlockPos pos,
                                             IBlockState lavaState) {
        if (world == null || world.isRemote || pos == null || lavaState == null
                || lavaState.getMaterial() != Material.LAVA
                || !(lavaState.getBlock() instanceof BlockLiquid)) {
            return false;
        }

        int level = lavaState.getValue(BlockLiquid.LEVEL).intValue();
        world.setBlockState(pos, (level == 0
                ? Blocks.OBSIDIAN : Blocks.COBBLESTONE).getDefaultState(), 3);
        FluidShapeCalculator.invalidate(world, pos);
        playExtinguishEffects(world, pos);
        return true;
    }

    /**
     * Vanilla 1.12.2 turns lava flowing directly down into water into stone.
     */
    public static boolean reactDownward(World world, BlockPos pos, IBlockState targetState) {
        if (world == null || world.isRemote || pos == null || targetState == null) {
            return false;
        }

        FluidStateBridge targetFluid = FluidStateMapper.fromWorld(
                world, pos, targetState);
        if (targetFluid == null
                || targetFluid.getKind() != FluidStateBridge.FluidKind.WATER) {
            return false;
        }

        // 1.13.2: waterlogged blocks react as water, but lava does not replace
        // the host block with stone. Ordinary water blocks still become stone.
        if (targetState.getBlock() instanceof BlockLiquid
                && targetState.getMaterial() == Material.WATER) {
            world.setBlockState(pos, Blocks.STONE.getDefaultState(), 3);
            FluidShapeCalculator.invalidate(world, pos);
        }

        playExtinguishEffects(world, pos);
        return true;
    }

    /**
     * Modern lava turns into basalt above soul soil when blue ice is adjacent.
     * 1.12.2 has none of these vanilla blocks, so this is an optional registry
     * bridge: it activates only when a backport/mod provides all three blocks.
     */
    private static boolean reactBasalt(World world, BlockPos lavaPos) {
        Block below = world.getBlockState(lavaPos.down()).getBlock();
        if (!hasRegistryPath(below, "soul_soil")) {
            return false;
        }

        boolean blueIceNearby = false;
        for (EnumFacing side : EnumFacing.values()) {
            if (side == EnumFacing.DOWN) {
                continue;
            }
            BlockPos neighborPos = lavaPos.offset(side);
            if (world.isBlockLoaded(neighborPos)
                    && hasRegistryPath(world.getBlockState(neighborPos).getBlock(),
                    "blue_ice")) {
                blueIceNearby = true;
                break;
            }
        }
        if (!blueIceNearby) {
            return false;
        }

        Block basalt = findRegisteredBlock("basalt");
        if (basalt == null) {
            return false;
        }

        world.setBlockState(lavaPos, basalt.getDefaultState(), 3);
        FluidShapeCalculator.invalidate(world, lavaPos);
        playExtinguishEffects(world, lavaPos);
        return true;
    }

    private static boolean hasRegistryPath(Block block, String path) {
        ResourceLocation key = block == null ? null : block.getRegistryName();
        return key != null && path.equals(key.getResourcePath());
    }

    private static Block findRegisteredBlock(String path) {
        for (Block block : ForgeRegistries.BLOCKS.getValuesCollection()) {
            if (hasRegistryPath(block, path)) {
                return block;
            }
        }
        return null;
    }

    public static void playExtinguishEffects(World world, BlockPos pos) {
        world.playSound((EntityPlayer) null, pos, SoundEvents.BLOCK_LAVA_EXTINGUISH,
                SoundCategory.BLOCKS, 0.5F,
                2.6F + (world.rand.nextFloat() - world.rand.nextFloat()) * 0.8F);

        for (int i = 0; i < 8; i++) {
            world.spawnParticle(EnumParticleTypes.SMOKE_LARGE,
                    pos.getX() + world.rand.nextDouble(),
                    pos.getY() + 1.2D,
                    pos.getZ() + world.rand.nextDouble(),
                    0.0D, 0.0D, 0.0D);
        }
    }
}
