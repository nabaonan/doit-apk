# AGENT.md — doit-apk 接手指南

本文件面向接手本项目的 AI / 开发者。目标是让接手者在动手前先理解工程结构、同步契约、业务口径和本地环境陷阱。

用户视角说明见 [README.md](./README.md)；本文件记录开发约束和高风险区域。

## 1. 项目定位

`doit-apk` 是 `doit` 待办体系的 Android 原生客户端。Kotlin + Jetpack Compose，离线优先，通过 WebDAV 与桌面 / GUI / 终端端共享 `doit-backup.json`。

四端仓库：

| 端 | 仓库 |
| --- | --- |
| 手机端 | `nabaonan/doit-apk`（本仓库） |
| 桌面端 | `nabaonan/doit-desktop` |
| GUI 端 | `nabaonan/doit-gpui` |
| 终端版 | `nabaonan/doit-tui` |
| 官网 | `nabaonan/doit-website` |

命名统一为 `doit`：包 `com.doit.app`、模块 `:doit`、数据库 `doit.db`、DataStore `doit_settings`、主题令牌 `Doit`。任何 `quandian` / `Q` 前缀都是待清理的旧名残留。

## 2. 技术栈与模块

- 单模块 Gradle：根项目 `doit`，`settings.gradle.kts` 只 `include(":doit")`
- 应用坐标：`namespace = applicationId = com.doit.app`；debug 包 `com.doit.app.debug`
- SDK：`compileSdk = targetSdk = 35`，`minSdk = 26`；JVM target 17
- 关键依赖：AGP 8.7.3、Kotlin 2.0.21、KSP、Compose BOM 2024.12.01、Room 2.6.1、DataStore 1.1.1、Coroutines 1.9.0
- Gradle wrapper 8.11.1；`configuration-cache=true`，版本推导必须用 `providers.exec` 调 git

依赖仓库按环境隔离：`settings.gradle.kts` 本机使用阿里云镜像，`CI=true` 时只用官方仓库（境外访问阿里云会 502）。`repositoriesMode = FAIL_ON_PROJECT_REPOS`。

## 3. 目录结构与职责

源码根：`doit/src/main/java/com/doit/app/`

| 路径 | 职责 |
| --- | --- |
| `DoitApp.kt` / `MainActivity.kt` | Application 与 Activity 入口，挂载 `DoitTheme { MainScreen(vm) }` |
| `data/Models.kt` | `TaskEntity` / `CategoryEntity` / `CompletionLogEntity`、`TaskNode`、`buildTree()` / `flatten()` |
| `data/DoitDatabase.kt` | Room DB v2、DAO、迁移 `MIGRATION_1_2` |
| `data/TaskRepository.kt` | 单例仓库，种子数据（仅 debug）、增删改、级联、软删、快照应用 |
| `data/SettingsRepository.kt` | DataStore：皮肤 / 主题 / WebDAV / 同步基线 / revision / deviceId / 遗留 githubToken |
| `sync/SyncModels.kt` | 纯 Kotlin `SyncTask` / `SyncCategory` / `SyncSnapshot`（schemaVersion=2） |
| `sync/SnapshotCodec.kt` | v2 JSON 编解码、v1 兼容、Room ↔ 快照转换 |
| `sync/SyncMerge.kt` | 三方合并全部规则，纯 JVM 可单测 |
| `sync/SyncEngine.kt` | 一轮同步编排，Mutex 串行，整轮成功才落盘 base |
| `sync/WebDavClient.kt` | PUT 探针、GET / PUT / DELETE、中文错误诊断 |
| `sync/DavUrl.kt` | WebDAV 地址格式校验 |
| `update/UpdateChecker.kt` | GitHub Releases 检查与语义版本比较 |
| `update/ApkDownloader.kt` | APK 流式下载、进度回调、安装意图 |
| `ui/MainViewModel.kt` | `UiState` / `Page` / `Sheet` / `UpdateState`、全部业务动作 |
| `ui/MainScreen.kt`、`StatsPage.kt`、`CalendarPage.kt`、`SettingsPage.kt` | 四个页面 |
| `ui/components/*` | `TaskRow` 滑动卡片、`Sheets` 弹层、`Common` 进度环等 |
| `ui/theme/Color.kt`、`Theme.kt` | `Doit` 令牌、亮暗皮肤、优先级色、分类色板 |
| `ui/DoitIcon.kt` | Material Icons Extended 封装与云同步三态动画 |

