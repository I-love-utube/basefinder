# Base Finder (Fabric, Minecraft 26.3)

Client-side mod that watches the chests, barrels, shulker boxes, hoppers, droppers, dispensers, crafters and
spawners your game loads, groups nearby ones into clusters, and outlines each cluster in the world (visible
through walls). It only uses data your client already has. It sends nothing anywhere.

## Build and install

- JDK 25 and Gradle 9.x. The Gradle wrapper is not included: run `gradle wrapper --gradle-version 9.7.1` once
  (or copy the wrapper from the Fabric example mod), then `./gradlew build`.
- Versions are in `gradle.properties` (Loader 0.19.5, Loom 1.18.2, Fabric API 0.161.0+26.3). If a version does
  not resolve, take the current ones from https://develop.fabricmc.net.
- Put `build/libs/basefinder-0.1.0.jar` and Fabric API into your `mods` folder.

## Use

| Key (rebindable) | Action |
|---|---|
| B | Toggle scanning and highlights |
| N | Open settings |
| unbound | Open results list (also reachable from settings) |

Settings: scan range, minimum blocks per cluster, grouping radius, per-category on/off and color, mark each
block, chat alerts. Results list: nearest first, with **Ignore** and **Report FP** per cluster.

## How it works

- `Tracker` listens to Fabric's client block-entity load/unload events and keeps a map of relevant blocks.
  Loading a chunk costs one map insert per matching block, so nothing scans the world.
- Clustering (`Clusterer`) runs on a background thread at most once per second, only when something changed
  or you moved 16+ blocks. It links blocks whose chain of neighbours is within the grouping radius.
- `Store` remembers clusters per server (singleplayer worlds share one key) in
  `config/basefinder-discoveries.json`, so they stay listed and highlighted after chunks unload.
- `HighlightRenderer` draws a translucent box plus 12 thin edge bars per cluster using the custom
  through-walls pipeline pattern from Fabric's "Rendering in the World" docs.
- **Report FP** appends one JSON line (no server address) to `config/basefinder-reports.jsonl` and ignores the
  cluster. Attach that file to an issue if you want a rule changed.

## Status: not compiled or run in-game

Written without access to Fabric's Maven or a JDK 25, so it has never been built against 26.3.

- Tested: `Category`, `Cluster` and `Clusterer` (pure Java) pass a unit test (chains, gaps, min size, merge).
- Checked: all files parse, and cross-references between the mod's own classes resolve.
- Not tested: everything that touches Minecraft or Fabric. Names follow Fabric's 26.1/26.2 docs
  (`LevelRenderEvents`, `GuiGraphicsExtractor`, `KeyMappingHelper`, `ClientLevelEvents`). Likeliest fixes if the
  build fails: `ScreenUtil.open` (`minecraft.gui.setScreen` vs `minecraft.setScreen`), `ServerData.ip`,
  `ClientPlayConnectionEvents.JOIN` argument list, `StagedVertexBuffer` / `RenderType.SMALL_BUFFER_SIZE`.

## Limitations

- Only loaded chunks are visible, so detection range is your server's view distance. Saved clusters stay.
- Natural structures (dungeons, villages, mineshafts, bastions) can look like stashes. Raise the minimum block
  count or use Ignore / Report FP.
- Cluster bounds only grow. If you remove chests from a base, use Ignore or Clear list.
- No minimap integration; the results list shows coordinates.
- Many multiplayer servers prohibit base-finding mods. Check the rules before using it on one.
