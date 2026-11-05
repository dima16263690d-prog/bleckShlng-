# BleckShlng

Minecraft 1.12.2 / Forge 14.23.5.2864 backport of vanilla Minecraft 1.13+ axe stripping.

## Features

- Stripped logs and stripped wood for oak, spruce, birch, jungle, acacia and dark oak.
- Right-click stripping with vanilla axes.
- Modded axes are supported through Forge's `axe` tool class or a non-negative `getHarvestLevel(stack, "axe", ...)` result.
- No whitelist by item name, material or mod ID.
- Preserves X/Y/Z log orientation. The legacy `BlockLog.EnumAxis.NONE` state maps to the corresponding stripped-wood block.
- One durability point is consumed outside creative mode.
- Vanilla-like fire behavior and a furnace fuel value of 300 ticks.
- Server-authoritative world changes.
- Uses the closest available 1.12.2 wood-break sound; the dedicated `AXE_STRIP` sound does not exist in this version.

## Modded tool compatibility

Any modded item is accepted when it extends vanilla `ItemAxe`, advertises Forge's `axe` tool class, or exposes an axe harvest level. A third-party item that provides none of these Forge signals cannot be identified safely as an axe without a dedicated compatibility rule.

## Build

Use Java 8 and the included wrapper:

```bat
gradlew.bat genIntellijRuns
gradlew.bat build
```

The resulting jar is in `build/libs`.