测试位于 `doit/src/test/java/com/doit/app/`，覆盖任务树、DavUrl、SyncMerge（33 个场景）、统计序列、UpdateChecker，共约 59 个 `@Test`。README 中“27 个”是旧数字。

## 4. 数据模型与同步契约

### 4.1 Room

- `TaskEntity`：`uid` 是稳定标识；`id` 是本地行号，不可跨设备使用；`parentId` 自引用；`deletedAt` 为墓碑
- `CategoryEntity`：`name` 为 PK；`colorHex` 统一带 `#` 大写；`deletedAt` 墓碑
- `CompletionLogEntity`：统计热力图 / 环形图 / 折线图数据源
- 查询铁律：业务查询一律 `deletedAt IS NULL`；同步快照用 `allIncludingDeleted()`

### 4.2 DataStore `doit_settings`

保存 `skin`、`theme_mode`、`dav_enabled/url/user/pass`、`dav_verified`、`last_sync`、`last_sync_at`、`sync_base`（上次成功同步的规范化快照 JSON）、`sync_revision`、`device_id`、遗留 `github_token`。

注意：手机端把同步基线存在 DataStore 字符串 `sync_base`，不是独立的 `sync-base.json` 文件；桌面 / GUI / TUI 才使用 `sync-base.json`。改跨端文档或工具时要区分。

### 4.3 快照 wire v2

```json
{
  "version": 2,
  "revision": 12,
  "exportedAt": 1764000000000,
  "deviceId": "uuid",
  "lastSyncAt": 1764000000000,
  "categories": [
    { "name": "工作", "color": "#5AA9E6", "sortOrder": 0, "updatedAt": 1764000000000, "deletedAt": null }
  ],
  "tasks": [
    { "uid": "uuid", "parentUid": null, "title": "任务", "category": "工作",
      "priority": 2, "done": false, "completedAt": null,
      "createdAt": 1764000000000, "updatedAt": 1764000000000,
      "deletedAt": null, "sortOrder": 0, "depth": 0 }
  ]
}
```

- camelCase，扁平 `tasks[] + parentUid`，非嵌套
- v1 兼容读把本地 `id` 映射为 `v1-<id>`，时间戳记 0，避免被当成新数据覆盖远端
- 删除写墓碑，不物理删；`applyMerged()` 按 uid 复用本地行号，并回填 `parentUid` → Room `parentId`

### 4.4 合并语义（`SyncMerge.kt`）

不可随意改，均为历史 bug 修复点：

1. 并集：单边独有记录一律保留
2. 本地相对 base 改过且远程与 base 一致 → 整条以本地为准
3. 单字段一边变取该边；两边改不同字段都保留；同字段冲突取 `effectiveTime`（删除 > 编辑 > 完成 > 创建）较新者，时间相同或缺失 → **远程优先**
4. 删除 vs 编辑：删除在同步之后且对方未编辑才生效；双方都动需删除严格更晚；时间相同 / 缺失保留活记录
5. `isStaleRemote()` 用 schemaVersion / revision / exportedAt（10 分钟时钟容差）识别旧副本
6. `fingerprint()` 只编码内容，相同则不上传，保证幂等
7. 分类“是否用户编辑过”用 `visibleContent()`（名称 / 颜色 / 排序）判断，**刻意排除 `updatedAt`**，避免同步回写导致已删分类复活

### 4.5 WebDAV

- 不使用 PROPFIND；用 PUT 探针文件 `doit-connection-test.json` 验证真实上传能力
- 候选目录按顺序尝试：用户原路径 → 去掉 `webdav/dav/web-dav` 尾段 → 域名根 `/doit/` → 域名根；成功的 `workingUrl` 回填设置
- Basic Auth；connect 8s / read 10s；`GET` 404 / 410 视为远端无文件，其它非 2xx 一律报错
- 错误按状态码翻译成中文，不泄漏 URL / 凭据

## 5. 关键业务口径

