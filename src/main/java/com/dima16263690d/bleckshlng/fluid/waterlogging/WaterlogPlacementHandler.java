package com.dima16263690d.bleckshlng.fluid.waterlogging;

import net.minecraft.block.BlockDynamicLiquid;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import com.dima16263690d.bleckshlng.BleckShlng;
import com.dima16263690d.bleckshlng.fluid.geometry.WaterloggedBlockRegistry;
import com.dima16263690d.bleckshlng.fluid.state.FluidStateBridge;
import com.dima16263690d.bleckshlng.fluid.state.FluidStateMapper;

/**
 * Forge-side safety net for Update Aquatic placement rules.
 *
 * Coremod World.setBlockState hooks already capture water replaced by a
 * waterloggable host. This handler covers the cases those hooks miss:
 * placement beside existing water, static ocean sources that do not re-tick
 * promptly, and environments where the coremod transform is unavailable.
 */
@Mod.EventBusSubscriber(modid = BleckShlng.MODID)
public final class WaterlogPlacementHandler {
    private WaterlogPlacementHandler() {
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onBlockPlace(BlockEvent.PlaceEvent event) {
        World world = event.getWorld();
        BlockPos pos = event.getPos();
        if (world == null || world.isRemote || pos == null || !world.isBlockLoaded(pos)) {
            return;
        }

        IBlockState placed = world.getBlockState(pos);
        if (placed == null || !WaterloggedBlockRegistry.canWaterlog(placed.getBlock())) {
            return;
        }

        // Already holding water (coremod path or prior fill).
        FluidStateBridge existing = FluidStateMapper.fromWorld(world, pos, placed);
        if (existing != null && existing.getKind() == FluidStateBridge.FluidKind.WATER) {
            scheduleNeighborFluidUpdates(world, pos);
            return;
        }

        if (hasAdjacentWaterSource(world, pos) || isSurroundedByWater(world, pos)) {
            if (WaterloggedFluidHandler.tryWaterlog(world, pos, placed, 0)) {
                scheduleNeighborFluidUpdates(world, pos);
                return;
            }
        }

        // Even without immediate waterlog, force neighboring liquid blocks to
        // convert from static sources and attempt a horizontal fill.
        scheduleNeighborFluidUpdates(world, pos);
    }

    private static boolean hasAdjacentWaterSource(World world, BlockPos pos) {
        for (EnumFacing facing : EnumFacing.VALUES) {
            BlockPos neighbor = pos.offset(facing);
            if (!world.isBlockLoaded(neighbor)) {
                continue;
            }
            IBlockState state = world.getBlockState(neighbor);
            if (state.getMaterial() == Material.WATER
                    && state.getBlock() instanceof BlockLiquid) {
                return true;
            }
            FluidStateBridge fluid = FluidStateMapper.fromWorld(world, neighbor, state);
            if (fluid != null && fluid.getKind() == FluidStateBridge.FluidKind.WATER) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSurroundedByWater(World world, BlockPos pos) {
        // Ocean / pool cutout: several horizontal neighbors are water.
        int waterSides = 0;
        for (EnumFacing facing : EnumFacing.Plane.HORIZONTAL) {
            BlockPos neighbor = pos.offset(facing);
            if (!world.isBlockLoaded(neighbor)) {
                continue;
            }
            if (world.getBlockState(neighbor).getMaterial() == Material.WATER) {
                waterSides++;
            }
        }
        return waterSides >= 2;
    }

    private static void scheduleNeighborFluidUpdates(World world, BlockPos pos) {
        for (EnumFacing facing : EnumFacing.VALUES) {
            BlockPos neighbor = pos.offset(facing);
            if (!world.isBlockLoaded(neighbor)) {
                continue;
            }
            IBlockState state = world.getBlockState(neighbor);
            if (state.getMaterial() != Material.WATER
                    && state.getMaterial() != Material.LAVA) {
                continue;
            }
            if (!(state.getBlock() instanceof BlockLiquid)) {
                continue;
            }

            // Convert static liquid to flowing so the spread engine runs.
            if (!(state.getBlock() instanceof BlockDynamicLiquid)) {
                BlockDynamicLiquid flowing = state.getMaterial() == Material.WATER
                        ? Blocks.FLOWING_WATER
                        : Blocks.FLOWING_LAVA;
                int level = state.getValue(BlockLiquid.LEVEL).intValue();
                world.setBlockState(neighbor, flowing.getDefaultState()
                        .withProperty(BlockLiquid.LEVEL, Integer.valueOf(level)), 2);
                state = world.getBlockState(neighbor);
            }
            world.scheduleUpdate(neighbor, state.getBlock(), 1);
        }
    }
}
