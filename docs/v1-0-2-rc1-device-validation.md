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

### P0 结构层根治真机验证（2026-11-06 夜 · 通过，已合 main）

**设备**：SM-S9280（Galaxy S24 Ultra）· Android 16 · arm64 · 1080×2340 · 覆盖安装自正式 v1.0.2（同 debug 签名，书架 7 本与知识卡数据保留）。
**被测包**：分支 `fix/p0-backdrop-isolation`（`c60de33`）重新构建的 `app-universal-debug.apk`（versionCode 5 / 1.0.2）。
**构建来源教训**：20:20 构建的旧 debug 包实为 main 工作树产物（reflog 证实分支只存在 5 秒），forceFallback 版与隔离版在矩阵①上都会存活，装错包测不出差异——21:06 checkout 分支后重建（21:12 产物）才装。教训：**验证包必须确认构建时间晚于 checkout 时刻**。
**驱动**：adb 全自动（uiautomator 定位 + screencap 逐屏核验），脚本 `D:/wb-tc/device-test/p0_matrix.py`；判定 pid 不变 + logcat 无 FATAL/SIGSEGV/tombstone。

| # | 场景 | 结果 |
| --- | --- | --- |
| ① | 液态玻璃下打开详情 ×3 | **存活 3/3**（pid 16055 / 17349 / 18183，截图确认两枚按钮玻璃管线正常渲染）✓ |
| ② | 进出详情页各一次 | 存活 ✓（退出后进程不变 + 重进无崩溃） |
| ③ | 删除确认弹窗 | 存活 ✓（弹窗文案/按钮渲染正常，点取消后 pid 不变，未真删） |
| ④ | 添加关系 Sheet | 存活 ✓（「选择关系类型」9 个类型 chip 渲染正常） |
| ⑤ | 邻页抽查（统计 / 设置 / 首页） | 存活 ✓ |
| ⑥ | 默认主题重复①② | 存活 ✓（pid 25458 / 25911） |
| ⑦ | 彩蛋主题 | **跳过**：显示与外观页「？」主题点击与长按均无响应（疑似未解锁彩蛋，与本次改动无关） |
| 收尾 | 切回液态玻璃复开详情 | 存活 ✓（pid 30595） |

**累计 8 个独立进程、0 崩溃**。隔离方案证实有效，merge 提交 `7a47821` 已合入 main 并推送，CI（run 42227572951）通过。

结论：知识详情页玻璃按钮不再经过 forceFallback 降级，重新走液态玻璃管线（仅失去背景折射，本页纯色背景视觉几乎无差）；线上 v1.0.2 的 forceFallback 行为将在下个版本被此结构层修复取代。

**操作注记**：覆盖安装同 versionCode 会重置新手向导（多页，需点完「下一步」）；统计页是从「我的」进入的独立页面，栈顶时底部栏点击无效需先返回；底部五栏为纯图标 tab（text/content-desc 全空），坐标 x=284/410/536/662/792、y≈2180。

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

## 后续待办（2026-11-06 晚登记）

| 项 | 状态 | 说明 |
| --- | --- | --- |
| P0 结构层根治的真机验证 | **已完成（2026-11-06 夜）** | 7 项矩阵 ①-⑥ 全过（⑦ 彩蛋未解锁跳过），累计 8 进程 0 崩溃；已合 main（`7a47821`），见上文「P0 结构层根治真机验证」 |
| 全量 150 条 connectedDebugAndroidTest | **已完成（2026-11-06 深夜）** | 分 3 批跑：执行 111 条 / 99 通过 / 12 失败（全部环境或测试代码缺陷，与 P0 零交集）/ 39 条被向导页崩溃中断。详见下节 |
| 站点法律页本地化 | 未做 | `docs/privacy.html` / `docs/terms.html`（GitHub Pages 单页 + `lumi-nav.js`）仍是纯简体；应用内 `assets/html/` 的 7 语言版本已完成 |
| 法律译文审校 | 未做 | `privacy-{en,ja,ko,zh-TW,zh-HK,zh-MO}.html` 与 `terms-*` 为本轮新译，未经人工/法务确认 |
| 仪器测试串跑挂起排查 | **已完成（2026-11-07）** | 根因=Android 16 真机窗口 Transition 随机竞态（androidx.test EmptyActivity 舞步正常 0.2s、楔死时每步 ~55s 或永不恢复），与测试间状态污染无关；修复=6 个 compose 测试类迁 Robolectric + Orchestrator 进程隔离 + androidx.test 栈升级，详见下节「串跑挂死根治与 compose 测试迁移」 |
| 首跑基线新失败分诊（2026-11-07） | 未做 | 全量首跑完成后的 4 条非既有基线失败：`AnnotationEditTransactionTest`（expected 2 was 1，Room 事务计数，**疑似真实并发缺陷**）、`ReaderThemeSuiteCodecInstrumentedTest`（codec 往返不一致）、`EpubParserLinkTest` ×2（TOC 链接映射/锚点，设备与预期结构不符）、`EpubHighlightOverlay.roundedOverlaySurvivesReaderReconfiguration`（高亮间隙负值，与既有度量基线同族） |
| `StatisticsScreen.kt` 玻璃用法复核 | 未做 | 该页在 rc1 崩溃矩阵中存活，但未单独验证结构安全性 |

