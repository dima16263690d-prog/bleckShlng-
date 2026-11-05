package com.dima16263690d.bleckshlng.interaction;

import com.dima16263690d.bleckshlng.block.BlockStrippedWood;
import com.dima16263690d.bleckshlng.registry.ModBlocks;
import net.minecraft.block.BlockLog;
import net.minecraft.block.BlockNewLog;
import net.minecraft.block.BlockOldLog;
import net.minecraft.block.BlockPlanks;
import net.minecraft.block.BlockRotatedPillar;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;

/**
 * Port of the vanilla 1.13+ AxeItem STRIPPABLES mappings to the
 * BlockOldLog/BlockNewLog metadata layout used by Minecraft 1.12.2.
 */
public final class StrippingRegistry {
    private StrippingRegistry() {
    }

    public static IBlockState getStrippedState(IBlockState sourceState) {
        if (sourceState == null) {
            return null;
        }

        BlockStrippedWood strippedLog;
        BlockStrippedWood strippedWood;
        BlockLog.EnumAxis sourceAxis;

        if (sourceState.getBlock() == Blocks.LOG) {
            BlockPlanks.EnumType type = sourceState.getValue(BlockOldLog.VARIANT);
            sourceAxis = sourceState.getValue(BlockLog.LOG_AXIS);

            switch (type) {
                case OAK:
                    strippedLog = ModBlocks.STRIPPED_OAK_LOG;
                    strippedWood = ModBlocks.STRIPPED_OAK_WOOD;
                    break;
                case SPRUCE:
                    strippedLog = ModBlocks.STRIPPED_SPRUCE_LOG;
                    strippedWood = ModBlocks.STRIPPED_SPRUCE_WOOD;
                    break;
                case BIRCH:
                    strippedLog = ModBlocks.STRIPPED_BIRCH_LOG;
                    strippedWood = ModBlocks.STRIPPED_BIRCH_WOOD;
                    break;
                case JUNGLE:
                    strippedLog = ModBlocks.STRIPPED_JUNGLE_LOG;
                    strippedWood = ModBlocks.STRIPPED_JUNGLE_WOOD;
                    break;
                default:
                    return null;
            }
        } else if (sourceState.getBlock() == Blocks.LOG2) {
            BlockPlanks.EnumType type = sourceState.getValue(BlockNewLog.VARIANT);
            sourceAxis = sourceState.getValue(BlockLog.LOG_AXIS);

            switch (type) {
                case ACACIA:
                    strippedLog = ModBlocks.STRIPPED_ACACIA_LOG;
                    strippedWood = ModBlocks.STRIPPED_ACACIA_WOOD;
                    break;
                case DARK_OAK:
                    strippedLog = ModBlocks.STRIPPED_DARK_OAK_LOG;
                    strippedWood = ModBlocks.STRIPPED_DARK_OAK_WOOD;
                    break;
                default:
                    return null;
            }
        } else {
            return null;
        }

        // The old LOG_AXIS=NONE state is the 1.12 representation of
        // all-sides-barked wood. Convert it to the matching stripped wood.
        if (sourceAxis == BlockLog.EnumAxis.NONE) {
            return strippedWood.getDefaultState()
                    .withProperty(BlockRotatedPillar.AXIS, EnumFacing.Axis.Y);
        }

        return strippedLog.getDefaultState().withProperty(
                BlockRotatedPillar.AXIS, toFacingAxis(sourceAxis));
    }

    private static EnumFacing.Axis toFacingAxis(BlockLog.EnumAxis axis) {
        switch (axis) {
            case X:
                return EnumFacing.Axis.X;
            case Z:
                return EnumFacing.Axis.Z;
            case Y:
            case NONE:
            default:
                return EnumFacing.Axis.Y;
        }
    }
}
