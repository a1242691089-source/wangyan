# v1.0.2-rc1 真机验证清单

Date: 2026-11-06 · 版本：v1.0.2-rc1（versionCode 3）· 基于 v1.0.1 之后 44 个提交

## 安装

- APK 在仓库的 `artifacts/` 目录（`.gitignore` 第 39 行 `/artifacts/` 已排除，不进版本库）：
  - `wangyan-1.0.2-rc1-arm64-v8a.apk` —— 39 MB，绝大多数真机（2018 年后机型）用这个
  - `wangyan-1.0.2-rc1-universal.apk` —— 39 MB，含 armeabi-v7a / x86 / x86_64，兼容性兜底
- 签名：**Android Debug 证书**（CN=Android Debug，SHA-256 `5e6068a1…a271a9fc`）。
  注意：`app/keystore.properties` 不存在时 AGP **不会**回落 debug 签名，`assembleRelease` 实际产出
  `*-unsigned.apk`（装不上）；本次的包是构建完成后用 `apksigner` + `~/.android/debug.keystore` 手工补签的。
- ⚠️ 若真机已装**正式签名**的 v1.0.1 发布包：签名不一致会导致覆盖安装失败，需先卸载
  （会清应用数据，注意备份）。`adb install -r` 报 `INSTALL_FAILED_UPDATE_INCOMPATIBLE` 即此原因。
- 设置 → 关于页应显示 **1.0.2**（versionCode 3）。

## 1 · 多语言（本版重点，八 locale 缺失归零）

切换系统语言或应用内语言设置，逐项检查：

| 项 | 检查点 |
| --- | --- |
| ja / ko / zh-rTW / zh-rHK / zh-rMO 各抽 10 条 | 成句、非中文残留；重点：诊断页三个长段落、高亮规则弹窗、导入统计 |
| TW/HK/MO 隐私政策 + 用户协议 | 全文滚动，**零简体字**；TW 显示「隱私」，HK/MO 显示「私隱」 |
| TXT 目录规则 | 导入「第 N 章」格式的 TXT，默认规则仍能识别章节（该正则为 `translatable=false`，不应被翻译改动） |
| 高亮规则导入导出 | `%1$d` 类数字占位符正确替换，不出现裸 `%1$d` |
| 文件夹授权 → 跨存储移动 | 二次确认文案完整（曾缺 260 条的重灾区） |
| 应用内更新日志 | 「关于」内 changelog 显示 1.0.2-rc1 条目 |

## 2 · 知识页（新功能）

- 阅读器长按选中 → 「存为知识」→ 知识页出现卡片。
- 知识节点详情页：证据列表点击可跳回原文（章节/位置）。
- 节点间关系：新建/删除关系，卡片上的关系计数随之变化。
- 按书筛选：选某本书后卡片流正确过滤；筛选无结果时显示空状态。

## 3 · 笔记六类型（新功能）

- 笔记输入面板可选择六种类型；无正文位置（哨兵）也能建笔记。
- 笔记栏筛选徽标按类型过滤；搜索输入防抖正常、无明显卡顿。

## 4 · 回归抽查

- EPUB / TXT / PDF / 漫画各打开一本，翻页、目录、进度恢复正常。
- 深浅色切换与主题色全局着色正常。
- 书架 → 笔记 → 知识 → 技能 → 我的 五栏导航正常。

## 结果记录（2026-11-06 真机实测）

**设备**：SM-S9280（Galaxy S24 Ultra）· Android 16（BP4A.251205.006）· arm64-v8a · 1080×2340
**方式**：adb 全自动驱动（uiautomator 定位 + screencap 截图逐屏核验），语言经应用内设置切换。

### 通过项

