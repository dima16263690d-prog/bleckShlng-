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
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.ResourceLocation;
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
     * source rules: source lava becomes obsidian; any flowing lava becomes
     * cobblestone. Contact is based on the adjacent fluid state, not shape sampling.
     */
    public static boolean reactAt(World world, BlockPos pos, IBlockState state) {
        if (world == null || world.isRemote || pos == null || state == null
                || !world.isBlockLoaded(pos) || state.getMaterial() != Material.LAVA) {
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
            if (!world.isBlockLoaded(neighbor)) {
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
            // LiquidBlock#shouldSpreadLiquid in 1.18.2 also converts lava
            // above soul soil when blue ice is adjacent (above or horizontally).
            if (tryFormBasalt(world, pos)) {
                return true;
            }
            return false;
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
     * 1.18.2 LiquidBlock#shouldSpreadLiquid basalt reaction.
     *
     * Minecraft 1.12.2 has no built-in soul soil, blue ice or basalt blocks;
     * resolve their vanilla registry names at runtime so a backport providing
     * these blocks can participate without a compile-time dependency.
     */
    private static boolean tryFormBasalt(World world, BlockPos lavaPos) {
        Block soulSoil = Block.REGISTRY.getObject(
                new ResourceLocation("minecraft:soul_soil"));
        Block blueIce = Block.REGISTRY.getObject(
                new ResourceLocation("minecraft:blue_ice"));
        Block basalt = Block.REGISTRY.getObject(
                new ResourceLocation("minecraft:basalt"));
        if (soulSoil == null || blueIce == null || basalt == null
                || soulSoil == Blocks.AIR || blueIce == Blocks.AIR
                || basalt == Blocks.AIR) {
            return false;
        }

        BlockPos belowPos = lavaPos.down();
        if (!world.isBlockLoaded(belowPos)
                || world.getBlockState(belowPos).getBlock() != soulSoil) {
            return false;
        }

        // POSSIBLE_FLOW_DIRECTIONS in 1.18.2 is DOWN + horizontal; the
        // LiquidBlock check examines the opposite positions: UP + horizontal.
        for (EnumFacing direction : EnumFacing.Plane.HORIZONTAL) {
            BlockPos neighborPos = lavaPos.offset(direction);
            if (world.isBlockLoaded(neighborPos)
                    && world.getBlockState(neighborPos).getBlock() == blueIce) {
                world.setBlockState(lavaPos, basalt.getDefaultState(), 3);
                FluidShapeCalculator.invalidate(world, lavaPos);
                world.playEvent(1501, lavaPos, 0);
                return true;
            }
        }

        BlockPos abovePos = lavaPos.up();
        if (world.isBlockLoaded(abovePos)
                && world.getBlockState(abovePos).getBlock() == blueIce) {
            world.setBlockState(lavaPos, basalt.getDefaultState(), 3);
            FluidShapeCalculator.invalidate(world, lavaPos);
            world.playEvent(1501, lavaPos, 0);
            return true;
        }
        return false;
    }

    /**
     * Water entering a lava block reacts with that lava before replacing it.
     * Source lava becomes obsidian; every flowing level becomes cobblestone.
     */
    public static boolean reactWaterIntoLava(World world, BlockPos pos,
                                             IBlockState lavaState) {
        if (world == null || world.isRemote || pos == null || lavaState == null
                || !world.isBlockLoaded(pos) || lavaState.getMaterial() != Material.LAVA
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
        if (world == null || world.isRemote || pos == null || targetState == null
                || !world.isBlockLoaded(pos)) {
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

    public static void playExtinguishEffects(World world, BlockPos pos) {
        if (world != null && !world.isRemote && pos != null
                && world.isBlockLoaded(pos)) {
            // 1.18.2 LavaFluid/LiquidBlock use the common fizz level event.
            world.playEvent(1501, pos, 0);
        }
    }
