package com.dima16263690d.bleckshlng.fluid.compatibility;

import java.util.Random;

import com.dima16263690d.bleckshlng.fluid.core.Fluid;
import com.dima16263690d.bleckshlng.fluid.core.FluidDirection;
import com.dima16263690d.bleckshlng.fluid.core.FluidParticle;
import com.dima16263690d.bleckshlng.fluid.core.FluidPos;
import com.dima16263690d.bleckshlng.fluid.core.FluidSound;
import com.dima16263690d.bleckshlng.fluid.core.FluidState;
import com.dima16263690d.bleckshlng.fluid.core.FluidWorld;
import com.dima16263690d.bleckshlng.fluid.core.Fluids;
import com.dima16263690d.bleckshlng.fluid.geometry.BlockGeometryAdapter;
import com.dima16263690d.bleckshlng.fluid.geometry.FluidShapeCalculator;
import com.dima16263690d.bleckshlng.fluid.geometry.WaterloggedBlockRegistry;
import com.dima16263690d.bleckshlng.fluid.network.FluidNetwork;
import com.dima16263690d.bleckshlng.fluid.state.FluidLevelMapper;
import com.dima16263690d.bleckshlng.fluid.state.FluidStateBridge;
import com.dima16263690d.bleckshlng.fluid.state.FluidStateMapper;
import com.dima16263690d.bleckshlng.fluid.waterlogging.ChunkFluidData;
import com.dima16263690d.bleckshlng.fluid.waterlogging.FluidTickScheduler;
import com.dima16263690d.bleckshlng.fluid.waterlogging.IFluidWaterloggable;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.ForgeEventFactory;

/**
 * The only world/block integration point used by the portable 1.18.2 fluid
 * domain. Fluid algorithms do not depend on World, IBlockState or metadata.
 */
public final class Minecraft112FluidWorldAdapter implements FluidWorld {
    private final World world;

    public Minecraft112FluidWorldAdapter(World world) {
        if (world == null) {
            throw new IllegalArgumentException("World cannot be null");
        }
        this.world = world;
    }

    public World getWorld() {
        return world;
    }

    @Override
    public boolean isLoaded(FluidPos pos) {
        return pos != null && world.isBlockLoaded(toBlockPos(pos));
    }

    @Override
    public FluidState getFluidState(FluidPos pos) {
        if (!isLoaded(pos)) {
            return FluidState.EMPTY;
        }

        BlockPos blockPos = toBlockPos(pos);
        IBlockState host = world.getBlockState(blockPos);
        FluidStateBridge state = FluidStateMapper.fromWorld(world, blockPos, host);
        if (state == null) {
            return FluidState.EMPTY;
        }

        if (state.getKind() == FluidStateBridge.FluidKind.WATER) {
            return state.isSource()
                    ? Fluids.waterSource()
                    : Fluids.WATER.flowingState(state.getAmount(), state.isFalling());
        }
        return state.isSource()
                ? Fluids.lavaSource()
                : Fluids.LAVA.flowingState(state.getAmount(), state.isFalling());
    }

    @Override
    public boolean blocksMotion(FluidPos pos) {
        return !isLoaded(pos) || world.getBlockState(toBlockPos(pos))
                .getMaterial().blocksMovement();
    }

    @Override
    public boolean isSolidMaterial(FluidPos pos) {
        return isLoaded(pos) && world.getBlockState(toBlockPos(pos))
                .getMaterial().isSolid();
    }

    @Override
    public boolean isAir(FluidPos pos) {
        return isLoaded(pos) && world.isAirBlock(toBlockPos(pos));
    }

    @Override
    public boolean isIce(FluidPos pos) {
        return isLoaded(pos) && world.getBlockState(toBlockPos(pos))
                .getMaterial() == Material.ICE;
    }

    @Override
    public boolean isFaceSturdy(FluidPos pos, FluidDirection face) {
        if (!isLoaded(pos)) {
            return true;
        }
        BlockPos blockPos = toBlockPos(pos);
        return world.getBlockState(blockPos).isSideSolid(
                world, blockPos, toFacing(face));
    }

    @Override
    public boolean canPassThroughWall(FluidPos from, FluidDirection direction) {
        if (!isLoaded(from)) {
            return false;
        }
        return FluidShapeCalculator.canPassThroughWall(
                world, toBlockPos(from), toFacing(direction));
    }

