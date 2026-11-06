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
| 1 | **P0 阻塞** | **点击知识卡片必崩**：SIGSEGV (SEGV_ACCERR) RenderThread **栈溢出**，512 帧全部是 `libhwui RenderNode::prepareTreeImpl` 互递归（渲染树过深）。冷启动直接进知识页点卡片 2/2 稳定复现，无 Java 堆栈。知识详情页完全不可用 | crash buffer：`stack pointer is close to top of stack; likely stack overflow` |
| 2 | P1 | **拆分 strings 资源未本地化**：res/values 下另有 13 个 `*_strings.xml`/`excerpt.xml` 等文件（共约 241 条），棘轮与历次补齐只覆盖 strings.xml。实测缺口：ja/ko 各 74、zh-rTW 58、zh-rHK/MO 各 78、en 4。表现为韩文界面混排「本地词典」「词库按需下载…」「浏览和管理 OPDS 远程书库」等简体原文 | dictionary_strings.xml、remote_library_strings.xml 等 |
| 3 | P1 | **应用内法律文档无本地化**：关于页「隐私政策/用户协议」加载 `assets/html/privacy.html`、`terms.html`，只有简体中文一个版本；标题栏走 strings.xml 是译文，形成「繁体标题+简体正文」混排。ja/ko/en 用户同样看到简体文档 | tw-privacy-top.png |
| 4 | P3 | `LocaleHelper.LANGUAGE_DISPLAY_NAMES` 把「跟隨系統設定」硬编码为繁体，简体界面下也显示繁体 | LocaleHelper.kt:26 |

**未验证**（受阻于 #1 或超出本轮）：高亮规则导入导出占位符替换、划词「存为知识」到节点关系管理、性能与发热。

### 仪器测试

- 首轮 `connectedDebugAndroidTest --offline` 直接失败：`androidx.test:monitor:1.6.1`、`espresso-core:3.5.1`、`espresso-idling-resource:3.5.0`、`room-testing:2.8.4` 不在离线缓存（本机 Gradle home 从未跑过 androidTest，JVM 单测不需要这几个包）。这不是代码问题，去掉 `--offline` 联网解析即可。
- 联网后 `:app:assembleDebugAndroidTest` 在 2m51s 内成功，产出 `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`（1.4 MB）。
- 执行阶段受阻：设备在编译期间掉线（`adb devices` 空，USB 上已无三星设备），connected 测试与 P0 复现都跑不了。复现脚本已备好：`D:/wb-tc/device-test/p0_repro.py`（清 logcat → 流式抓全量 → 冷启动 → 知识 Tab → 点首张卡片 → 判 `pidof` 存活 → 抽取含 `#000` 起始帧的 crash 段）。

结果待补：util 包冒烟（i18n 守门所在分片）与全量 150 条 connectedDebugAndroidTest；以及 P0 的完整 tombstone 头部帧（现有 512 帧里只留存了 `#452`–`#512`，`#0`–`#451` 被 logcat 环形缓冲冲掉，无法判定递归起点是应用侧还是框架侧的 RenderNode 自引用）。
