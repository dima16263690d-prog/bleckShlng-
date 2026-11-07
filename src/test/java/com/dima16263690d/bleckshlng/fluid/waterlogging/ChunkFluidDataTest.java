package com.dima16263690d.bleckshlng.fluid.waterlogging;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import org.junit.Test;

import java.util.List;

import com.dima16263690d.bleckshlng.fluid.state.FluidStateBridge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * NBT regression tests for the dimension-persistent waterlogging sidecar.
 * Does not require a live World or load any chunks.
 */
public class ChunkFluidDataTest {
    @Test
    public void waterStatesSurviveWorldSavedDataNbtRoundTrip() {
        ChunkFluidData saved = new ChunkFluidData("bleckshlng_test");
        BlockPos sourcePos = new BlockPos(17, 64, -1);
        BlockPos flowingPos = new BlockPos(-33, 70, 48);

        assertTrue(saved.putWater(sourcePos,
                FluidStateBridge.source(FluidStateBridge.FluidKind.WATER)));
        assertTrue(saved.putWater(flowingPos,
                FluidStateBridge.flowing(FluidStateBridge.FluidKind.WATER, 5, false)));

        NBTTagCompound nbt = saved.writeToNBT(new NBTTagCompound());
        ChunkFluidData loaded = new ChunkFluidData("bleckshlng_test");
        loaded.readFromNBT(nbt);

        assertEquals(FluidStateBridge.source(FluidStateBridge.FluidKind.WATER),
                loaded.getWater(sourcePos));
        assertEquals(FluidStateBridge.flowing(FluidStateBridge.FluidKind.WATER, 5, false),
                loaded.getWater(flowingPos));
    }

    @Test
    public void chunkIndexIsRebuiltAfterNbtLoad() {
        ChunkFluidData saved = new ChunkFluidData("bleckshlng_test");
        BlockPos first = new BlockPos(17, 64, -1);     // chunk (1, -1)
        BlockPos second = new BlockPos(-33, 70, 48);  // chunk (-3, 3)
        saved.putWater(first, FluidStateBridge.source(FluidStateBridge.FluidKind.WATER));
        saved.putWater(second, FluidStateBridge.source(FluidStateBridge.FluidKind.WATER));

        ChunkFluidData loaded = new ChunkFluidData("bleckshlng_test");
        loaded.readFromNBT(saved.writeToNBT(new NBTTagCompound()));

        List<BlockPos> firstChunk = loaded.getPositionsInChunk(1, -1);
        List<BlockPos> secondChunk = loaded.getPositionsInChunk(-3, 3);
        assertEquals(1, firstChunk.size());
        assertEquals(first, firstChunk.get(0));
        assertEquals(1, secondChunk.size());
        assertEquals(second, secondChunk.get(0));
    }

    @Test
    public void removingStateRemovesItFromDataAndChunkIndex() {
        ChunkFluidData data = new ChunkFluidData("bleckshlng_test");
        BlockPos pos = new BlockPos(4, 64, 4);
        data.putWater(pos, FluidStateBridge.source(FluidStateBridge.FluidKind.WATER));

        assertTrue(data.remove(pos));
        assertNull(data.getWater(pos));
        assertTrue(data.getPositionsInChunk(0, 0).isEmpty());
    }

    @Test
    public void legacyNbtWithoutSourceFlagLoadsAsFlowing() {
        NBTTagCompound root = new NBTTagCompound();
        net.minecraft.nbt.NBTTagList entries = new net.minecraft.nbt.NBTTagList();
        NBTTagCompound entry = new NBTTagCompound();
        BlockPos pos = new BlockPos(1, 2, 3);
        entry.setLong("pos", pos.toLong());
        entry.setInteger("amount", 4);
        entry.setBoolean("falling", false);
        entries.appendTag(entry);
        root.setTag("water", entries);

        ChunkFluidData loaded = new ChunkFluidData("bleckshlng_test");
        loaded.readFromNBT(root);
        FluidStateBridge state = loaded.getWater(pos);

        assertNotNull(state);
        assertEquals(4, state.getAmount());
        assertEquals(FluidStateBridge.FluidKind.WATER, state.getKind());
        assertTrue(loaded.getPositionsInChunk(0, 0).contains(pos));
    }
}