    @Override
    public boolean canHoldFluid(FluidPos target, Fluid fluid) {
        if (!isLoaded(target) || fluid == null) {
            return false;
        }
        BlockPos blockPos = toBlockPos(target);
        IBlockState state = world.getBlockState(blockPos);

        // Do not take over third-party Forge fluid blocks.
        if (ForgeFluidCompatibility.isForgeFluidBlock(state)) {
            return false;
        }

        Material material = toMaterial(fluid);
        if (state.getBlock() instanceof BlockLiquid) {
            return true;
        }
        if (material == Material.WATER
                && WaterloggedBlockRegistry.canWaterlog(state.getBlock())) {
            return BlockGeometryAdapter.canHoldFluid(
                    world, blockPos, state, material);
        }
        return BlockGeometryAdapter.canHoldFluid(
                world, blockPos, state, material);
    }

    @Override
    public boolean canReplaceFluid(FluidPos target, Fluid incoming,
                                   FluidDirection direction) {
        if (!isLoaded(target) || incoming == null) {
            return false;
        }
        BlockPos blockPos = toBlockPos(target);
        IBlockState state = world.getBlockState(blockPos);
        if (ForgeFluidCompatibility.isForgeFluidBlock(state)) {
            return false;
        }

        // A dry waterloggable host is not a replaceable block in 1.12.2,
        // but the modern fluid API may store water inside it without replacing
        // the host state. canHoldFluid() separately rejects already-filled hosts.
        if (incoming.isSame(Fluids.WATER)
                && state.getMaterial() != Material.WATER
                && WaterloggedBlockRegistry.canWaterlog(state.getBlock())) {
            return true;
        }

        FluidState existing = getFluidState(target);
        if (!existing.isEmpty()) {
            return existing.getType().canBeReplacedWith(
                    existing, this, target, incoming, direction);
        }

        return state.getMaterial() == Material.AIR
                || state.getBlock().isReplaceable(world, blockPos)
                || (incoming.isSame(Fluids.LAVA)
                && state.getMaterial() == Material.WATER
                && direction == FluidDirection.DOWN);
    }

    @Override
    public boolean canCreateFluidSource(FluidPos target, Fluid fluid) {
        if (!isLoaded(target) || fluid == null || !fluid.isSame(Fluids.WATER)) {
            return false;
        }
        return ForgeEventFactory.canCreateFluidSource(
                world, toBlockPos(target), Blocks.FLOWING_WATER.getDefaultState(), true);
    }

    @Override
    public void setFluidState(FluidPos pos, FluidState state) {
        if (!isLoaded(pos) || state == null || state.isEmpty()) {
            return;
        }

        BlockPos blockPos = toBlockPos(pos);
        IBlockState host = world.getBlockState(blockPos);
        if (state.getType().isSame(Fluids.WATER)
                && WaterloggedBlockRegistry.canWaterlog(host.getBlock())
                && host.getMaterial() != Material.WATER
                && host.getMaterial() != Material.LAVA) {
            storeWaterlogged(blockPos, host, state);
            return;
        }

        world.setBlockState(blockPos, toLegacyState(state), 2);
        FluidShapeCalculator.invalidate(world, blockPos);
    }

    @Override
    public void removeFluid(FluidPos pos) {
        if (!isLoaded(pos)) {
            return;
        }

        BlockPos blockPos = toBlockPos(pos);
        IBlockState host = world.getBlockState(blockPos);
        if (world.isRemote) {
            return;
        }

        ChunkFluidData data = ChunkFluidData.get(world);
        boolean removedSidecar = data != null && data.remove(blockPos);
        if (removedSidecar) {
            FluidNetwork.removeState(world, blockPos);
        }

        if (host.getBlock() instanceof BlockLiquid) {
            world.setBlockToAir(blockPos);
        } else if (host.getBlock() instanceof IFluidWaterloggable) {
            IFluidWaterloggable container = (IFluidWaterloggable) host.getBlock();
            if (container.isWaterlogged(host)) {
                IBlockState dry = container.withWaterlogged(host, false);
                if (dry != null) {
                    world.setBlockState(blockPos, dry, 3);
                }
            }
        }
        FluidShapeCalculator.invalidate(world, blockPos);
        world.notifyNeighborsOfStateChange(blockPos, host.getBlock(), false);
    }