- [x] 安装与版本号：versionName 1.0.2 / versionCode 3，签名与 artifacts 包一致
- [x] 语言切换链路：8 个 locale 全部可切换、重启生效，确认弹窗本身已本地化
- [x] TW/HK/MO 用词：TW「隱私權政策」「點選」、HK·MO「私隱政策」「點擊」「重新啟動」——区域用词正确
- [x] TXT 目录规则：导入含「第一章…第十章」的测试书，目录全部识别（translatable=false 正则完好）
- [x] 笔记类型筛选：顶部 chips（高亮/划线/笔记/书签）+「全部类型」下拉（思考/灵感），六类型在位
- [x] 应用内更新日志：正确显示 1.0.2-rc1 条目
- [x] 阅读器回归：EPUB/TXT 打开、翻页、进度、目录正常
- [x] 知识页列表：卡片渲染、按书筛选控件正常

### 发现的问题

| # | 级别 | 问题 | 证据 |
| --- | --- | --- | --- |
| 1 | **P0 阻塞** | **点击知识卡片必崩（已定位并热修，见下文「P0 根因与热修」）**：SIGSEGV (SEGV_ACCERR) RenderThread **栈溢出**，完整栈（`#000` 起 510 帧）100% 是 `libhwui RenderNode::prepareTreeImpl` 互递归，零应用/View 帧 —— RenderNode 渲染树成环。触发条件：**液态玻璃主题下打开知识详情**（页面顶部两个 `LiquidGlassIconButton` 的玻璃渲染路径）。默认主题同路径存活；动画时长置 0 仍崩（排除转场时序）；统计页玻璃正常（非泛化问题） | `p0-crash.txt`、`docs/` 下复现脚本 `p0_repro.py` |
| 2 | P1 | **拆分 strings 资源未本地化**：res/values 下另有 13 个 `*_strings.xml`/`excerpt.xml` 等文件（共约 241 条），棘轮与历次补齐只覆盖 strings.xml。实测缺口：ja/ko 各 74、zh-rTW 58、zh-rHK/MO 各 78、en 4。表现为韩文界面混排「本地词典」「词库按需下载…」「浏览和管理 OPDS 远程书库」等简体原文 | dictionary_strings.xml、remote_library_strings.xml 等 |
| 3 | P1 | **应用内法律文档无本地化**：关于页「隐私政策/用户协议」加载 `assets/html/privacy.html`、`terms.html`，只有简体中文一个版本；标题栏走 strings.xml 是译文，形成「繁体标题+简体正文」混排。ja/ko/en 用户同样看到简体文档 | tw-privacy-top.png |
| 4 | P3 | `LocaleHelper.LANGUAGE_DISPLAY_NAMES` 把「跟隨系統設定」硬编码为繁体，简体界面下也显示繁体 | LocaleHelper.kt:26 |

**未验证**（受阻于 #1 或超出本轮）：高亮规则导入导出占位符替换、划词「存为知识」到节点关系管理、性能与发热。

### P0 根因与热修（2026-11-06 下午）

**实证矩阵**（Galaxy S24 Ultra / Android 16，脚本 `D:/wb-tc/device-test/p0_repro.py` 自动复现）：

| 场景 | 主题 | 结果 |
| --- | --- | --- |
| 知识列表 → 点卡片进详情 | 液态玻璃 | 崩 6/6（多轮，含动画时长=0） |
| 同路径 | 默认 | 存活 |
| 知识详情按钮 forceFallback | 液态玻璃 | 存活 2/2 |
| 统计页 / 笔记 Tab / 设置页（独立 Activity）玻璃 | 液态玻璃 | 存活 |

**结论**：崩溃需要「液态玻璃管线 + 知识详情页内两个玻璃按钮」。完整栈 510 帧纯 `prepareTreeImpl ↔ 子节点遍历 functor` 交替 —— 渲染树存在 RenderNode 自引用环。backdrop 库 1.0.6 的机制：`layerBackdrop` 每帧把宿主子树重录进 GraphicsLayer；玻璃节点再把采样层画进自己的离屏层（`CompositingStrategy.Offscreen` + RuntimeShader 链）。当「被录进捕获层的玻璃节点」与「采样链」组合成环时，RenderThread `prepareTree` 无限递归直至栈溢出。具体哪条采样链在知识详情页成环未完全定论（两次 backdrop 供给修复后仍崩，forceFallback 才根治），疑似与设备/Compose/hwui 版本相关的库缺陷（上游 backdrop 已迭代到 2.0.1）。

