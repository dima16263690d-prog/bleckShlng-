package com.dima16263690d.bleckshlng.client;

import java.util.List;
import java.util.Random;

import net.minecraft.block.state.IBlockState;
import net.minecraft.block.material.Material;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;

import com.dima16263690d.bleckshlng.BleckShlng;
import com.dima16263690d.bleckshlng.fluid.geometry.FluidShapeCalculator;
import com.dima16263690d.bleckshlng.fluid.geometry.WaterloggedBlockRegistry;
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
        Random fluidRandom = new Random();

        // ClientLevel.animateTick(1.18.2) samples both radii on each tick.
        sampleRadius(world, dimension, centerX, centerY, centerZ,
                16, fluidRandom);
        sampleRadius(world, dimension, centerX, centerY, centerZ,
                32, fluidRandom);
    }

    private static void sampleRadius(World world, int dimension,
                                     int centerX, int centerY, int centerZ,
                                     int radius, Random fluidRandom) {
        for (int i = 0; i < SAMPLES_PER_RADIUS; i++) {
            // Match Level/ClientLevel's triangular random coordinate spread.
            int x = centerX + world.rand.nextInt(radius) - world.rand.nextInt(radius);
            int y = centerY + world.rand.nextInt(radius) - world.rand.nextInt(radius);
            int z = centerZ + world.rand.nextInt(radius) - world.rand.nextInt(radius);
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

            animateWater(world, pos, fluid, fluidRandom);

            // ClientLevel.doAnimateTick also asks FluidState#getDripParticle
            // once per selected position, independently of animateTick().
            if (world.rand.nextInt(10) == 0) {
                trySpawnDrippingWater(world, pos, host);
            }
        }
    }

    /**
     * WaterFluid.animateTick from 1.18.2, mapped to the closest 1.12.2
     * particle and sound registrations.
     */
    private static void animateWater(World world, BlockPos pos,
                                      FluidStateBridge fluid, Random random) {
        double x = (double) pos.getX();
        double y = (double) pos.getY();
        double z = (double) pos.getZ();

        if (!fluid.isSource() && !fluid.isFalling()) {
            if (random.nextInt(64) == 0) {
                world.playSound(x + 0.5D, y + 0.5D, z + 0.5D,
                        SoundEvents.BLOCK_WATER_AMBIENT, SoundCategory.BLOCKS,
                        random.nextFloat() * 0.25F + 0.75F,
                        random.nextFloat() + 0.5F, false);
            }
        } else if (random.nextInt(10) == 0) {
            // 1.12.2 SUSPENDED is the legacy equivalent of 1.18.2 UNDERWATER.
            world.spawnParticle(EnumParticleTypes.SUSPENDED,
                    x + random.nextDouble(),
                    y + random.nextDouble(),
                    z + random.nextDouble(),
                    0.0D, 0.0D, 0.0D);
        }
    }

    /**
     * Ports ClientLevel.trySpawnDripParticles for WaterFluid's
     * DRIPPING_WATER particle. 1.12.2 exposes the matching DRIP_WATER particle.
     */
    private static void trySpawnDrippingWater(World world, BlockPos fluidPos,
                                              IBlockState waterloggedHost) {
        boolean sturdyHostBottom =
                waterloggedHost.isSideSolid(world, fluidPos, EnumFacing.DOWN);

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
                        0.0D, 1.0D, 1.0D - 0.05D);
            }
            return;
        }

        // The 1.18.2 IMPERMEABLE tag has no complete 1.12.2 equivalent.
        // Preserve its important vanilla barrier/structure-void exclusions.
        if (belowState.getBlock() == Blocks.BARRIER
                || belowState.getMaterial() == Material.STRUCTURE_VOID) {
            return;
        }

        if (belowShape.minY > 0.0D) {
            spawnDrip(world, belowPos,
                    belowShape.minX, belowShape.maxX,
                    belowShape.minZ, belowShape.maxZ,
                    belowShape.minY - 0.05D);
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
                    -0.05D);
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
                                  double minZ, double maxZ, double yOffset) {
        double xRange = Math.max(0.0D, maxX - minX);
        double zRange = Math.max(0.0D, maxZ - minZ);
        double x = pos.getX() + minX + world.rand.nextDouble() * xRange;
        double z = pos.getZ() + minZ + world.rand.nextDouble() * zRange;
        double y = pos.getY() + yOffset;
        world.spawnParticle(EnumParticleTypes.DRIP_WATER,
                x, y, z, 0.0D, 0.0D, 0.0D);
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
