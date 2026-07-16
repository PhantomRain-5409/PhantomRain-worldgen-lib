# Ptrlib WG 模组文档

> English: [README.md](README.md)

本模组为 Minecraft 世界生成提供了一套增强型扩展工具，旨在简化数据包与模组开发中的结构生成与维度管理流程。

> **前置要求**：使用本模组功能前，请确保您具备一定的 Minecraft 数据包基础知识。

## 目录

1. [固定位置结构生成](#1-固定位置结构生成)
2. [单 NBT 简单结构注册](#2-单-nbt-简单结构注册)
3. [维度预设](#3-维度预设)
4. [实时维度重置](#4-实时维度重置)
5. [维度副本](#5-维度副本)
6. [群系替换](#6-群系替换)

---

## 1. 固定位置结构生成

本模组注册了新的结构放置类型 `ptrlib_wg:fixed_position`，允许在世界的指定固定坐标生成结构。配置文件位于 `worldgen/structure_set` 目录下。

> **注意**：结构的生成高度（Y 轴）由对应的 `structure` 文件决定，不在此放置器中配置。

### 示例 JSON

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

**参数说明：**

- `x` 和 `z`：定义结构生成的区块坐标。两者均为可选字段，缺省时默认值为 `0`。上述示例将使远古城市固定在坐标 `(0, 0)` 处生成。

---

## 2. 单 NBT 简单结构注册

针对原版拼图结构配置繁琐且基于高度图生成难以微调 Y 轴位置的问题，本模组提供了 `ptrlib_wg:basic` 结构类型。配置文件位于 `worldgen/structure` 目录下。

### 字段说明

- **必填字段**：`biomes`、`nbt_path`、`spawn_overrides`、`step`
- **选填字段**：`terrain_adaptation`、`project_start_to_heightmap`、`fixed_height`、`y_offset`
- 除拼图模板池相关配置外，其余字段与原版拼图结构基本一致。

### 示例 1：固定高度生成（`fixed_height`）

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

- 当指定 `fixed_height` 时，结构将固定在设定的 Y 坐标生成，此时高度图配置将被忽略。
- `fixed_height` 也支持原版整数提供器，以实现 Y 坐标的随机生成：

```json
"fixed_height": {
  "type": "minecraft:uniform",
  "min_inclusive": 60,
  "max_inclusive": 80
}
```

### 示例 2：高度图与 Y 轴偏移（`y_offset`）

当未配置 `fixed_height` 时，模组将扫描地表表面高度并生成结构。

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

- `project_start_to_heightmap`：可选，默认为 `WORLD_SURFACE_WG`。
- `y_offset`：可选，默认为 `0`。使结构上下偏移，适用于部分埋地结构（如紫水晶、地窖等）。上述示例将使结构下降一格，可用作地基处理。

---

## 3. 维度预设

本模组提供了维度生成器类型 `preset`，可用于快速构建纯虚空维度模板。

### 示例 JSON

```json
{
  "type": "minecraft:overworld",
  "generator": {
    "type": "ptrlib_wg:preset",
    "default_biome": "minecraft:the_void"
  }
}
```

*说明：上述配置将生成带有原版虚空起始平台的纯虚空维度。若不需要该平台，可自行注册自定义虚空群系，或通过数据包覆盖原版相关结构。*

### 预设建筑导入流程

1. 进入生成的虚空维度，在其中放置预设建筑（如浮空岛等）。
2. 退出并保存世界，定位至 `saves\<存档名>\dimensions` 目录，该文件夹内存放了维度的区块数据。
3. 将您需要固化的内容（通常为地形区块文件，例如 `ptrlib_wg/void/region` 文件夹。可在游戏内按 `F3` 查看区块坐标与名称）复制到 `config/ptrlib_wg/preset_dims` 目录内。
4. 请确保最终目录与 `config/ptrlib_wg/preset_dims` 内部结构保持一致（`namespace/path/...`）。只需包含您要复制的区块文件即可。
5. 配置完成后，在后续创建新存档时，模组将自动提取并应用这些预设数据。

---

## 4. 实时维度重置

本功能允许在服务器运行期间动态重置特定维度，支持定时自动重置、指令触发及 KubeJS 联动。

### 配置文件说明

请在 `config/ptrlib_wg/ptrlib_wg-common.toml` 中进行维度重置参数的配置：

```toml
#格式: dimId;enable_auto;auto_empty;interval_min;warn_sec;force
#除了 dimId 外所有参数均可选。留空将使用默认值。
#- dimId: 维度ID (例如: twilightforest:twilight_forest)。支持原版维度，如 minecraft:the_nether 和 minecraft:the_end。
#- enable_auto: 开启定时自动重置 (默认: false)。若为 false，则只能通过指令手动重置。
#- auto_empty: 维度内无玩家时自动重置 (默认: false)。
#- interval_min: 重置间隔，单位为分钟 (默认: 60)。
#- warn_sec: 重置前的警告时间，单位为秒 (默认: 10)。
#- force: 重置前强制将玩家踢出该维度 (默认: true)。
# 最终列表示意：
reset_dimensions = "ptrlib_wg:void;true;false;1;10;true,spectrum:deeper_down;true;false;1;10;true"
```

**参数补充说明：**

- 当 `enable_auto` 设为 `false` 时，只能通过指令或 KubeJS 等方法手动重置。
- 当 `auto_empty` 设为 `true` 时，将在维度内无玩家时自动执行重置。
- 当 `force` 设为 `true` 时，重置时会强制踢出该维度内的所有玩家；若设为 `false`，则当维度内有玩家时取消该次重置。
- **自动重置不会更换种子**（始终保持当前种子）。

### 指令调用

使用指令可立即重置已在配置文件中注册的维度（需 OP 权限 2）：

```
/dim_reset <dim_id> [enable] [seed]
```

| 参数 | 说明 |
|------|------|
| `dim_id` | 必填。目标维度 ID（含命名空间） |
| `enable` | 可选，默认 `false`。为 `true` 时在重置前更换种子 |
| `seed` | 可选。仅当 `enable=true` 时生效；省略则使用随机种子 |

**示例：**

- `/dim_reset minecraft:the_nether` — 按当前种子清空并重置下界
- `/dim_reset ptrlib_wg:void true` — 使用随机新种子重置
- `/dim_reset ptrlib_wg:void true 12345` — 使用指定种子 `12345` 重置

*说明：换种后，新生成的区块将按新种子生成；该覆盖种子会写入存档（副本维度写入 `ptrlib_wg_dim_copies.json`，其它维度写入 `ptrlib_wg_dim_seeds.json`）。*

### KubeJS 联动示例（待测试）

可通过 KubeJS 调用底层 Java 类触发重置（需使用 `loadClass`）。

*示例：右键红石块重置指定维度。*

```javascript
// kubejs/server_scripts/wglib_interact.js
const DimResetHelper = Java.loadClass('com.ptr5409.wglib.dimreset.DimensionResetHelper');
const DimResetManager = Java.loadClass('com.ptr5409.wglib.dimreset.DimResetManager');

BlockEvents.rightClicked(event => {
    if (event.block.id === 'minecraft:redstone_block') {
        let server = event.server;
        let dimId = Utils.id('ptrlib_wg:void');

        // 不换种重置
        DimResetHelper.resetDimension(server, dimId, true, false);

        // 换种重置（随机）
        // DimResetHelper.resetDimension(server, dimId, true, false, true, null);

        // 换种重置（指定种子）
        // DimResetHelper.resetDimension(server, dimId, true, false, true, 12345);

        // 或走管理器接口（含全服提示）
        // DimResetManager.forceReset(server, dimId, true, null);
    }
});
```

**方法签名：**

```text
DimensionResetHelper.resetDimension(server, dimId, kickPlayers, isAutoEmpty)
DimensionResetHelper.resetDimension(server, dimId, kickPlayers, isAutoEmpty, enableSeed, seed)
DimResetManager.forceReset(server, dimId)
DimResetManager.forceReset(server, dimId, enableSeed, seed)
```

---

## 5. 维度副本

本功能允许在服务器运行期间，将已有维度复制为一份独立的新维度，并可选择是否使用新种子。仅本模组创建的副本可通过删除指令移除。

### 指令

需 OP 权限 2。

#### 创建副本

```
/dim_copy <dim_id> <id> [enable] [seed]
```

| 参数 | 说明 |
|------|------|
| `dim_id` | 必填。源维度 ID（将被复制的维度） |
| `id` | 必填。新维度 ID；若已存在则创建失败 |
| `enable` | 可选，默认 `false`。为 `true` 时使用自定义/随机种子；为 `false` 时沿用源维度种子 |
| `seed` | 可选。仅当 `enable=true` 时生效；省略则使用随机种子 |

**示例：**

- `/dim_copy minecraft:overworld ptrlib_wg:copy1` — 复制主世界，种子与源相同
- `/dim_copy minecraft:overworld ptrlib_wg:copy2 false` — 同上
- `/dim_copy minecraft:the_nether ptrlib_wg:nether_a true` — 复制下界，使用随机种子
- `/dim_copy minecraft:the_nether ptrlib_wg:nether_b true 20260101` — 复制下界，使用指定种子

#### 删除副本

```
/dim_delete <dim_id>
```

- `dim_id`：要删除的副本维度 ID。
- **仅副本维度可被删除**；原版或数据包原生维度不会被删除。
- 删除时会将仍在该维度内的玩家送回重生点，并清理对应区块数据。

### 数据与行为说明

- 副本元数据保存在世界存档根目录的 `ptrlib_wg_dim_copies.json`。
- 服务器重启后会自动重新加载已创建的副本维度。
- 副本维度可与「维度重置」配合使用：先在配置中注册副本 ID，即可定时或手动重置；手动重置时同样可指定新种子。


### KubeJS 联动示例（待测试）

可通过 KubeJS 调用 `DimCopyManager` 创建/删除副本。

*示例：右键绿宝石块创建副本，右键红石块删除副本。*

```javascript
// kubejs/server_scripts/wglib_dim_copy.js
const DimCopyManager = Java.loadClass('com.ptr5409.wglib.dimcopy.DimCopyManager');

// 创建副本：复制 overworld 为 ptrlib_wg:arena，使用随机种子
BlockEvents.rightClicked('minecraft:emerald_block', event => {
    let server = event.server;
    let source = Utils.id('minecraft:overworld');
    let copyId = Utils.id('ptrlib_wg:arena');

    // enableSeed=true, seed=null -> 随机种子
    let ok = DimCopyManager.createCopy(server, source, copyId, true, null);
    if (ok) {
        event.player.tell('已创建维度副本: ' + copyId);
    } else {
        event.player.tell('创建失败（可能已存在或源维度无效）');
    }
});

// 指定种子创建
// DimCopyManager.createCopy(server, source, copyId, true, 20260101);

// 不换种复制（沿用源维度种子）
// DimCopyManager.createCopy(server, source, copyId, false, null);

// 删除副本（仅本模组创建的副本可删）
BlockEvents.rightClicked('minecraft:redstone_block', event => {
    let server = event.server;
    let copyId = Utils.id('ptrlib_wg:arena');
    let ok = DimCopyManager.deleteCopy(server, copyId);
    event.player.tell(ok ? ('已删除: ' + copyId) : '删除失败（不是副本或不存在）');
});
```

**方法签名：**

```text
DimCopyManager.createCopy(server, sourceId, copyId, enableSeed, seed)
DimCopyManager.deleteCopy(server, copyId)
DimCopyManager.isCopy(dimId)
DimCopyManager.setSeed(server, copyId, seed)
```

### 传送示例

创建成功后，可使用原版指令进入副本：

```
/execute in ptrlib_wg:copy1 run tp @s ~ ~ ~
```

---

## 6. 群系替换

群系替换规则通过数据包加载，可作用于使用原版 `MultiNoiseBiomeSource` 的维度。安装 TerraBlender 时，也支持其注入的群系参数。

规则文件放置于：

```text
data/<命名空间>/ptrlib_wg/biome_replace/<文件名>.json
```

每个文件的根元素必须是 JSON 列表，列表中的每个元素对应一条独立规则。规则会在世界加载时解析；修改数据包后需重启世界或服务器。已经生成的区块会保留其已存储的群系，因此规则变更主要影响新生成区域。

### 通用字段

| 字段 | 说明 |
|------|------|
| `type` | `direct_replace`、`weighted_replace`、`weighted_infuse` 或 `sub_biome` |
| `dimension` | 可选维度 ID；省略时作用于所有兼容的 MultiNoise 维度 |
| `target` | 目标群系 ID 或 `#群系标签`；`weighted_infuse` 必须填写单个群系 ID |

### 直接替换

`direct_replace` 会替换所有匹配的目标群系。省略 `replacement` 时，该群系将被阻止生成。

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

### 权重替换

`weighted_replace` 按正整数权重，将目标群系对应的气候参数区域分配给多个新群系。

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

该类型按气候参数空间确定性切分，并非逐区块随机选择。因此群系边界会跟随气候过渡和被选中的切分轴。

### 权重注入

`weighted_infuse` 会保留一部分目标群系，同时按权重插入多个子群系。

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

- `parent_weight`：目标群系保留的权重，默认 `1`，最小为 `0`。
- `subs`：非空子群系列表，每个元素使用正整数权重。
- `target`：只能填写群系 ID，不能使用标签。

与 `weighted_replace` 相同，该类型切分气候参数空间，并不是运行时随机放置。

### 条件子群系

`sub_biome` 会在主群系选定后进行第二阶段判断，仅在条件匹配的位置替换为子群系。它可以表达群系边缘、内部、相邻群系、气候值及噪声斑块等规则。

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

同一目标存在多条子群系规则时，将按数据包中的顺序判断，第一条匹配规则生效。

#### 条件类型

| 类型 | 字段 | 行为 |
|------|------|------|
| `all_of` | `criteria` | 所有嵌套条件都匹配时生效 |
| `any_of` | `criteria` | 至少一个嵌套条件匹配时生效 |
| `not` | `criterion` | 反转一个嵌套条件的结果 |
| `value` | `parameter`、`min`、`max` | 判断原始气候值 |
| `deviation` | `parameter`、`min`、`max` | 判断相对主群系参数中心的有符号偏移 |
| `ratio` | `target`、`min`、`max`，可选 `parameter` | 判断相对 `center` 或 `edge` 的距离；越接近 `0` 表示越靠近指定区域 |
| `neighbor` | `biome`、`biomes` 或 `tag` | 判断气候匹配度第二高的相邻候选群系 |
| `original` | `biome`、`biomes` 或 `tag` | 判断静态替换前选中的群系 |
| `primary` | `biome`、`biomes` 或 `tag` | 判断静态替换后当前选中的主群系 |
| `patch_noise` | `min`、`max`，可选 `scale`、`salt` | 选择随世界种子稳定生成的平滑斑块；输出范围为 `0..1` |

支持的气候参数包括 `temperature`、`humidity`、`continentalness`、`erosion`、`depth`、`weirdness` 和 `peaks_valleys`。

`patch_noise.scale` 表示近似斑块尺寸（方块），默认值为 `96`。修改 `salt` 可以在不更换世界种子的情况下得到另一套稳定分布。

群系匹配可使用以下形式：

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

#### 内置预设

常见放置方式可以省略 `criterion`，直接在规则顶层填写 `preset`：

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

| 预设 | 行为 |
|------|------|
| `near_border` | 目标群系边界附近的狭窄区域 |
| `near_interior` | 目标群系气候中心附近的区域 |
| `beachside` | 与 `#minecraft:is_beach` 群系相邻的边界 |
| `oceanside` | 与 `#minecraft:is_ocean` 群系相邻的边界 |
| `riverside` | 与 `#minecraft:is_river` 群系相邻的边界 |

### 完整数据包示例

项目内提供了覆盖全部规则类型、可直接使用的测试数据包：

```text
test_datapack/biome_replace_test
```

将该文件夹复制到 `<存档>/datapacks/`，必要时启用数据包，然后重启世界或服务器。其规则文件位于：

```text
data/ptrlib_wg/ptrlib_wg/biome_replace/all_types.json
```

### 兼容性与限制

- 支持原版 MultiNoise 群系源与 TerraBlender 注入的 Region。
- 不会修改非 MultiNoise 群系源。
- 权重类型表达的是确定性的气候空间占比，实际生成面积为近似值，并不保证精确区块数量。
- 条件子群系在运行时进行选择；其群系会加入群系源的 possible-biomes 列表，使相关群系特征与结构能够识别它。