## 全量仪器测试（2026-11-06 深夜 · 分 3 批，设备 SM-S9280）

基于合并后 main（`7a47821`）。首轮一次性跑全量在 84 分钟时停滞——`EpubDirectNavigationHostInstrumentedTest.preparedNextViewReplacesActiveOnlyWhenPromoted` 之后 1 小时零产出，UTP 未收尾，遂终止改分批。

| 批次 | 范围 | 条数 | 结果 |
| --- | --- | --- | --- |
| 1 | 非 reader 包（data / util / widget / ui.settings / welcome / components） | 77 | 执行 38（35 通过 / 3 失败），随后**进程崩溃**中断，39 条未执行 |
| 2 | `ui.reader.**`（排除卡死类） | 72 | 执行 72，63 通过 / **9 失败** |
| 3 | `EpubDirectNavigationHostInstrumentedTest` 单独跑 | 1 | **通过**（1m27s） |

合计 150 条：执行 111、通过 99、失败 12、未执行 39。

### 12 条失败清单与定性（全部与 P0 玻璃改动零交集）

| 类/用例 | 报错 | 定性 |
| --- | --- | --- |
| `EpubFixedLayoutInsetsInstrumentedTest.fixedLayoutTextPageIsScaledIntoInsetBox` | `expected:<213.333>` vs `<219.961>` | 密度断言，已知环境基线 |
| `DataStoreManagerIconStyleTest.initializationError`（`AccentColorTest` 同） | `Method tearDown() should be void` | **测试代码缺陷，已修复**：`@After`/`@Before` 表达式体推断返回 `Preferences`（非 void）；改块体 void 后两类真机全过 |
| `WelcomeScreenInstrumentedTest.introductionUsesUnifiedVersionTwoDesign` | 失败后 `Process crashed`；日志 14000+ 行全是 `setRequestedFrameRate frameRate=NaN` + `dispatchDraw` 刷屏 | **既有问题，已修复**：根因是测试未传 motionPreference（默认 STANDARD）→ 向导页渐变背景 3 路 `infiniteRepeatable`（18s/23s/29s）永不结束；测试注入 `MotionPreference.REDUCED` 后该用例单独跑通过（59s），NaN 刷屏降至约 224 行（残余来自系统 UI） |
| `ReaderPagingLayoutInstrumentedTest.chapterTitleParagraphIgnoresExplicitTextAlignment` | `expected:<ALIGN_NORMAL>` vs `<ALIGN_CENTER>` | 文本度量/对齐，环境相关 |
| `ReaderSelectionMagnifierInstrumentedTest.imageOnlySelectionDoesNotShowMagnifier` | `expected:<0>` vs `<1>` | 同上 |
| `RoundedHighlightRenderingInstrumentedTest` ×3 | 字间 `largestGap=38`、`positions must be monotonic`、justified 坐标 | 像素/几何断言，与已知 `EpubHighlightOverlay` 同类 |
| `VerticalTextLayouterInstrumentedTest` ×2 | 垂直几何、Latin cluster 宽度 | 字体度量差异 |
| `ReaderParagraphFormatterTest.existingWhitespaceIsSupplementedToRequestedIndent` | 缩进宽度 | 同上 |
| `EpubVerticalPagingInstrumentedTest.verticalTurnShowsIncomingPageAndPromotesItOnce` | 翻页转场 | 同上 |

### 结论与后续

