# BleckShlng

**Minecraft 1.12.2 Forge mod — modern fluid-system backport and stripped-wood features.**

BleckShlng targets Minecraft 1.12.2 and Java 8. The project contains a standalone fluid core inspired by the modern Minecraft fluid architecture, with a Forge 1.12.2 compatibility layer.

> **Development status:** the fluid backport is actively being developed. A successful Gradle build confirms compilation and automated tests only; it does not guarantee that all fluid rendering and in-game behavior match modern Minecraft. Water and lava behavior, especially on slabs and stairs, still require in-game verification.

## Main features

### Modern fluid core

The fluid implementation is separated from Minecraft 1.12.2 classes:

- Immutable `FluidState` with fluid kind, amount, source and falling state.
- Separate water and lava implementations.
- Flow direction, spreading, slope search and source conversion logic.
- A `FluidWorld` abstraction so the core is not tied directly to the legacy world API.
- `ModernFluidEngine` as the runtime entry point.

The Forge adapter translates between the portable fluid core and Minecraft 1.12.2 world, block, chunk and event APIs.

### Waterlogging and fluid storage

The project includes:

- Per-chunk fluid state storage and persistence.
- Fluid state synchronization and client-side cache.
- Waterlogged block handling and placement hooks.
- Bucket interaction and sponge handling.
- Public access through `FluidAPI`.
- Forge fluid compatibility hooks.

Compatibility with every third-party fluid or waterloggable block is not guaranteed.

### Fluid rendering and effects

The client implementation includes:

- Custom fluid mesh generation for waterlogged blocks.
- Separate fluid-face occlusion geometry.
- Integration hooks for vanilla liquid rendering.
- Water and lava animation, ambient effects, particles and sounds.

Rendering parity with Minecraft 1.18.2 is not yet considered complete. Complex block shapes and modded occlusion geometry need further validation.

### Stripped logs and stripped wood

The mod also contains stripped log and stripped wood blocks for the six vanilla overworld wood types:

- Oak
- Spruce
- Birch
- Jungle
- Acacia
- Dark Oak

Axes can strip supported vanilla logs and wood. Axe compatibility uses vanilla/Forge axe tool signals rather than a hard-coded list of mod IDs. Crafting recipes are registered separately from the stripping interaction.

## Requirements

- Minecraft **1.12.2**
- Minecraft Forge **14.23.5.2864**
- **Java 8**
- **MixinBooter 12.2 or newer** for the Mixin-based chunk-render integration.

MixinBooter is a separate dependency and is not bundled in the BleckShlng JAR. Install it on the client. For a multiplayer installation, use matching mod versions on the server and clients; client-only rendering hooks remain client-side.

## Installation

1. Install Minecraft Forge 1.12.2 (14.23.5.2864).
2. Install MixinBooter 12.2 or newer.
3. Place the BleckShlng JAR in the `mods` directory.
4. Launch Minecraft.

For multiplayer, install the same BleckShlng build on the server and all connecting clients. Test dedicated-server startup with the exact modpack before deployment.

## Build from source

Use Java 8 and the Gradle wrapper included in the repository.

**Windows**
```bat
gradlew.bat build
```

**Linux**
```bash
./gradlew build
```

Build output is written to `build/libs/`.

## Project layout

```
src/main/java/com/dima16263690d/bleckshlng/
├── fluid/
│   ├── api/             Public fluid API and world view
│   ├── compatibility/   Forge / Minecraft 1.12.2 adapter
│   ├── core/            Portable fluid model and engine
│   ├── engine/          Spread, mixing and flow helpers
│   ├── geometry/        Block geometry and shape adapters
│   ├── interaction/     Bucket interactions
│   ├── network/         Fluid synchronization
│   ├── physics/         Entity flow and movement
│   ├── render/          Fluid occlusion shapes
│   ├── state/           Fluid state mapping
│   └── waterlogging/    Chunk storage and waterlogged behavior
├── coremod/             Bytecode integration hooks
├── client/              Client effects and rendering
└── mixin/               Mixin integration
```

## Testing and compatibility

The repository contains unit/regression tests for fluid state mapping, spreading, waterlogging storage, core fluid behavior, and integration configuration.

Automated tests do not replace in-game testing. Before reporting a fluid-rendering issue, test at minimum:

- Water and lava on bottom and top slabs.
- Water and lava beside stairs and other partial blocks.
- Horizontal spreading and downward flow.
- Source blocks and flowing levels.
- Chunk unload/reload and world save/reload.
- Bucket placement/pickup and sponge absorption.
- Client/server synchronization.

## Development reference

The modern fluid implementation is developed with reference to the project's Minecraft 1.18.2 backport research repository:

- [bLECPORT-1.18.2](https://github.com/dima16263690d-prog/bLECPORT-1.18.2)

The target runtime remains Forge 1.12.2; modern Minecraft classes are not available at runtime.

## Project metadata

- Mod ID: `bleckshlng`
- Minecraft: `1.12.2`
- Forge: `14.23.5.2864`
- Java: `8`

## Credits

- Mojang/Minecraft for the original game behavior and reference implementations.
- Minecraft Forge for the 1.12.2 modding API.
- MixinBooter for Mixin runtime integration.
