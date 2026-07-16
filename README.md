# Ptrlib WG Documentation

PhantomRain's worldgen library for Minecraft (Forge 1.20.1). It adds tools for structure generation and runtime dimension management in datapacks and mods.

> **Prerequisite**: Basic knowledge of Minecraft datapacks is recommended.  
> 中文文档: [README_zh.md](README_zh.md)

## Table of Contents

1. [Fixed-Position Structure Placement](#1-fixed-position-structure-placement)
2. [Simple Single-NBT Structure](#2-simple-single-nbt-structure)
3. [Dimension Presets](#3-dimension-presets)
4. [Live Dimension Reset](#4-live-dimension-reset)
5. [Dimension Copies](#5-dimension-copies)
6. [Biome Replacement](#6-biome-replacement)

---

## 1. Fixed-Position Structure Placement

Registers placement type `ptrlib_wg:fixed_position` so structures can generate at fixed chunk coordinates. Place configs under `worldgen/structure_set`.

> **Note**: Vertical placement (Y) is controlled by the `structure` definition, not this placement type.

### Example JSON

```json
{
  "placement": {
    "type": "ptrlib_wg:fixed_position",
    "x": 0,
    "z": 0
  },
  "structures": [
    {
      "structure": "minecraft:ancient_city",
      "weight": 1
    }
  ]
}
```

**Fields:**

- `x` / `z` (optional, default `0`): chunk coordinates where the structure may place. The example pins an ancient city to chunk `(0, 0)`.

---

## 2. Simple Single-NBT Structure

`ptrlib_wg:basic` avoids heavy jigsaw setup and makes Y placement easier. Configs live under `worldgen/structure`.

### Fields

- **Required**: `biomes`, `nbt_path`, `spawn_overrides`, `step`
- **Optional**: `terrain_adaptation`, `project_start_to_heightmap`, `fixed_height`, `y_offset`
- Other fields mostly match vanilla structure-style settings (except jigsaw pool wiring).

### Example 1: Fixed height (`fixed_height`)

```json
{
  "type": "ptrlib_wg:basic",
  "biomes": "#minecraft:is_overworld",
  "nbt_path": "ptrlib_wg:plains_library",
  "spawn_overrides": {},
  "fixed_height": 100,
  "step": "surface_structures"
}
```

- With `fixed_height`, the structure spawns at that Y; heightmap projection is ignored.
- `fixed_height` also accepts vanilla int providers for random Y:

```json
"fixed_height": {
  "type": "minecraft:uniform",
  "min_inclusive": 60,
  "max_inclusive": 80
}
```

### Example 2: Heightmap + offset (`y_offset`)

Without `fixed_height`, the mod samples surface height, then applies offset.

```json
{
  "type": "ptrlib_wg:basic",
  "biomes": "#minecraft:is_overworld",
  "nbt_path": "ptrlib_wg:plains_library",
  "spawn_overrides": {},
  "y_offset": -1,
  "step": "surface_structures"
}
```

- `project_start_to_heightmap`: optional, default `WORLD_SURFACE_WG`.
- `y_offset`: optional, default `0`. Useful for slightly buried builds (e.g. foundation sink of 1 block).

---

## 3. Dimension Presets

Chunk generator type `ptrlib_wg:preset` builds empty/void-style dimensions quickly.

### Example JSON

```json
{
  "type": "minecraft:overworld",
  "generator": {
    "type": "ptrlib_wg:preset",
    "default_biome": "minecraft:the_void"
  }
}
```

*This produces a void-like dimension with the vanilla void start platform. To remove the platform, use a custom void biome or datapack overrides.*

### Importing preset builds

1. Enter the void dimension and place the buildings you want to keep.
2. Save and quit. Open `saves/<world>/dimensions`.
3. Copy the region data you need (e.g. `ptrlib_wg/void/region`; use `F3` in-game for chunk coords) into `config/ptrlib_wg/preset_dims`.
4. Keep the same layout under `preset_dims` (`namespace/path/...`). Only the chunk files you need are required.
5. On **new** worlds, the mod copies those presets into the save automatically (once per world via lock file).

---

## 4. Live Dimension Reset

Reset registered dimensions while the server is running: scheduled auto-reset, commands, or KubeJS.

### Config

Edit `config/ptrlib_wg/ptrlib_wg-common.toml`:

```toml
# Format: dimId;enable_auto;auto_empty;interval_min;warn_sec;force
# All fields except dimId are optional (defaults apply when blank).
# - dimId: dimension id (e.g. twilightforest:twilight_forest). Vanilla dims supported.
# - enable_auto: scheduled auto reset (default: false). If false, command/API only.
# - auto_empty: reset when no players are in the dimension (default: false).
# - interval_min: interval in minutes (default: 60).
# - warn_sec: warning seconds before reset (default: 10).
# - force: kick players before reset (default: true). If false, skip when players present.
reset_dimensions = "ptrlib_wg:void;true;false;1;10;true,spectrum:deeper_down;true;false;1;10;true"
```

**Notes:**

- Auto reset **never** changes seed (`enable=false`).
- Manual reset may optionally reseed (see command below).

### Command

Requires permission level 2. Dimension must be listed in config.

```
/dim_reset <dim_id> [enable] [seed]
```

| Arg | Description |
|-----|-------------|
| `dim_id` | Target dimension id (with namespace) |
| `enable` | Optional, default `false`. If `true`, change seed before reset |
| `seed` | Optional. Used only when `enable=true`; omit for a random seed |

**Examples:**

- `/dim_reset minecraft:the_nether` — reset with current seed
- `/dim_reset ptrlib_wg:void true` — reset with a random new seed
- `/dim_reset ptrlib_wg:void true 12345` — reset with seed `12345`

*Reseeded worlds use the new seed for newly generated chunks. Overrides are saved in the world folder (`ptrlib_wg_dim_copies.json` for copies, `ptrlib_wg_dim_seeds.json` for others).*

### KubeJS example (experimental)

```javascript
// kubejs/server_scripts/wglib_interact.js
const DimResetHelper = Java.loadClass('com.ptr5409.wglib.dimreset.DimensionResetHelper');
const DimResetManager = Java.loadClass('com.ptr5409.wglib.dimreset.DimResetManager');

BlockEvents.rightClicked(event => {
    if (event.block.id === 'minecraft:redstone_block') {
        let server = event.server;
        let dimId = Utils.id('ptrlib_wg:void');

        // Reset without reseed
        DimResetHelper.resetDimension(server, dimId, true, false);

        // Reseed random
        // DimResetHelper.resetDimension(server, dimId, true, false, true, null);

        // Reseed fixed
        // DimResetHelper.resetDimension(server, dimId, true, false, true, 12345);

        // Manager API (broadcast message)
        // DimResetManager.forceReset(server, dimId, true, null);
    }
});
```

**Signatures:**

```text
DimensionResetHelper.resetDimension(server, dimId, kickPlayers, isAutoEmpty)
DimensionResetHelper.resetDimension(server, dimId, kickPlayers, isAutoEmpty, enableSeed, seed)
DimResetManager.forceReset(server, dimId)
DimResetManager.forceReset(server, dimId, enableSeed, seed)
```

---

## 5. Dimension Copies

Create independent runtime copies of existing dimensions, optionally with a new seed. Only copies created by this mod can be deleted via command.

### Commands

Permission level 2.

#### Create

```
/dim_copy <dim_id> <id> [enable] [seed]
```

| Arg | Description |
|-----|-------------|
| `dim_id` | Source dimension to copy |
| `id` | New dimension id (fails if already exists) |
| `enable` | Optional, default `false`. `true` = custom/random seed; `false` = keep source seed |
| `seed` | Optional. Only when `enable=true`; omit for random |

**Examples:**

- `/dim_copy minecraft:overworld ptrlib_wg:copy1` — copy overworld, same seed
- `/dim_copy minecraft:overworld ptrlib_wg:copy2 false` — same
- `/dim_copy minecraft:the_nether ptrlib_wg:nether_a true` — random seed
- `/dim_copy minecraft:the_nether ptrlib_wg:nether_b true 20260101` — fixed seed

#### Delete

```
/dim_delete <dim_id>
```

- Only **copy** dimensions can be deleted (not vanilla/datapack native dims).
- Players inside are respawned; region data for the copy is cleaned.

### Data & behavior

- Metadata: world root `ptrlib_wg_dim_copies.json`
- Copies reload after server restart
- Register a copy id in dim-reset config to auto/manual reset it; manual reset can reseed

### KubeJS example (experimental)

```javascript
// kubejs/server_scripts/wglib_dim_copy.js
const DimCopyManager = Java.loadClass('com.ptr5409.wglib.dimcopy.DimCopyManager');

// Create: overworld -> ptrlib_wg:arena with random seed
BlockEvents.rightClicked('minecraft:emerald_block', event => {
    let server = event.server;
    let source = Utils.id('minecraft:overworld');
    let copyId = Utils.id('ptrlib_wg:arena');

    // enableSeed=true, seed=null -> random seed
    let ok = DimCopyManager.createCopy(server, source, copyId, true, null);
    if (ok) {
        event.player.tell('Created dimension copy: ' + copyId);
    } else {
        event.player.tell('Create failed (exists or invalid source)');
    }
});

// Fixed seed
// DimCopyManager.createCopy(server, source, copyId, true, 20260101);

// Same seed as source
// DimCopyManager.createCopy(server, source, copyId, false, null);

// Delete copy (mod-created only)
BlockEvents.rightClicked('minecraft:redstone_block', event => {
    let server = event.server;
    let copyId = Utils.id('ptrlib_wg:arena');
    let ok = DimCopyManager.deleteCopy(server, copyId);
    event.player.tell(ok ? ('Deleted: ' + copyId) : 'Delete failed (not a copy / missing)');
});
```

**Signatures:**

```text
DimCopyManager.createCopy(server, sourceId, copyId, enableSeed, seed)
DimCopyManager.deleteCopy(server, copyId)
DimCopyManager.isCopy(dimId)
DimCopyManager.setSeed(server, copyId, seed)
```

### Teleport example

```
/execute in ptrlib_wg:copy1 run tp @s ~ ~ ~
```

---

## 6. Biome Replacement

Biome replacement rules are loaded from datapacks and applied to vanilla `MultiNoiseBiomeSource` dimensions. TerraBlender-injected biome parameters are also supported when TerraBlender is installed.

Place rule files under:

```text
data/<namespace>/ptrlib_wg/biome_replace/<file>.json
```

Each file must contain a JSON array. Every array element is one independent rule. Rules are resolved when the world loads; restart the world/server after editing the datapack. Existing generated chunks keep their stored biomes, so changes primarily affect newly generated terrain.

### Common fields

| Field | Description |
|-------|-------------|
| `type` | `direct_replace`, `weighted_replace`, `weighted_infuse`, or `sub_biome` |
| `dimension` | Optional dimension ID. If omitted, the rule applies to every compatible MultiNoise dimension |
| `target` | Target biome ID or `#biome_tag`; `weighted_infuse` requires one biome ID |

### Direct replacement

`direct_replace` replaces every matching target biome. Omit `replacement` to prevent that biome from being selected.

```json
[
  {
    "type": "direct_replace",
    "dimension": "minecraft:overworld",
    "target": "minecraft:dark_forest",
    "replacement": "minecraft:cherry_grove"
  },
  {
    "type": "direct_replace",
    "dimension": "minecraft:overworld",
    "target": "minecraft:swamp"
  },
  {
    "type": "direct_replace",
    "target": "#minecraft:is_beach",
    "replacement": "minecraft:snowy_beach"
  }
]
```

### Weighted replacement

`weighted_replace` divides each matching climate parameter area between the listed biomes according to positive integer weights.

```json
{
  "type": "weighted_replace",
  "dimension": "minecraft:overworld",
  "target": "minecraft:forest",
  "replacements": [
    { "biome": "minecraft:plains", "weight": 9 },
    { "biome": "minecraft:mushroom_fields", "weight": 1 }
  ]
}
```

This is deterministic climate-space splitting, not per-chunk random selection. Biome borders therefore follow climate transitions and the selected split axis.

### Weighted infusion

`weighted_infuse` retains part of the target biome while inserting weighted child biomes.

```json
{
  "type": "weighted_infuse",
  "dimension": "minecraft:overworld",
  "target": "minecraft:birch_forest",
  "parent_weight": 7,
  "subs": [
    { "biome": "minecraft:old_growth_birch_forest", "weight": 2 },
    { "biome": "minecraft:flower_forest", "weight": 1 }
  ]
}
```

- `parent_weight`: weight retained by the target biome; default `1`, minimum `0`.
- `subs`: non-empty child list using positive integer weights.
- `target`: must be a biome ID, not a tag.

Like `weighted_replace`, this mode splits climate parameter space and is not runtime random placement.

### Conditional sub-biomes

`sub_biome` performs a second selection after the primary biome has been chosen. It replaces only positions where its condition matches, allowing border, interior, neighboring-biome, climate, and noise-patch placement.

```json
{
  "type": "sub_biome",
  "dimension": "minecraft:overworld",
  "target": "minecraft:plains",
  "biome": "minecraft:sunflower_plains",
  "criterion": {
    "type": "all_of",
    "criteria": [
      {
        "type": "ratio",
        "target": "center",
        "max": 0.35
      },
      {
        "type": "patch_noise",
        "min": 0.0,
        "max": 0.45,
        "scale": 96.0,
        "salt": 1001
      }
    ]
  }
}
```

For rules with the same target, conditions are evaluated in datapack order and the first matching rule wins.

#### Criterion types

| Type | Fields | Behavior |
|------|--------|----------|
| `all_of` | `criteria` | Matches when every nested criterion matches |
| `any_of` | `criteria` | Matches when at least one nested criterion matches |
| `not` | `criterion` | Inverts one nested criterion |
| `value` | `parameter`, `min`, `max` | Tests the raw climate value |
| `deviation` | `parameter`, `min`, `max` | Tests signed deviation from the primary biome parameter center |
| `ratio` | `target`, `min`, `max`, optional `parameter` | Tests distance from `center` or `edge`; values near `0` are closer to the requested target |
| `neighbor` | `biome`, `biomes`, or `tag` | Tests the next-best fitting biome |
| `original` | `biome`, `biomes`, or `tag` | Tests the biome selected before static replacement |
| `primary` | `biome`, `biomes`, or `tag` | Tests the current primary biome after static replacement |
| `patch_noise` | `min`, `max`, optional `scale`, `salt` | Selects smooth seed-dependent patches; output range is `0..1` |

Supported climate parameters are `temperature`, `humidity`, `continentalness`, `erosion`, `depth`, `weirdness`, and `peaks_valleys`.

`patch_noise.scale` is the approximate patch size in blocks and defaults to `96`. Change `salt` to produce a different stable pattern without changing the world seed.

Biome matching accepts one of these forms:

```json
{ "type": "neighbor", "biome": "minecraft:river" }
```

```json
{ "type": "neighbor", "tag": "minecraft:is_ocean" }
```

```json
{
  "type": "neighbor",
  "biomes": ["minecraft:river", "minecraft:frozen_river"]
}
```

#### Built-in presets

For common placements, `criterion` can be replaced by a top-level `preset`:

```json
{
  "type": "sub_biome",
  "dimension": "minecraft:overworld",
  "target": "minecraft:forest",
  "biome": "minecraft:flower_forest",
  "preset": "near_border",
  "max": 0.2
}
```

| Preset | Behavior |
|--------|----------|
| `near_border` | Thin area near the target biome border |
| `near_interior` | Area near the target biome climate center |
| `beachside` | Border adjacent to a biome in `#minecraft:is_beach` |
| `oceanside` | Border adjacent to a biome in `#minecraft:is_ocean` |
| `riverside` | Border adjacent to a biome in `#minecraft:is_river` |

### Complete datapack example

A ready-to-use datapack covering all rule types is included at:

```text
test_datapack/biome_replace_test
```

Copy that folder into `<world>/datapacks/`, enable it when necessary, and restart the world/server. Its rule file is located at:

```text
data/ptrlib_wg/ptrlib_wg/biome_replace/all_types.json
```

### Compatibility and limitations

- Supports vanilla MultiNoise biome sources and TerraBlender-injected regions.
- Does not modify non-MultiNoise biome sources.
- Weighted modes describe deterministic climate-space proportions; exact generated area is approximate rather than a guaranteed chunk count.
- Conditional sub-biomes are runtime selections. Their biome is added to the source's possible-biome list so biome features and structures can recognize it.
