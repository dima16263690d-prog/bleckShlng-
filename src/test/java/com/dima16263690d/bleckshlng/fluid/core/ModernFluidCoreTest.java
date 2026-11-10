package com.dima16263690d.bleckshlng.fluid.core;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Tests portable fluid rules without launching a Minecraft world. */
public class ModernFluidCoreTest {
    @Test
    public void sourceAndFlowingStatesKeepModernAmountAndHeight() {
        FluidState source = Fluids.waterSource();
        FluidState falling = Fluids.WATER.flowingState(8, true);
        FluidState levelFour = Fluids.WATER.flowingState(4, false);

        assertTrue(source.isSource());
        assertEquals(8, source.getAmount());
        assertEquals(8.0F / 9.0F, source.getOwnHeight(), 0.000001F);

        assertFalse(falling.isSource());
        assertTrue(falling.isFalling());
        assertEquals(8, falling.getAmount());
        assertEquals(4, levelFour.getAmount());
        assertEquals(4.0F / 9.0F, levelFour.getOwnHeight(), 0.000001F);
    }

    @Test
    public void fluidHeightReachesOneOnlyWhenSameFluidIsAbove() {
        TestWorld world = new TestWorld();
        FluidPos pos = new FluidPos(0, 10, 0);
        world.fluids.put(pos, Fluids.waterSource());

        assertEquals(8.0F / 9.0F,
                Fluids.WATER.sourceState().getHeight(world, pos), 0.000001F);

        world.fluids.put(pos.above(), Fluids.WATER.flowingState(3, false));
        assertEquals(1.0F,
                Fluids.WATER.sourceState().getHeight(world, pos), 0.000001F);
    }

    @Test
    public void waterFlowVectorUsesNeighborSurfaceDifference() {
        TestWorld world = new TestWorld();
        FluidPos pos = new FluidPos(0, 10, 0);
        world.fluids.put(pos, Fluids.waterSource());
        world.fluids.put(pos.offset(FluidDirection.EAST),
                Fluids.WATER.flowingState(4, false));

        FluidVector flow = Fluids.WATER.getFlow(
                world, pos, world.getFluidState(pos));

        assertTrue("flow should point toward the lower eastern surface",
                flow.getX() > 0.99D);
        assertEquals(0.0D, flow.getY(), 0.000001D);
        assertEquals(0.0D, flow.getZ(), 0.000001D);
    }

    @Test
    public void fallingWaterSpreadsDownwardAsFallingFluid() {
        TestWorld world = new TestWorld();
        FluidPos sourcePos = new FluidPos(0, 10, 0);
        FluidPos belowPos = sourcePos.below();
        FluidState source = Fluids.waterSource();
        world.fluids.put(sourcePos, source);

        Fluids.WATER.tick(world, sourcePos, source);

        FluidState below = world.getFluidState(belowPos);
        assertFalse("downward flow must be stored in the destination", below.isEmpty());
        assertTrue(below.isFalling());
        assertEquals(8, below.getAmount());
    }

    @Test
    public void spreadSearchDoesNotChooseAnOccupiedSourceAsItsOnlyRoute() {
        TestWorld world = new TestWorld();
        FluidPos origin = new FluidPos(0, 10, 0);
        FluidState source = Fluids.waterSource();

        // The source's downward exit is blocked. The east cell is already
        // a source, but its lower cell is an open drop. The other three
        // horizontal cells have no immediate drop and must be considered
        // as the next-best routes.
        world.fluids.put(origin, source);
        world.fluids.put(origin.offset(FluidDirection.EAST), source);
        world.solid.add(origin.below());
        world.solid.add(origin.offset(FluidDirection.NORTH).below());
        world.solid.add(origin.offset(FluidDirection.SOUTH).below());
        world.solid.add(origin.offset(FluidDirection.WEST).below());

        Fluids.WATER.tick(world, origin, source);

        assertTrue("existing source must not be overwritten",
                world.getFluidState(origin.offset(FluidDirection.EAST)).isSource());
        assertFalse("north route should receive flowing water",
                world.getFluidState(origin.offset(FluidDirection.NORTH)).isEmpty());
        assertFalse("south route should receive flowing water",
                world.getFluidState(origin.offset(FluidDirection.SOUTH)).isEmpty());
        assertFalse("west route should receive flowing water",
                world.getFluidState(origin.offset(FluidDirection.WEST)).isEmpty());
    }

    @Test
    public void sourceWaterSpreadsAcrossAllOpenSidesWhenSlabSupportBlocksDownwardFlow() {
        TestWorld world = new TestWorld();
        FluidPos sourcePos = new FluidPos(0, 10, 0);
        FluidState source = Fluids.waterSource();

        world.fluids.put(sourcePos, source);
        world.solid.add(sourcePos.below());
        for (FluidDirection direction : FluidDirection.HORIZONTAL) {
            world.solid.add(sourcePos.offset(direction).below());
        }

        Fluids.WATER.tick(world, sourcePos, source);

        for (FluidDirection direction : FluidDirection.HORIZONTAL) {
            FluidState side = world.getFluidState(sourcePos.offset(direction));
            assertFalse("water must flow onto the open side " + direction, side.isEmpty());
            assertFalse("side flow must not become a source", side.isSource());
            assertEquals("source flow drops by one level", 7, side.getAmount());
        }
    }

