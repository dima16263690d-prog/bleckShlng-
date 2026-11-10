package com.dima16263690d.bleckshlng.client;

import java.util.List;
import java.util.Random;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.dima16263690d.bleckshlng.BleckShlng;
import com.dima16263690d.bleckshlng.fluid.geometry.FluidShapeCalculator;
import com.dima16263690d.bleckshlng.fluid.geometry.FluidImpermeableRegistry;
import com.dima16263690d.bleckshlng.fluid.geometry.WaterloggedBlockRegistry;
import com.dima16263690d.bleckshlng.fluid.network.ClientFluidStateCache;
import com.dima16263690d.bleckshlng.fluid.state.FluidStateBridge;
import com.dima16263690d.bleckshlng.fluid.state.FluidStateMapper;

/**
 * Client-side FluidState.animateTick bridge for water stored outside the
 * vanilla BlockLiquid state (the persistent waterlogged sidecar).
 *
 * The sample distribution mirrors ClientLevel.animateTick in 1.18.2:
 * 667 samples in radius 16 and 667 samples in radius 32 per client tick.
 * This avoids updating every waterlogged block every tick and keeps particle
 * density independent of the rendered frame rate.
 */
@SideOnly(Side.CLIENT)
@Mod.EventBusSubscriber(modid = BleckShlng.MODID, value = Side.CLIENT)
public final class WaterloggedFluidAnimateHandler {
    private static final int SAMPLES_PER_RADIUS = 667;

    private WaterloggedFluidAnimateHandler() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        net.minecraft.client.Minecraft minecraft =
                net.minecraft.client.Minecraft.getMinecraft();
        World world = minecraft.world;
        net.minecraft.entity.Entity viewer = minecraft.getRenderViewEntity();
        if (world == null || !world.isRemote || viewer == null) {
            return;
        }

        int centerX = floor(viewer.posX);
        int centerY = floor(viewer.posY);
        int centerZ = floor(viewer.posZ);
        int dimension = world.provider.getDimension();
        if (!ClientFluidStateCache.hasAnyInChunkRange(
                dimension, centerX >> 4, centerZ >> 4, 2)) {
            return;
        }
        Random positionRandom = world.rand;
        Random effectRandom = new Random();

