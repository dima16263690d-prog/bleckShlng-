# BleckShlng

**A Minecraft Forge 1.12.2 mod that backports stripped wood blocks and develops a modern fluid system inspired by Minecraft 1.13–1.18.2.**

BleckShlng targets Minecraft 1.12.2 and Java 8. It combines stripped-log/stripped-wood features with an in-progress fluid engine intended to bring newer fluid behavior to the older Forge environment.

> **Development status:** the fluid port is experimental and is not yet a complete, verified recreation of Minecraft 1.18.2. A successful Gradle build does not guarantee correct in-game fluid physics or rendering. Test in a disposable world and report issues with logs and screenshots.

## Features

### Stripped logs and stripped wood
- Adds stripped log and stripped wood blocks for the six vanilla overworld wood families.
- Preserves log axis/orientation when stripping.
- Supports vanilla axes and modded tools exposing Forge's axe tool class or harvest-level information.
- Keeps axe stripping separate from crafting recipes.
- Includes block/item models, textures, and English/Russian translations.

### Modern fluid port (work in progress)
The fluid implementation is being moved into a standalone core with a thin Minecraft 1.12.2/Forge adapter.

Current code areas include:
- Separate fluid-state and fluid-kind models for water and lava.
- Flowing-fluid updates, spreading, source conversion, and flow-vector calculations.
- A Forge 1.12.2 world adapter.
- Waterlogged-fluid state storage, chunk persistence, and client synchronization.
- Bucket interaction and sponge-related handling.
- Fluid collision/flow interaction with entities.
- Custom waterlogged-fluid chunk meshing and liquid-render hooks.
- Water/lava animation, ambient effects, sounds, and particles.
- Automated tests for selected fluid-state, flow, persistence, and transformer behavior.

**Not all modern behavior is complete.** Exact parity for every block shape, occlusion case, bucket/container interaction, modded fluid, chunk transition, and water/lava rendering path still requires implementation and in-game verification. Slabs, stairs, flowing surfaces, waterfalls, and lava rendering are important regression-test cases.

## Requirements
- Minecraft 1.12.2
- Minecraft Forge 14.23.5.2864
- Java 8 (JDK for building)
- MixinBooter 12.2 or newer at runtime for the Mixin integration

MixinBooter is a separate dependency. It is not bundled or shaded into the BleckShlng JAR.

## Installation
1. Install Minecraft Forge 1.12.2 (14.23.5.2864).
2. Install MixinBooter 12.2 or newer into the mods folder.
3. Place the built BleckShlng JAR into the same mods folder.
4. Launch the game.

For multiplayer, install compatible versions of BleckShlng and MixinBooter on the server and connecting clients. Client-only rendering integrations run only on the client.

**Use a backup or a new test world while the fluid system is under development.**

## Build from source
Use JDK 8 and the included Gradle wrapper.

Windows:
```bat
gradlew.bat build
```

Linux:
```bash
./gradlew build
```

The build produces the mod JAR under build/libs/ (including the reobfuscated artifact).

## Project layout
```
src/main/java/com/dima16263690d/bleckshlng/
  block/          Stripped block implementations
  client/         Client setup, fluid animation, and rendering
  coremod/        Forge coremod and bytecode hooks
  fluid/
    api/          Public fluid API and world-view contracts
    compatibility/Forge/Minecraft 1.12.2 adapters
    core/         Portable fluid states and water/lava logic
    engine/       Spread, mixing, and flow engines
    geometry/     Block geometry and waterlogging support
    interaction/  Bucket and fluid interactions
    network/      Fluid state synchronization
    physics/      Entity-fluid interaction
    render/       Fluid occlusion and rendering shapes
    state/        Fluid state mapping and height calculations
    waterlogging/ Waterlogged state, ticks, and persistence
  mixin/          Client rendering and animation mixins

src/test/           Automated tests
```

## Testing checklist
When changing fluid code, build and run the automated tests, then verify behavior in Minecraft 1.12.2. At minimum, test:
- Still and flowing water on full blocks, bottom slabs, top slabs, and stairs.
- Water poured from a bucket onto slabs and other partial blocks.
- Horizontal spreading, downward flow, and waterfalls.
- Still and flowing lava, including lava animation and side faces.
- Fluid visibility at block boundaries (no flicker, missing faces, or thin strips).
- Waterlogged blocks after chunk unload/reload and world save/reopen.
- Bucket pickup/placement, sponge behavior, and water/lava interactions.
- Entity movement in flowing water.
- Client/server synchronization in a dedicated-server world.

Automated tests cover selected logic only; they do not replace in-game checks.

## Project details
- Mod ID: bleckshlng
- Minecraft: 1.12.2
- Forge: 14.23.5.2864
- Java: 8
- Mod version: 1.0.0 (project build version)

## Credits
- Mojang/Minecraft for the original game behavior and reference implementations.
- Minecraft Forge for the 1.12.2 modding API.
- MixinBooter for the shared Mixin runtime used by the integration.