    @Override
    public void spreadFluidTo(FluidPos target, FluidState incoming,
                              FluidDirection direction) {
        if (!isLoaded(target) || incoming == null || incoming.isEmpty()) {
            return;
        }

        BlockPos blockPos = toBlockPos(target);
        IBlockState host = world.getBlockState(blockPos);
        Material incomingMaterial = toMaterial(incoming.getType());

        if (incomingMaterial == Material.WATER
                && host.getMaterial() == Material.LAVA
                && host.getBlock() instanceof BlockLiquid) {
            com.dima16263690d.bleckshlng.fluid.engine.FluidMixingHandler
                    .reactWaterIntoLava(world, blockPos, host);
            return;
        }

        if (incomingMaterial == Material.LAVA
                && direction == FluidDirection.DOWN
                && host.getMaterial() == Material.WATER
                && host.getBlock() instanceof BlockLiquid) {
            com.dima16263690d.bleckshlng.fluid.engine.FluidMixingHandler
                    .reactDownward(world, blockPos, host);
            return;
        }

        if (incomingMaterial == Material.WATER
                && host.getMaterial() != Material.WATER
                && WaterloggedBlockRegistry.canWaterlog(host.getBlock())) {
            storeWaterlogged(blockPos, host, incoming);
            return;
        }

        if (ForgeFluidCompatibility.isForgeFluidBlock(host)) {
            return;
        }

        if (host.getMaterial() != Material.AIR
                && host.getMaterial() != Material.WATER
                && host.getMaterial() != Material.LAVA) {
            if (incomingMaterial == Material.LAVA) {
                com.dima16263690d.bleckshlng.fluid.engine.FluidMixingHandler
                        .playExtinguishEffects(world, blockPos);
            } else {
                host.getBlock().dropBlockAsItem(world, blockPos, host, 0);
            }
        }

        world.setBlockState(blockPos, toLegacyState(incoming), 3);
        FluidShapeCalculator.invalidate(world, blockPos);
        if (incomingMaterial == Material.LAVA) {
            com.dima16263690d.bleckshlng.fluid.engine.FluidMixingHandler
                    .reactAt(world, blockPos, world.getBlockState(blockPos));
        }

        // 1.18.2 schedules every successfully placed fluid state itself.
        // In 1.12.2 the adapter owns the scheduled update for native blocks.
        if (!getFluidState(target).isEmpty()) {
            scheduleFluidTick(target, incoming.getType(),
                    incoming.getType().getTickDelay(this));
        }
    }

    @Override
    public void scheduleFluidTick(FluidPos pos, Fluid fluid, int delay) {
        if (!isLoaded(pos) || fluid == null || world.isRemote) {
            return;
        }
        world.scheduleUpdate(toBlockPos(pos), flowingBlock(fluid), Math.max(1, delay));
    }

    @Override
    public void notifyFluidNeighbors(FluidPos pos, Fluid fluid) {
        if (!isLoaded(pos)) {
            return;
        }
        world.notifyNeighborsOfStateChange(
                toBlockPos(pos), flowingBlock(fluid), false);
    }

    @Override
    public boolean isUltraWarm() {
        return world.provider.doesWaterVaporize();
    }

    @Override
    public boolean isFireTickEnabled() {
        return world.getGameRules().getBoolean("doFireTick");
    }

