# BleckShlng

**A vanilla Minecraft 1.13+ stripped-log and stripped-wood backport for Minecraft 1.12.2.**

BleckShlng brings the stripped log and stripped wood blocks, axe interaction, and related block behavior to Forge 1.12.2. The implementation is adapted to the older block states and Forge event system used by this Minecraft version.

## What was ported

The mod adds the two stripped block types for all six vanilla overworld wood families:

| Wood type | Stripped log | Stripped wood |
|---|---|---|
| Oak | `stripped_oak_log` | `stripped_oak_wood` |
| Spruce | `stripped_spruce_log` | `stripped_spruce_wood` |
| Birch | `stripped_birch_log` | `stripped_birch_wood` |
| Jungle | `stripped_jungle_log` | `stripped_jungle_wood` |
| Acacia | `stripped_acacia_log` | `stripped_acacia_wood` |
| Dark Oak | `stripped_dark_oak_log` | `stripped_dark_oak_wood` |

That is **12 registered blocks** in total. Each block has its own block state, model, item model, and textures. English and Russian name translations are included.

## Axe stripping

1. Hold an axe.
2. Right-click a supported vanilla log or wood block.
3. The block is replaced with its stripped counterpart.

Supported source blocks are the six vanilla log variants represented by `Blocks.LOG` and `Blocks.LOG2` in Minecraft 1.12.2.

### Axe compatibility

The interaction handler accepts:

- Vanilla `ItemAxe` items.
- Modded items advertising Forge's `axe` tool class.
- Items reporting a non-negative axe harvest level through `getHarvestLevel(stack, "axe", ...)`.

There is no hard-coded whitelist of mod IDs, item registry names, or axe materials. A third-party tool must expose at least one of the supported Forge/vanilla axe signals to be recognized.

### Block orientation

The original X, Y, and Z log axes are preserved when stripping a log.

Minecraft 1.12.2 uses `BlockLog.EnumAxis.NONE` for the all-bark wood state. BleckShlng maps that state to the matching stripped wood block, while ordinary X/Y/Z log states become stripped logs.

## Block properties and behavior

The stripped blocks use the following vanilla-style properties:

| Property | Value |
|---|---:|
| Material | Wood |
| Hardness | 2.0 |
| Blast resistance | 2.0 |
| Harvest tool | Axe |
| Harvest level | 0 |
| Sound | Wood |
| Fire encouragement | 5 |
| Flammability | 5 |
| Furnace fuel value | 300 ticks |

Additional behavior:

- Stripping consumes **1 durability point** from the axe outside creative mode.
- Creative mode does not consume axe durability.
- Stripped blocks can sustain leaves.
- Breaking a stripped log marks nearby leaves for decay, matching vanilla log behavior.
- The block replacement is performed server-side and synchronized to clients.
- Minecraft 1.12.2 does not provide the later dedicated axe-stripping sound event, so the mod uses the closest available vanilla wood sound.

## Crafting recipes

Each stripped log can be crafted into its matching stripped wood:

- Place **4 matching stripped logs** in a 2×2 square.
- The recipe produces **3 matching stripped wood blocks**.

Recipes are registered independently from the axe-stripping interaction. This keeps crafting separate from tool compatibility.

## Client and server

BleckShlng is a Forge mod intended for both client and dedicated-server environments.

- The server handles the block interaction, block replacement, and axe durability.
- The client registers and renders the block models and textures.
- For multiplayer, install the same BleckShlng JAR on the server and on every connecting client.

The code separates client-only model registration from common block and interaction code. Dedicated-server deployment should still be tested with the exact modpack and server configuration in use.

## Requirements

- Minecraft **1.12.2**
- Minecraft Forge **14.23.5.2864**
- Java **8**

The mod has no required third-party mod dependencies.

## Installation

1. Install Forge 1.12.2.
2. Put the BleckShlng JAR into the `mods` folder.
3. For multiplayer, install the same JAR on the server and all clients.
4. Launch the game.

## Build from source

Use Java 8 and the included Gradle wrapper.

**Windows:**

```bat
gradlew.bat build
```

**Linux:**

```bash
./gradlew build
```

The reobfuscated mod JAR is generated in `build/libs/`.

## Project details

- Mod ID: `bleckshlng`
- Mod version: `1.0.0`
- Minecraft target: `1.12.2`
- Forge: `14.23.5.2864`
- License: see the repository license file, if present.

## Credits

- Vanilla Minecraft for the original stripped-log, stripped-wood, and axe-stripping behavior.
- Forge for the 1.12.2 modding API and tool-class compatibility system.
