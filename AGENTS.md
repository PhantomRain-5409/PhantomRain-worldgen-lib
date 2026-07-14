# AGENTS.md — ptrlib_wg 项目说明

## 项目概况

- **模组 ID**: `ptrlib_wg`
- **包名根**: `com.ptr5409.wglib`
- **主类**: `com.ptr5409.wglib.PhantomRainWorldgenLib`
- **版本**: Minecraft 1.20.1 / Forge 47.x
- **映射**: official
- **Java**: 17
- **Mixin**: 已配置（`ptrlib_wg.mixins.json`，包 `com.ptr5409.wglib.mixin`）

## 功能模块

1. **世界生成库**：结构、放置类型、预设 ChunkGenerator 注册
2. **维度预设复制**：服务器启动时将配置目录预设维度文件拷入存档
3. **维度重置**：按配置定时/强制重置维度区块（`dim_reset`，可选手动换种）
4. **维度副本**：运行时复制维度（可改种子）、删除副本（`dim_copy` / `dim_delete`）

## 源码包结构

```
com.ptr5409.wglib/
  PhantomRainWorldgenLib.java          # 主类（注册 DeferredRegister、配置）
  accessor/                            # Mixin 访问器接口
    MinecraftServerAccessor
  command/                             # 指令注册
    DimCopyCommand                     # /dim_copy /dim_delete
    DimResetCommand                    # /dim_reset
  config/                              # Forge 配置
    DimResetConfig
  dimcopy/                             # 维度副本创建/删除/持久化
    DimCopyManager
  dimreset/                            # 维度重置逻辑
    DimResetManager
    DimensionResetHelper
    DimSeedOverrides                   # 非副本维度的换种覆盖
  mixin/                               # Mixin（勿改 package，与 mixins.json 绑定）
    LevelResourceAccessor
    MinecraftServerMixin
    ServerLevelMixin
  worldgen/
    structure/                         # 结构与放置
      BasicStructure
      BasicStructurePiece
      FixedPositionPlacement
      WGStructureTypes
      WGStructurePieceTypes
      WGPlacementTypes
    generator/                         # 自定义区块生成器注册
      PresetChunkGenerator
      WGChunkGenerators
    preset/                            # 预设维度文件管理
      PresetChunkManager

src/main/resources/
  META-INF/mods.toml
  ptrlib_wg.mixins.json
  pack.mcmeta
  assets/ptrlib_wg/lang/               # en_us.json / zh_cn.json
```

## 指令

### 维度副本

- `/dim_copy <dim_id> <id> [enable] [seed]`
  - `dim_id`：源维度（ResourceLocation）
  - `id`：新维度 ID（ResourceLocation，已存在则失败）
  - `enable`：是否自定义种子，默认 `false`（沿用源维度种子）
  - `seed`：仅 `enable=true` 时生效；省略则随机种子
- `/dim_delete <dim_id>`：仅可删除本模组创建的副本维度
- 元数据：世界存档根目录 `ptrlib_wg_dim_copies.json`

### 维度重置

- `/dim_reset <dimension_id> [enable] [seed]`：强制重置已在配置中注册的维度
  - `enable` 默认 `false`（不换种）
  - `enable=true` 时换种；`seed` 省略则随机
- 自动重置始终不换种（`enable=false`）
- 非副本换种写入 `ptrlib_wg_dim_seeds.json`；副本换种更新 `ptrlib_wg_dim_copies.json`

### API（KubeJS / 其它模组）

```
DimensionResetHelper.resetDimension(server, dimId, kickPlayers, isAutoEmpty)
DimensionResetHelper.resetDimension(server, dimId, kickPlayers, isAutoEmpty, enableSeed, seed)
DimResetManager.forceReset(server, dimId)
DimResetManager.forceReset(server, dimId, enableSeed, seed)
```

## Agent 工作规范

### 输出与思考

- 使用**中文**思考与回复用户
- 输出简洁：说明写了什么即可，**不要**大段贴代码或预览
- 读取文件时只读当前任务相关文件，不扩大范围

### 代码规范

- 新功能按职责放入对应包（`dimcopy` / `dimreset` / `worldgen.*` / `command` / `config`）
- 除必要入口（主类、指令注册）外，尽量少改无关类；优先**新建类**
- 代码内仅在关键模块/类前用简短中文注释标注功能；避免无意义行内注释
- **不要**引入 mixinextras；用原版 Mixin（`@Inject` / `@Redirect` / `@Invoker` 等）
- Mixin 类必须放在 `com.ptr5409.wglib.mixin`，并同步 `ptrlib_wg.mixins.json`
- 世界内玩家可见文本必须用**翻译键**，并写入 `en_us.json` 与 `zh_cn.json`
- 日志/调试文本可用硬编码英文，不要求本地化
- 重构包名时：只改路径与 `package`/`import`，不改业务逻辑
- 写文件请使用 **UTF-8 无 BOM**，避免中文乱码

### 实现偏好

- 配置非必须时可不做 Forge 配置项；副本/种子元数据可存世界存档目录
- 参考外部实现时按本项目包名与规范重写，不直接复制依赖其它模组 API
- 不提交无关重构；改动保持最小、可运行

### 构建

- Gradle Wrapper：`gradlew` / `gradlew.bat`
- 常用：`build`、`runClient`、`compileJava`