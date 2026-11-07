# Fluid runtime event integration

Target: Minecraft 1.12.2 / Forge 14.23.5.x / Java 8.

## Event buses

Forge 1.12.2 publishes TickEvent.WorldTickEvent through
FMLCommonHandler.instance().bus(). In contrast, @Mod.EventBusSubscriber
registers static handlers on MinecraftForge.EVENT_BUS.

The fluid scheduler receives both kinds of events:

- World/chunk/block lifecycle events remain on the Forge event bus through
  @Mod.EventBusSubscriber.
- World tick events are forwarded by FluidTickScheduler.FmlTickEventBridge,
  registered once during FMLPreInitializationEvent.

Without this dual registration, the project can compile and the chunk lifecycle
handlers can run, while scheduled waterlogging updates never execute.

## Runtime validation still required

A passing ForgeGradle build verifies compilation and bytecode unit tests; it
does not start a real server world. In-game validation must still check:

1. A source waterlogged slab schedules and performs a downward update.
2. Waterlogged source propagation occurs after neighbor changes.
3. A chunk unload removes pending tasks and a later chunk load restores them
   from persisted ChunkFluidData.
4. Dedicated-server startup does not load any client-only class.
5. Chunk watch synchronization sends the stored states to a joining client.