- **今日待办**：只算顶级任务；未完成或今日完成才显示；`todayTotal/todayDone` 是右上角进度环口径，与分类筛选解耦；副标题「还有 N 件事」按当前可见列表。`sortTopByPriority()` 只在 `visible` 上做稳定降序（4→0），子任务顺序与引用不动；开关写 `AppSettings.sortByPriority`（DataStore），不进同步快照
- **云同步按钮三态**：未同步=橘黄云 + 白上箭头；同步中=绿云 + 白上箭头往复动画 + 旋转进度环（禁用点击）；已同步=绿云 + 白对钩。`CloudSyncVisual` 的橘色取自 `ActionColors.Priority`，与桌面端 `--warn` 同语义
- **完成规则**：叶子任务才可勾选 / 右滑完成；所有有效子任务完成 → 父任务逐级 `recalcUp()` 自动完成；完成写 `CompletionLog`。删除子任务后同样要 `recalcUp()`（墓碑已被 `childrenOf` 排除），同步 `applyMerged()` 末尾跑 `recalcAllParents()`（不写日志）自愈其它端来的脏完成态
- **优先级**：`0 不设 / 1 低 #4FBF9E / 2 普通 #4A86D6 / 3 高 #E08A34 / 4 紧急 #DE5B55`；选择器从高到低 + 末尾「不设」；新建默认普通(2)；0 不渲染标签
- **分类颜色**：四端统一 8 色 `#34C7A8 / #5AA9E6 / #F2A65A / #A78BFA / #22B8CF / #F08A8A / #AACB5E / #7C96B0`；带 `#` 大写存储；`parseHex()` 兼容旧值
- **编辑规则**：顶级父任务编辑 = 标题 + 分类；子任务编辑 = 标题 + 优先级，**分类跟随父任务**；已完成任务不提供编辑入口
- **统计**：本周（周一~周日）/ 本月 / 近一年（含今天），三张图共用同一 `rangeStartEnd()`，全部按本地时区

## 6. 本机构建与模拟器环境

本机工具链不在 `~/Library/Android/sdk`：

```text
JAVA_HOME=/Users/nbn/WorkBuddy/安卓应用/待办应用/.toolchain/jdk-17.0.21+9/Contents/Home
SDK=/Users/nbn/WorkBuddy/安卓应用/待办应用/.toolchain/sdk
ADB=$SDK/platform-tools/adb
```

`local.properties` 已指向该 SDK，不入库。

常用命令：

```bash
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
./gradlew assembleRelease
$ADB install -r doit/build/outputs/apk/debug/doit-1.0.17-debug.apk
```

环境陷阱：

- `~/.gradle/gradle.properties` 有死代理；session 环境变量代理也可能是死值。构建前临时清空 HTTP(S)_PROXY / ALL_PROXY，结束后恢复文件。缓存预热后可 `--offline --no-configuration-cache`
- 模拟器启动必须清代理，并设置 `ANDROID_AVD_HOME` / `ANDROID_USER_HOME` 指向工作区 `.emu`，避开沙箱对 `~/.android` 的拦截
- AVD `quan`（Android 15 / arm64 / 1080×2400）位于 `.emu/avd/`；启动前清 `*.lock` 和 `read-snapshot.txt`
- 不要用 `adb getprop sys.boot_completed`（adb 1.0.41 报 unknown），看 emulator 日志 `Boot completed`
- `input text` 在 Compose 输入框丢字符（尤其 `/`），WebDAV 地址建议用 `adb reverse` + `http://127.0.0.1:8899/webdav` 做本地 mock
- 有 ModalBottomSheet 时 `uiautomator dump` 可能返回 null root；滑动起点贴边常被吞，用中段起终点

## 7. 发版纪律

- **只有用户明确要求「发版 / 发布」时才能执行。** 日常改动只 `commit + push main`；CI 仅在 `v*` tag 时构建发布
- `./release.sh [patch|minor|major|1.2.3] [--dry-run|--force]`
- 脚本从最近 tag 自增，校验工作区干净和远端无同名 tag，打 annotated tag 并推送；版本号由 Gradle 从 tag 推导：`versionCode = major*10000 + minor*100 + patch`
- 签名优先级：`keystore.properties`（项目根或 `~/.doit-keystore/`）→ 环境变量 `DOIT_KEYSTORE*` → 无则 debug 签名警告。密钥材料绝不入库
- CI：`.github/workflows/android.yml`，单测 → Lint → assembleDebug / assembleRelease → 发布 Release
- 当前最新 tag：`v1.0.17`

## 8. 接手红线清单

1. 命名统一 `doit`，不要引入旧名
2. 四端 wire 契约、合并语义、色板是硬约束，改一端必须评估四端
3. 删除必须走墓碑，禁止日常业务物理删除
4. 不要破坏 `SyncMerge` 的本地优先 / 平局远程优先 / 分类内容指纹 / 陈旧远端短路 / fingerprint 幂等
5. 统计与今日口径要同步更新 ViewModel 与文档，并补测试
6. 已完成任务无编辑入口；子任务分类跟随父任务
7. 构建 / 模拟器前处理代理与 toolchain，结束后恢复配置
8. 不要重新引入 GitHub Token 输入框；仓库已 public，匿名访问 Releases 即可