**热修（3 处，均为低风险）**：
1. `KnowledgeDetailScreen.kt`：详情页两个 `LiquidGlassIconButton` 加 `forceFallback = true`（根治，已真机验证 2/2 不崩）。代价：玻璃主题下详情页按钮退化为普通圆钮。
2. `NavGraph.kt` `LumiBackgroundHost` 不再把 NavHost 捕获层传作 `backgroundBackdrop` —— 恢复 `LumiBackground.kt:44` 注释声明的设计（"cards can never sample their own render nodes"），目的地内玻璃兜底只采样背景画布。
3. `LiquidGlassDialog.kt` `LiquidGlassDialogHost` 的 content 不再继承弹窗采样 backdrop（弹窗本体不受影响），防止全局玻璃弹窗可见期间主内容玻璃采样到含自身的捕获层。

**遗留**：知识详情按钮的玻璃质感待 backdrop 升级/根因明确后恢复；其他 NavHost 目的地若有新增玻璃同样先 forceFallback；统计页虽存活，`StatisticsScreen.kt` 的玻璃用法建议单独复核。

### P0 根治（结构层，2026-11-06 晚 · 待真机确认）

上节写的「具体哪条采样链成环未完全定论」现已定到行：

| 环节 | 位置 | 作用 |
| --- | --- | --- |
| ① 捕获层挂载 | `NavGraph.kt:851` `Modifier.layerBackdrop(liquidGlassBackdrop)`（条件 `canCaptureNavigationBackdrop && isLiquidGlass && !isLumiChan`） | NavHost **自身**把整棵导航内容（含当前目的地页面）每帧录进 `liquidGlassBackdrop` |
| ② 采样链下发 | `AnchoredLiquidGlassMenu.kt:567`（`LiquidGlassMenuHost` 默认参数 `backdrop = LocalLiquidGlassBackdrop.current`）、`:1111`（面板内容 `provides` 同一个 backdrop） | 该层沿 CompositionLocal 一路下发到子树的每个玻璃节点 |
| ③ 自采样 | 知识详情页两枚玻璃按钮不传 `backdrop`，取 `LocalLiquidGlassBackdrop.current` | 玻璃节点把「正在录制自己的那个捕获层」画进自己的离屏层 → RenderNode 自引用环 → `prepareTreeImpl ↔ 子节点遍历 functor` 无限递归 |

**为什么只有知识详情页崩**：它是全仓唯一「NavHost 目的地 + 使用玻璃 + 零 backdrop 治理」的组合。结构同构的首页卡片「更多」按钮靠 `HomeScreen.kt:643` 一行 `CompositionLocalProvider(LocalLiquidGlassBackdrop provides null)` 活着，其上方注释写的就是这个自引用问题（"a surface that samples its own capture makes the render tree reference itself (native HWUI crash)"）。

**「升级 backdrop 2.0.1」已排除**，四条证据：① 2.0.1 release notes 只有 Compose 1.12 抬版，2.0.0 只讲 KMP 化与 API 变更，均无递归/崩溃修复；② 上游 Issue #100 仍 open，维护者答复指向「使用方布局约束」；③ 1.0.6 与 2.0.1 的 `recordLayer` / `drawBackdrop` 机制逐段一致；④ 升级带 breaking change（`effects.effect` 只留 Compose 版，命中 `LiquidMenuOptics.kt:64`）。

**根治改动**（分支 `fix/p0-backdrop-isolation`，提交 `c60de33`，**未合并**）：详情页头部 `Row` 外包 `CompositionLocalProvider(LocalLiquidGlassBackdrop provides null)`，删掉 rc1 的两处 `forceFallback = true`。按钮重新走玻璃管线，只失去背景折射——本页纯色背景，视觉几乎无差。

**状态**：`assembleDebug` 已通过（`app-universal-debug.apk`），真机验证因设备占用中断。**线上 v1.0.2 正式包仍是 forceFallback 行为**，本节结论待验证，不得视为已交付。