    @Test
    public void twoWaterSourcesCreateSourceAboveSolidSupport() {
        TestWorld world = new TestWorld();
        FluidPos target = new FluidPos(0, 10, 0);
        world.fluids.put(target.offset(FluidDirection.NORTH), Fluids.waterSource());
        world.fluids.put(target.offset(FluidDirection.SOUTH), Fluids.waterSource());
        world.solid.add(target.below());

        FluidState result = Fluids.WATER.getNewLiquid(world, target);

        assertTrue(result.isSource());
        assertEquals(8, result.getAmount());
    }

    @Test
    public void waterOnlyAllowsDifferentFluidToReplaceFromAbove() {
        TestWorld world = new TestWorld();
        FluidPos pos = new FluidPos(0, 10, 0);
        FluidState water = Fluids.waterSource();

        assertFalse(Fluids.WATER.canBeReplacedWith(
                water, world, pos, Fluids.LAVA, FluidDirection.EAST));
        assertTrue(Fluids.WATER.canBeReplacedWith(
                water, world, pos, Fluids.LAVA, FluidDirection.DOWN));
    }

    @Test
    public void lavaReplacementUsesModernFourNinthsHeightThreshold() {
        TestWorld world = new TestWorld();
        FluidPos pos = new FluidPos(0, 10, 0);
        FluidState levelFour = Fluids.LAVA.flowingState(4, false);
        FluidState levelThree = Fluids.LAVA.flowingState(3, false);

        assertTrue(Fluids.LAVA.canBeReplacedWith(
                levelFour, world, pos, Fluids.WATER, FluidDirection.EAST));
        assertFalse(Fluids.LAVA.canBeReplacedWith(
                levelThree, world, pos, Fluids.WATER, FluidDirection.DOWN));
    }

    @Test
    public void waterAndLavaKeepModernTickAndSlopeRules() {
        TestWorld overworld = new TestWorld();
        TestWorld ultrawarm = new TestWorld();
        ultrawarm.ultraWarm = true;

        assertEquals(5, Fluids.WATER.getTickDelay(overworld));
        assertEquals(4, Fluids.WATER.getSlopeFindDistance(overworld));
        assertEquals(30, Fluids.LAVA.getTickDelay(overworld));
        assertEquals(2, Fluids.LAVA.getSlopeFindDistance(overworld));
        assertEquals(10, Fluids.LAVA.getTickDelay(ultrawarm));
        assertEquals(4, Fluids.LAVA.getSlopeFindDistance(ultrawarm));
    }

    /**
     * Test-only access to protected FlowingFluid values through Fluid behavior.
     */
    private static final class TestWorld implements FluidWorld {
        private final Map<FluidPos, FluidState> fluids =
                new HashMap<FluidPos, FluidState>();
        private final Set<FluidPos> solid = new HashSet<FluidPos>();
        private final Random random = new Random(1L);
        private boolean wallOpen = true;
        private boolean ultraWarm;

        @Override public boolean isLoaded(FluidPos pos) { return true; }
        @Override public FluidState getFluidState(FluidPos pos) {
            FluidState state = fluids.get(pos);
            return state == null ? FluidState.EMPTY : state;
        }
        @Override public boolean blocksMotion(FluidPos pos) { return solid.contains(pos); }
        @Override public boolean isSolidMaterial(FluidPos pos) { return solid.contains(pos); }
        @Override public boolean isAir(FluidPos pos) { return !solid.contains(pos) && getFluidState(pos).isEmpty(); }
        @Override public boolean isSolidRender(FluidPos pos) { return solid.contains(pos); }
        @Override public boolean isIce(FluidPos pos) { return false; }
        @Override public boolean isFaceSturdy(FluidPos pos, FluidDirection face) { return solid.contains(pos); }
        @Override public boolean canPassThroughWall(FluidPos from, FluidDirection direction) { return wallOpen; }
        @Override public boolean canHoldFluid(FluidPos target, Fluid fluid) { return !solid.contains(target); }
        @Override public boolean canReplaceFluid(FluidPos target, Fluid incoming, FluidDirection direction) {
            FluidState current = getFluidState(target);
            return current.isEmpty() || current.getType().canBeReplacedWith(
                    current, this, target, incoming, direction);
        }
        @Override public boolean canCreateFluidSource(FluidPos target, Fluid fluid) { return true; }
        @Override public void setFluidState(FluidPos pos, FluidState state) { fluids.put(pos, state); }
        @Override public void removeFluid(FluidPos pos) { fluids.remove(pos); }
        @Override public void spreadFluidTo(FluidPos target, FluidState state, FluidDirection direction) {
            fluids.put(target, state);
        }
        @Override public void scheduleFluidTick(FluidPos pos, Fluid fluid, int delay) { }
        @Override public void notifyFluidNeighbors(FluidPos pos, Fluid fluid) { }
        @Override public boolean isUltraWarm() { return ultraWarm; }
        @Override public boolean isFireTickEnabled() { return true; }
        @Override public boolean hasFlammableNeighbor(FluidPos pos) { return false; }
        @Override public boolean isFlammable(FluidPos pos) { return false; }
        @Override public void setFire(FluidPos pos) { }
        @Override public Random getRandom() { return random; }
        @Override public void playSound(FluidSound sound, double x, double y, double z,
                                        float volume, float pitch) { }
        @Override public void spawnParticle(FluidParticle particle, double x, double y, double z,
                                            double velocityX, double velocityY, double velocityZ) { }
        @Override public void levelEvent(int eventId, FluidPos pos, int data) { }
    }
}
