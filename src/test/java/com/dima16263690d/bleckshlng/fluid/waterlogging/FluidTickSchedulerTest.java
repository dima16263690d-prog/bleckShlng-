package com.dima16263690d.bleckshlng.fluid.waterlogging;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Tests chunk-unload/reload semantics without starting a Minecraft World. */
public class FluidTickSchedulerTest {
    @Test
    public void unloadDropsOnlyInMemoryTasksForThatChunk() {
        Map<Long, Long> pending = new HashMap<Long, Long>();
        BlockPos first = new BlockPos(17, 64, -1);  // chunk (1, -1)
        BlockPos other = new BlockPos(33, 64, 1);   // chunk (2, 0)
        FluidTickScheduler.scheduleAt(pending, first, 1200L);
        FluidTickScheduler.scheduleAt(pending, other, 1400L);

        FluidTickScheduler.removeChunkTasks(pending, 1, -1);

        assertFalse(pending.containsKey(Long.valueOf(first.toLong())));
        assertEquals(Long.valueOf(1400L), pending.get(Long.valueOf(other.toLong())));
    }

    @Test
    public void chunkReloadRestoresOriginalAndAlreadyOverdueDeadlines() {
        ChunkFluidData saved = new ChunkFluidData("bleckshlng_tick_test");
        BlockPos future = new BlockPos(17, 64, -1);
        BlockPos overdue = new BlockPos(18, 64, -1);
        saved.putScheduledTick(future, 1200L);
        saved.putScheduledTick(overdue, 800L);

        NBTTagCompound nbt = saved.writeToNBT(new NBTTagCompound());
        ChunkFluidData loaded = new ChunkFluidData("bleckshlng_tick_test");
        loaded.readFromNBT(nbt);

        Map<Long, Long> pending = new HashMap<Long, Long>();
        FluidTickScheduler.restoreChunkDeadlines(pending, loaded, 1, -1);

        assertEquals(Long.valueOf(1200L), pending.get(Long.valueOf(future.toLong())));
        assertEquals(Long.valueOf(800L), pending.get(Long.valueOf(overdue.toLong())));
        assertTrue(pending.get(Long.valueOf(overdue.toLong())).longValue() < 1000L);
    }

    @Test
    public void restoringDoesNotPostponeAnEarlierPendingDeadline() {
        Map<Long, Long> pending = new HashMap<Long, Long>();
        BlockPos pos = new BlockPos(1, 64, 1);
        FluidTickScheduler.scheduleAt(pending, pos, 50L);
        FluidTickScheduler.scheduleAt(pending, pos, 100L);
        assertEquals(Long.valueOf(50L), pending.get(Long.valueOf(pos.toLong())));
    }
}