- 这些用例此前**从未在真机跑过**（CI 只跑 Robolectric 分片），无基线可比，登记为「首次真机基线的待观察项」；密度/字体度量类断言大概率需要按设备密度做容差，或改断言为范围判断。
- 两处**测试代码/被测页的既有缺陷**已于 2026-11-07 修复：`DataStoreManager*Test` 的 `@After`/`@Before` 改块体 void；`WelcomeScreenInstrumentedTest` 注入 `MotionPreference.REDUCED` 使渐变背景走静态分支（原崩溃用例复跑通过）。注意：**真机向导页本身仍带无限动画**（REDUCED 才静态化），若真机上向导页有卡顿/耗电疑虑，需另行评估产品侧对「减弱动态效果」系统设置的自适应。
- **整批不可一次跑完（2026-11-06 结论，已于 2026-11-07 根治，见下节）**：向导页 NaN 崩溃消除后，复测发现向导类用例串跑仍会无声挂死；当时的「状态污染/按方法分批」定性后来被证明不成立——真正根因是真机窗口 Transition 竞态，与用例顺序无关。

## 串跑挂死根治与 compose 测试迁移（2026-11-07）

### 根因链（四轮排除实验后收敛）

1. 启用 AndroidX Test Orchestrator + `clearPackageData`（每用例独立进程）后**挂死依旧复现**——排除「跨用例状态污染」，原定性被推翻。
2. 升级 androidx.test 全家桶（`ext:junit 1.1.5→1.3.0`、`espresso 3.5.1→3.7.0`、新增 `runner/core 1.7.0`）后**同位置复现**——排除版本陈旧。
3. 系统三把动画尺归零（`window/transition/animator = 0.0`）后**仍复现**——排除动画时长。
4. 决定性对照：每个 compose 用例都会执行 androidx.test 的标准「EmptyActivity 窗口舞步」（启动 `InstrumentationActivityInvoker$EmptyActivity` 盖住 compose 测试活动 → PAUSED/不可见 → 恢复）。同一舞步在 supportPage 用例上 **0.23s 完成**，在 updateWalkthrough 用例上**楔死**（12 分钟静默后以每操作 ~55s 的速度爬完 772s，或永不恢复）。**结论：SM-S9280 / Android 16 上窗口 Transition 恢复存在随机竞态**，历史上「串跑第 3 用例挂死 23 分钟」「EpubDirectNavigation 串跑挂死」均为同一竞态的偶然表现（挂死时窗口在前台、全线程 sleeping、无 ANR，与此机制完全吻合）。

### 修复措施

- **6 个 compose 测试类自 androidTest 迁至 `app/src/test`（Robolectric，`sdk 35` + NATIVE graphics）**：`WelcomeScreenTest`（原 `WelcomeScreenInstrumentedTest`）、`BookDetailRowComposeTest`、`LiquidGlassButtonInteractionTest`、`AppIconStyleScreenTest`、`SettingsRowsTest`、`TocReturnButtonTest`。结构性/文本/交互断言在 Robolectric 等价，且**首次可进 CI 常跑**（仪器测试 CI 从不执行）；真机 androidTest 从此不含任何 ActivityScenario 使用者，竞态入口清零。
- **Orchestrator + clearPackageData + androidx.test 升级保留**：`app/build.gradle.kts`（`execution = ANDROIDX_TEST_ORCHESTRATOR`、`testInstrumentationRunnerArguments["clearPackageData"]="true"`、`androidTestUtil androidx.test:orchestrator:1.6.1`）。

### 迁移后全量真机回归（SM-S9280，2026-11-07）

`connectedDebugAndroidTest` 一次性跑完 **135 条 / 6m44s**（历史：150 条、84 分钟停滞、需 3 批）。执行 134、**15 失败**：

- **11 条既有环境基线**（与上节 12 条清单重叠，度量/几何/密度类）：`EpubFixedLayoutInsets` ×1、`ReaderPagingLayout` ×1、`ReaderSelectionMagnifier` ×1、`RoundedHighlightRendering` ×3、`VerticalTextLayouter` ×2、`ReaderParagraphFormatter` ×1、`EpubVerticalPaging` ×1、`EpubHighlightOverlay.roundedOverlaySurvivesReaderReconfiguration` ×1。
- **4 条首跑基线新失败**（上一轮 39 条未执行域的首次真机运行，登记待分诊）：`AnnotationEditTransactionTest`（Room 事务计数 2 vs 1，疑似真实并发缺陷，优先排查）、`ReaderThemeSuiteCodecInstrumentedTest`（codec 往返）、`EpubParserLinkTest` ×2（TOC 链接映射与锚点）。

验证注意：迁移后的 JVM 测试本地命令 `gradle :app:testDebugUnitTest --tests "…"`；真机验证必须按类/包整跑（`class#method` 无法复现串跑场景，且 Orchestrator 下每用例独立进程本就无串跑）。