        // Level.random selects sample positions and drip chances. ClientLevel
        // passes a separate Random to block/fluid animateTick().
        sampleRadius(world, centerX, centerY, centerZ, 16,
                positionRandom, effectRandom);
        sampleRadius(world, centerX, centerY, centerZ, 32,
                positionRandom, effectRandom);
    }

    private static void sampleRadius(World world, int centerX, int centerY,
                                     int centerZ, int radius,
                                     Random positionRandom, Random effectRandom) {
        for (int i = 0; i < SAMPLES_PER_RADIUS; i++) {
            // Match Level/ClientLevel's triangular random coordinate spread.
            int x = centerX + positionRandom.nextInt(radius) - positionRandom.nextInt(radius);
            int y = centerY + positionRandom.nextInt(radius) - positionRandom.nextInt(radius);
            int z = centerZ + positionRandom.nextInt(radius) - positionRandom.nextInt(radius);
            BlockPos pos = new BlockPos(x, y, z);
            if (!world.isBlockLoaded(pos)) {
                continue;
            }

            IBlockState host = world.getBlockState(pos);
            if (host == null || host.getMaterial().isLiquid()
                    || !WaterloggedBlockRegistry.canWaterlog(host.getBlock())) {
                continue;
            }

            // FluidStateMapper reads both the synchronized sidecar and native
            // IFluidWaterloggable adapters, matching Level#getFluidState.
            FluidStateBridge fluid = FluidStateMapper.fromWorld(world, pos, host);
            if (fluid == null
                    || fluid.getKind() != FluidStateBridge.FluidKind.WATER) {
                continue;
            }

            animateWater(world, pos, fluid, effectRandom);

            // ClientLevel.doAnimateTick also asks FluidState#getDripParticle
            // once per selected position, independently of animateTick().
            if (positionRandom.nextInt(10) == 0) {
                trySpawnDrippingFluid(world, pos);
            }
        }
    }

    /**
     * WaterFluid.animateTick from 1.18.2, mapped to the closest 1.12.2
     * particle and sound registrations.
     */
    private static void animateWater(World world, BlockPos pos,
                                     FluidStateBridge fluid, Random random) {
        // Both native and waterlogged liquids now use the same portable
        // Fluid#animateTick implementation through the 1.12.2 API adapter.
        com.dima16263690d.bleckshlng.fluid.core.ModernFluidEngine
                .animateTick(world, pos, random);
    }

    /**
     * Ports ClientLevel.trySpawnDripParticles for WaterFluid's
     * DRIPPING_WATER particle. 1.12.2 exposes the matching DRIP_WATER particle.
     */
    public static void trySpawnDrippingFluid(World world, BlockPos fluidPos) {
        if (world == null || !world.isRemote || fluidPos == null
                || !world.isBlockLoaded(fluidPos)) {
            return;
        }
        IBlockState fluidHost = world.getBlockState(fluidPos);
        FluidStateBridge fluid = FluidStateMapper.fromWorld(world, fluidPos, fluidHost);
        if (fluid == null) {
            return;
        }
        EnumParticleTypes dripParticle = fluid.getKind() == FluidStateBridge.FluidKind.LAVA
                ? EnumParticleTypes.DRIP_LAVA : EnumParticleTypes.DRIP_WATER;
        boolean sturdyHostBottom =
                fluidHost.isSideSolid(world, fluidPos, EnumFacing.DOWN);

        BlockPos belowPos = fluidPos.down();
        if (!world.isBlockLoaded(belowPos)) {
            return;
        }

        IBlockState belowState = world.getBlockState(belowPos);
        if (hasFluid(world, belowPos, belowState)) {
            return;
        }

        ShapeBounds belowShape = getShapeBounds(world, belowPos, belowState);
        if (belowShape.maxY < 1.0D) {
            if (sturdyHostBottom) {
                spawnDrip(world, belowPos, 0.0D, 1.0D,
                        0.0D, 1.0D, 1.0D - 0.05D, dripParticle);
            }
            return;
        }

        // Compatibility bridge for the 1.18.2 #minecraft:impermeable tag.
        if (FluidImpermeableRegistry.isImpermeable(belowState, world, belowPos)) {
            return;
        }

        if (belowShape.minY > 0.0D) {
            spawnDrip(world, belowPos,
                    belowShape.minX, belowShape.maxX,
                    belowShape.minZ, belowShape.maxZ,
                    belowShape.minY - 0.05D, dripParticle);
            return;
        }

        BlockPos lowerPos = belowPos.down();
        if (!world.isBlockLoaded(lowerPos)) {
            return;
        }

        IBlockState lowerState = world.getBlockState(lowerPos);
        if (hasFluid(world, lowerPos, lowerState)) {
            return;
        }

        ShapeBounds lowerShape = getShapeBounds(world, lowerPos, lowerState);
        if (lowerShape.maxY < 1.0D) {
            spawnDrip(world, belowPos,
                    belowShape.minX, belowShape.maxX,
                    belowShape.minZ, belowShape.maxZ,
                    -0.05D, dripParticle);
        }
    }

    private static boolean hasFluid(World world, BlockPos pos, IBlockState state) {
        FluidStateBridge fluid = FluidStateMapper.fromWorld(world, pos, state);
        return fluid != null;
    }

    private static ShapeBounds getShapeBounds(World world, BlockPos pos,
                                              IBlockState state) {
        List<AxisAlignedBB> boxes =
                FluidShapeCalculator.getCollisionBoxes(world, pos, state);
        if (boxes == null || boxes.isEmpty()) {
            return ShapeBounds.EMPTY;
        }

        double minX = 1.0D;
        double minY = 1.0D;
        double minZ = 1.0D;
        double maxX = 0.0D;
        double maxY = 0.0D;
        double maxZ = 0.0D;

        for (AxisAlignedBB box : boxes) {
            minX = Math.min(minX, box.minX - pos.getX());
            minY = Math.min(minY, box.minY - pos.getY());
            minZ = Math.min(minZ, box.minZ - pos.getZ());
            maxX = Math.max(maxX, box.maxX - pos.getX());
            maxY = Math.max(maxY, box.maxY - pos.getY());
            maxZ = Math.max(maxZ, box.maxZ - pos.getZ());
        }

        return new ShapeBounds(clamp(minX), clamp(minY), clamp(minZ),
                clamp(maxX), maxY, clamp(maxZ));
    }

    private static void spawnDrip(World world, BlockPos pos,
                                  double minX, double maxX,
                                  double minZ, double maxZ, double yOffset,
                                  EnumParticleTypes particle) {
        double xRange = Math.max(0.0D, maxX - minX);
        double zRange = Math.max(0.0D, maxZ - minZ);
        double x = pos.getX() + minX + world.rand.nextDouble() * xRange;
        double z = pos.getZ() + minZ + world.rand.nextDouble() * zRange;
        double y = pos.getY() + yOffset;
        world.spawnParticle(particle, x, y, z, 0.0D, 0.0D, 0.0D);
    }

    private static int floor(double value) {
        int integer = (int) value;
        return value < (double) integer ? integer - 1 : integer;
    }

    private static double clamp(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }

    private static final class ShapeBounds {
        private static final ShapeBounds EMPTY =
                new ShapeBounds(0.0D, 0.0D, 0.0D, 1.0D, 0.0D, 1.0D);

        private final double minX;
        private final double minY;
        private final double minZ;
        private final double maxX;
        private final double maxY;
        private final double maxZ;

        private ShapeBounds(double minX, double minY, double minZ,
                            double maxX, double maxY, double maxZ) {
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
        }
    }
}
