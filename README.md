# Ring Place

Minecraft **1.8.9** Forge client mod that only silent-places a saved ring. No kill aura, grind, clicker, or other modules.

## Build

Requires **JDK 17** for Gradle and a **Java 8** toolchain for the game (Foojay resolver can download it).

```bat
gradlew remapJar
```

The jar is written to `build/libs/ringplace-1.6.11.jar`. Put it in `.minecraft/mods`. Do not load it next to the full FPS-Patcher client (same mixins / overlapping features).

## In game

- `.ringplace` — toggle
- `.coords` — save the block you are looking at (or bind **B**)
- `.ring item obsidian` — block to place
- `.ring list` / `.ring file` — plan from the coordinate file
- `.bind ringplace <key>` — keybind

Saved points: `.minecraft/config/ringplace/coords/points.jsonl`