    @Override
    public boolean hasFlammableNeighbor(FluidPos pos) {
        if (pos == null) {
            return false;
        }
        for (FluidDirection direction : FluidDirection.values()) {
            FluidPos neighbor = pos.offset(direction);
            if (isFlammable(neighbor)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isFlammable(FluidPos pos) {
        if (!isLoaded(pos)) {
            return false;
        }
        BlockPos blockPos = toBlockPos(pos);
        IBlockState state = world.getBlockState(blockPos);
        return state.getBlock().isFlammable(
                world, blockPos, EnumFacing.UP);
    }

    @Override
    public void setFire(FluidPos pos) {
        if (isLoaded(pos) && world.isAirBlock(toBlockPos(pos))) {
            world.setBlockState(toBlockPos(pos), Blocks.FIRE.getDefaultState(), 3);
        }
    }

    @Override
    public Random getRandom() {
        return world.rand;
    }

    @Override
    public void playSound(FluidSound sound, double x, double y, double z,
                          float volume, float pitch) {
        if (world.isRemote) {
            net.minecraft.util.SoundEvent event;
            switch (sound) {
                case WATER_AMBIENT:
                    event = SoundEvents.BLOCK_WATER_AMBIENT;
                    break;
                case LAVA_AMBIENT:
                    event = SoundEvents.BLOCK_LAVA_AMBIENT;
                    break;
                case LAVA_POP:
                    event = SoundEvents.BLOCK_LAVA_POP;
                    break;
                case FLUID_FIZZ:
                    event = SoundEvents.BLOCK_LAVA_EXTINGUISH;
                    break;
                case BUCKET_FILL_WATER:
                    event = SoundEvents.ITEM_BUCKET_FILL;
                    break;
                case BUCKET_FILL_LAVA:
                    event = SoundEvents.ITEM_BUCKET_FILL_LAVA;
                    break;
                default:
                    return;
            }
            world.playSound(x, y, z, event, SoundCategory.BLOCKS,
                    volume, pitch, false);
        }
    }

    @Override
    public void spawnParticle(FluidParticle particle, double x, double y, double z,
                              double velocityX, double velocityY, double velocityZ) {
        if (!world.isRemote || particle == null) {
            return;
        }
        EnumParticleTypes type;
        switch (particle) {
            case UNDERWATER:
                type = EnumParticleTypes.SUSPENDED;
                break;
            case LAVA:
                type = EnumParticleTypes.LAVA;
                break;
            case DRIPPING_WATER:
                type = EnumParticleTypes.DRIP_WATER;
                break;
            case DRIPPING_LAVA:
                type = EnumParticleTypes.DRIP_LAVA;
                break;
            default:
                return;
        }
        world.spawnParticle(type, x, y, z, velocityX, velocityY, velocityZ);
    }

    @Override
    public void levelEvent(int eventId, FluidPos pos, int data) {
        if (isLoaded(pos)) {
            world.playEvent(eventId, toBlockPos(pos), data);
        }
    }

    private void storeWaterlogged(BlockPos pos, IBlockState host,
                                  FluidState fluid) {
        if (world.isRemote) {
            return;
        }

        if (host.getBlock() instanceof IFluidWaterloggable) {
            IFluidWaterloggable container = (IFluidWaterloggable) host.getBlock();
            if (!container.isWaterlogged(host)) {
                IBlockState wet = container.withWaterlogged(host, true);
                if (wet != null && !wet.equals(host)) {
                    world.setBlockState(pos, wet, 3);
                }
            }
        }

        FluidStateBridge stored = toBridge(fluid);
        ChunkFluidData data = ChunkFluidData.get(world);
        if (data != null) {
            if (data.putWater(pos, stored)) {
                FluidNetwork.syncState(world, pos, stored);
                FluidShapeCalculator.invalidate(world, pos);
                world.notifyNeighborsOfStateChange(pos, host.getBlock(), false);
            }
            FluidTickScheduler.schedule(world, pos,
                    fluid.getType().getTickDelay(this));
        }
    }

    private static IBlockState toLegacyState(FluidState state) {
        boolean water = state.getType().isSame(Fluids.WATER);
        int metadata = FluidLevelMapper.toMetadata(
                state.getAmount(), state.isFalling(), state.isSource());
        BlockLiquid block;
        if (water) {
            block = state.isSource()
                    ? (BlockLiquid) Blocks.WATER
                    : (BlockLiquid) Blocks.FLOWING_WATER;
        } else {
            block = state.isSource()
                    ? (BlockLiquid) Blocks.LAVA
                    : (BlockLiquid) Blocks.FLOWING_LAVA;
        }
        return block.getDefaultState().withProperty(
                BlockLiquid.LEVEL, Integer.valueOf(metadata));
    }

    private static Block flowingBlock(Fluid fluid) {
        if (fluid != null && fluid.isSame(Fluids.WATER)) {
            return Blocks.FLOWING_WATER;
        }
        return Blocks.FLOWING_LAVA;
    }

    private static FluidStateBridge toBridge(FluidState state) {
        FluidStateBridge.FluidKind kind = state.getType().isSame(Fluids.WATER)
                ? FluidStateBridge.FluidKind.WATER
                : FluidStateBridge.FluidKind.LAVA;
        return state.isSource()
                ? FluidStateBridge.source(kind)
                : FluidStateBridge.flowing(kind, state.getAmount(), state.isFalling());
    }

    private static Material toMaterial(Fluid fluid) {
        return fluid != null && fluid.isSame(Fluids.WATER)
                ? Material.WATER : Material.LAVA;
    }

    private static BlockPos toBlockPos(FluidPos pos) {
        return new BlockPos(pos.getX(), pos.getY(), pos.getZ());
    }

    private static EnumFacing toFacing(FluidDirection direction) {
        switch (direction) {
            case DOWN: return EnumFacing.DOWN;
            case UP: return EnumFacing.UP;
            case NORTH: return EnumFacing.NORTH;
            case SOUTH: return EnumFacing.SOUTH;
            case WEST: return EnumFacing.WEST;
            case EAST: return EnumFacing.EAST;
            default: throw new IllegalArgumentException("Unknown direction: " + direction);
        }
    }
}