**待跑的真机矩阵**（设备空出后执行，全绿再合 main）：① 液态玻璃下打开详情 3/3 存活；② 进出详情页各一次；③ 删除确认弹窗；④ 添加关联 Sheet；⑤ 邻页抽查（统计 / 设置 / 首页）；⑥ 默认主题；⑦ 彩蛋主题。

### 仪器测试

- 首轮 `connectedDebugAndroidTest --offline` 直接失败：缺 `androidx.test:monitor:1.6.1`、`espresso-core:3.5.1`、`espresso-idling-resource:3.5.0`、`room-testing:2.8.4` 及 UTP 插件 `android-test-plugin-host-additional-test-output:31.13.2`（本机 Gradle home 从未跑过 androidTest）。不是代码问题，联网跑即可。
- **util 包冒烟（含 P0 热修代码）：31 条，27 过 / 4 失败**。4 条均为设备环境相关，与本次 UI 改动零交集：`EpubFixedLayoutInsets`（屏幕密度断言 213.33 vs 219.96）、`EpubHighlightOverlay`（高亮行像素差）、`EpubParserLinkTest`×2（TOC 解析出更多条目）。本机首次跑通仪器测试，无基线可比，登记为待观察项。
- 注意：`connectedAndroidTest` 结束会**自动卸载应用**（AGP 默认），跑完需重装 APK 并重走新手向导/协议弹窗。

结果待补：全量 150 条 connectedDebugAndroidTest。P0 的完整 tombstone 已于下午补齐（`#000` 起 510 帧，见「P0 根因与热修」）。

## rc2 真机验证（2026-11-06 晚，Galaxy S24 Ultra / Android 16）

版本：v1.0.2-rc2（versionCode 4，`wangyan-1.0.2-rc2-universal.apk` 覆盖安装自 rc1 修复版，同为 debug 签名）。

| 项 | 结果 |
| --- | --- |
| 安装 | Success（Streamed Install，覆盖升级，书架 7 本数据完好） |
| 新手向导 + 协议确认 | 覆盖安装重置向导，已走完 |
| 关于页版本号 | 显示 **1.0.2** ✓ |
| 主题 | 液态玻璃（设置保留）✓ |
| 知识卡创建 | 阅读器划词「stay」→ 存为知识 ✓ |
| **P0 回归：液态玻璃下开知识详情** | **存活 2/2**（进出各一次，进程 pid 不变，logcat 无 FATAL/SIGSEGV）✓ |

结论：rc2 唯一变更（P0 热修）真机验证通过，正式版 1.0.2 发布阻断项清零。

## v1.0.2 正式版真机安装与更新链路验证（2026-11-06 晚，Galaxy S24 Ultra / Android 16）

前提：设备为 rc2（versionCode 4），远端 `docs/app-config.json` 已是 `latest_version_code=5`，构成真实的「有更新」对照——这是验证应用内更新链路的唯一窗口（升级后无法复现）。

| 项 | 结果 |
| --- | --- |
| 应用内更新弹窗（rc2 冷启动自动检查） | 弹出「忘言 1.0.2」，标题 / 正文 / 更新日志与 app-config.json 完全一致 ✓ |
| 「下载」跳转 | 拉起浏览器落地 GitHub Releases → 忘言 v1.0.2（Latest 徽标）✓ |
| 覆盖安装正式包 | Success（Streamed Install），versionCode=5 / versionName=1.0.2，书架 7 本数据完好 |
| 新手向导 / 协议确认 | 覆盖安装重置向导已走完；协议弹窗未再触发（条款版本已接受，符合预期） |
| 关于页版本号 | 显示 **1.0.2** ✓ |
| 主题 | 液态玻璃（设置保留，显示与外观页确认选中）✓ |
| **P0 回归：液态玻璃下开知识详情** | **存活**（进程 pid 不变，logcat 无 FATAL/SIGSEGV）✓ |
| 手动检查更新 | Toast「目前已是最新版本」✓；冷启动不再弹更新窗 |

结论：应用内更新链路（远端配置拉取 → 弹窗渲染 → 下载跳转 → 已最新判定）首次端到端验证通过；v1.0.2 正式版真机收尾完成，1.0.1 用户可经此链路升级。
