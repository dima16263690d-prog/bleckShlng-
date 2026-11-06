package com.dima16263690d.bleckshlng.fluid.physics;

import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityBoat;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import com.dima16263690d.bleckshlng.fluid.geometry.FluidShapeCalculator;
import com.dima16263690d.bleckshlng.fluid.state.FluidHeightCalculator;
import com.dima16263690d.bleckshlng.fluid.state.FluidStateBridge;
import com.dima16263690d.bleckshlng.fluid.state.FluidStateMapper;

/**
 * Entity/fluid bridge for water stored inside legacy blocks.
 *
 * It extends the 1.13.2 "waterlogged counts as water" behavior to the
 * legacy 1.12.2 entity checks. The entity bounds are tested against the free
 * volume inside the host block, and the legacy water-current coefficient
 * (0.014) is applied from the stored source state.
 */
public final class FluidEntityHandler {
    private static final double WATER_PUSH = 0.014D;
    private static final double MIN_HORIZONTAL_MOTION = 0.003D;
    private static final double MIN_PUSH = 0.0045D;
    private static final double EPSILON = 0.001D;

    private FluidEntityHandler() {
    }

    public static boolean updateWaterloggedMovement(Entity entity) {
        if (entity == null || entity.world == null
                || entity.getRidingEntity() instanceof EntityBoat) {
            return false;
        }

        AxisAlignedBB bounds = entity.getEntityBoundingBox().shrink(EPSILON);
        int minX = MathHelper.floor(bounds.minX);
        int maxX = MathHelper.ceil(bounds.maxX);
        int minY = MathHelper.floor(bounds.minY);
        int maxY = MathHelper.ceil(bounds.maxY);
        int minZ = MathHelper.floor(bounds.minZ);
        int maxZ = MathHelper.ceil(bounds.maxZ);

        // Match the vanilla/modern rule: never infer fluid state from an
        // incomplete area touching an unloaded chunk.
        for (int x = minX; x < maxX; x++) {
            for (int y = minY; y < maxY; y++) {
                for (int z = minZ; z < maxZ; z++) {
                    if (!entity.world.isBlockLoaded(new BlockPos(x, y, z))) {
                        return false;
                    }
                }
            }
        }

        boolean touchingWater = false;
        double maxFluidDepth = 0.0D;
        Vec3d flow = Vec3d.ZERO;
        int flowSamples = 0;

        for (int x = minX; x < maxX; x++) {
            for (int y = minY; y < maxY; y++) {
                for (int z = minZ; z < maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    IBlockState blockState = entity.world.getBlockState(pos);
                    FluidStateBridge fluid = FluidStateMapper.fromWorld(
                            entity.world, pos, blockState);
                    if (fluid == null
                            || fluid.getKind() != FluidStateBridge.FluidKind.WATER) {
                        continue;
                    }

                    float height = FluidHeightCalculator.getHeight(
                            entity.world, pos, fluid);
                    double surfaceY = pos.getY() + (double) height;
                    if (surfaceY < bounds.minY
                            || !FluidShapeCalculator.intersectsFluidSpace(
                            entity.world, pos, blockState, bounds, height)) {
                        continue;
                    }

                    touchingWater = true;
                    maxFluidDepth = Math.max(maxFluidDepth, surfaceY - bounds.minY);

                    Vec3d localFlow = FluidFlowVector.calculate(
                            entity.world, pos, blockState);
                    if (maxFluidDepth < 0.4D) {
                        localFlow = localFlow.scale(maxFluidDepth);
                    }
                    flow = flow.add(localFlow);
                    flowSamples++;
                }
            }
        }

        if (!touchingWater) {
            return false;
        }

        entity.fallDistance = 0.0F;
        entity.extinguish();

        if (flow.lengthVector() > 0.0D) {
            if (flowSamples > 0) {
                flow = flow.scale(1.0D / (double) flowSamples);
            }
            if (!(entity instanceof EntityPlayer)) {
                flow = flow.normalize();
            }
            flow = flow.scale(WATER_PUSH);

            if (Math.abs(entity.motionX) < MIN_HORIZONTAL_MOTION
                    && Math.abs(entity.motionZ) < MIN_HORIZONTAL_MOTION
                    && flow.lengthVector() < MIN_PUSH) {
                flow = flow.normalize().scale(MIN_PUSH);
            }

            entity.motionX += flow.x;
            entity.motionY += flow.y;
            entity.motionZ += flow.z;
        }

        return true;
    }

    public static boolean isInWaterloggedFluid(Entity entity) {
        if (entity == null || entity.world == null
                || entity.getRidingEntity() instanceof EntityBoat) {
            return false;
        }

        AxisAlignedBB bounds = entity.getEntityBoundingBox().shrink(EPSILON);
        int minX = MathHelper.floor(bounds.minX);
        int maxX = MathHelper.ceil(bounds.maxX);
        int minY = MathHelper.floor(bounds.minY);
        int maxY = MathHelper.ceil(bounds.maxY);
        int minZ = MathHelper.floor(bounds.minZ);
        int maxZ = MathHelper.ceil(bounds.maxZ);

        for (int x = minX; x < maxX; x++) {
            for (int y = minY; y < maxY; y++) {
                for (int z = minZ; z < maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!entity.world.isBlockLoaded(pos)) {
                        continue;
                    }
                    IBlockState blockState = entity.world.getBlockState(pos);
                    FluidStateBridge fluid = FluidStateMapper.fromWorld(
                            entity.world, pos, blockState);
                    if (fluid == null
                            || fluid.getKind() != FluidStateBridge.FluidKind.WATER) {
                        continue;
                    }
                    float height = FluidHeightCalculator.getHeight(
                            entity.world, pos, fluid);
                    if (FluidShapeCalculator.intersectsFluidSpace(
                            entity.world, pos, blockState, bounds, height)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static boolean isEyeInWaterloggedFluid(Entity entity) {
        if (entity == null || entity.world == null) {
            return false;
        }

        double eyeY = entity.posY + (double) entity.getEyeHeight() - 0.11111111D;
        BlockPos pos = new BlockPos(entity.posX, eyeY, entity.posZ);
        if (!entity.world.isBlockLoaded(pos)) {
            return false;
        }

        IBlockState state = entity.world.getBlockState(pos);
        FluidStateBridge fluid = FluidStateMapper.fromWorld(entity.world, pos, state);
        if (fluid == null || fluid.getKind() != FluidStateBridge.FluidKind.WATER) {
            return false;
        }

        float height = FluidHeightCalculator.getHeight(entity.world, pos, fluid);
        return FluidShapeCalculator.containsFluidPoint(entity.world, pos,
                state, entity.posX, eyeY, entity.posZ, height);
    }
}
