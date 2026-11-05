# 方案 V1.0 vs 代码现状差距分析

> 对照对象：《忘言_Android端产品与技术开发方案_V1.0》（2026-11-04，11 页）
> 分析基线：`D:\lumi_ws\lumi-books` main 分支，含 P1/P2/P3a 阅读器治理与 CI 加固成果（截至 2026-11-05）
> 性质：只读分析文档，不代表已实施任何 V0.2+ 功能

## 一、总体结论

1. **V0.1 已全部完成且超出方案验收标准**。方案要求「可构建安装 + 阅读回归 + 五栏导航 + 占位状态」，实际交付还包含：正式签名链路、词典自托管、v1.0.1 体检版、798 条单测与五 job CI。
2. **方案最大的范围膨胀点在 V0.3 知识图谱与 V0.4/V0.6 Skills**：两块目前均为零代码（各只有 1 个占位页），而方案分别规划了 6 张表 + 审核流 + 图谱视图、10 张表 + 自进化生命周期。直接按方案实施风险过高，报告给出裁剪版路径（见第四节）。
3. **AI 引擎（V0.5）的基建比方案假设的好**：翻译与外部 TTS 已经打通「多 Provider + 加密 API key + 自定义 Provider」全链路，通用化成 chat 通道是增量工作而非新建。
4. **配套基建（Room 迁移链、WorkManager、WebDAV 同步、墓碑机制）全部成熟**，新增数据域可以低成本挂载。

## 二、逐版本差距对照

### V0.1 品牌与基础改造 — ✅ 完成

| 方案验收项 | 实际状态 |
| --- | --- |
| 可构建安装 | ✅ release 签名链路（keystore.properties + signingConfigs），v1.0.0/v1.0.1 已发布 GitHub Release |
| 核心阅读回归 | ✅ 798 条单测 + `check-known-failures.py` 权威判定 + 五 job CI（含 Robolectric 分片缓存） |
| 品牌改造 | ✅ 忘言品牌、主题色预设、隐私政策同步 |
| 五栏导航 | ✅ 书架、笔记、知识、技能、我的（`FloatingTabBar.kt:139`） |
| 占位状态 | ✅ `KnowledgePlaceholderScreen` / `SkillsPlaceholderScreen` 带空状态文案 |
| 数据不丢 | ✅ DB version=20，MIGRATION_1_2→19_20 全链路无断档 |

### V0.2 阅读与笔记整合 — ◐ 部分（缺口：中）

| 方案要求 | 现状 | 证据 |
| --- | --- | --- |
| 六类型笔记（原文摘录/阅读批注/个人思考/疑问/灵感/AI 摘要） | 仅 4 种自由字符串：`highlight`/`underline`/`note` + 手写墨迹（`pdf_ink_*`/`epub_comic_ink_*`）；无枚举、无「批注/思考/疑问/灵感/AI摘要」区分 | `NoteEntity.kt:27`、`SelectionMenuSettings.kt:4`、`PdfInkStroke.kt:89` |
| 笔记可追溯到书籍和原文位置 | ✅ 已有：`bookId` + `chapterIndex` + `start/endPosition` + `start/endLocatorJson` | `NoteEntity.kt:16-22` |
| 摘录/批注/思考分层保存 | 部分：`isNote` 与 `origin` 字段可承载一部分，但无类型维度 | `NoteEntity.kt:31-33` |
| 检索与关联 | 书架搜索仅标题/作者/标签（`BookDao.kt:49-50`）；单书内全文搜索已有（`BookTextSearch.kt:12-43`）；**跨书搜笔记/摘录内容没有**（NoteDao 无内容搜索） | — |

### V0.3 知识图谱 — ✗ 空白（缺口：大）

- DB 15 个实体中无任何 knowledge/graph/node 表；DAO 层零匹配（`AppDatabase.kt:31-48`）。
- 方案要求：8 类节点、8 类关系、证据链、三态审核、局部子图视图 —— 全部不存在。
- 有利条件：`NoteEntity` 的 `selectedText`/`locatorJson`/`tagsJson` 即证据链输入源；`AnnotationFeedScreen` 已做跨书时间线，是列表视图的前身。

### V0.4 Skills 基础 — ✗ 空白（缺口：大）

- 仅 `SkillsPlaceholderScreen.kt`（26 行）+ 导航路由（`Screen.kt:14`）。
- 方案要求的导入/校验/运行/Trace/反馈全部不存在。占位页注释已承认依赖「账号、服务端编排与工具注册」，而方案第十三节又明确「第一版不做服务端」——**方案自身存在张力，需要先裁剪 Skills 的 V1 形态**。

### V0.5 AI 知识引擎 — ◐ 部分（缺口：中）

| 方案要求 | 现状 |
| --- | --- |
| 统一模型适配层 | ◐ 翻译已有多 Provider（MiMo/DeepSeek/OpenAI/自定义）+ BEARER/API_KEY 鉴权 + 加密存储（`TranslationSettings.kt:6-12`、`TranslationTokenStore.kt:16`）；外部 TTS 同构（`ExternalTtsConfig.kt:51`）。但无通用 chat/completions 通道 |
| 「AI 设置」入口 | ✗ `SettingsDetailScreens.kt` 无任何 AI 项（只有阅读/显示/目标/存储/备份/关于/高亮色/语言） |
| 回答显示来源、推断标注 | ✗ 无 AI 生成内容 |

### V0.6 Skills 进化 — ✗ 空白（缺口：大，且依赖 V0.4+V0.5）

### 配套基建 — ✅ 成熟

- WorkManager：6 个 CoroutineWorker（词典×2、WebDAV 同步、高亮规则扫描、PDF 转换×2）。
- WebDAV：同步书籍文件 + 笔记/书签/阅读记录/标签/文件夹 + 墓碑（`WebdavSyncManager.kt:184-236`、`WebdavPortableStateSync.kt:141-180`），namespace 开关机制可直接新增 `"knowledge"` 域。

## 三、可复用资产清单

| 资产 | 位置 | 可复用为 |
| --- | --- | --- |
| 多 Provider HTTP 骨架 | `translation/TranslationSettings.kt` + `TranslationTokenStore.kt` | V0.5 AI Provider Adapter 的直接前身：把「翻译 prompt」换成「chat messages」，加一个通用 `LlmClient` 即可 |
| WebDAV namespace 机制 | `WebdavPortableStateSync.kt:168-180` | 知识域同步：`setOf("knowledge_node","knowledge_evidence")` 一行扩展 |
| NoteEntity 证据字段 | `NoteEntity.kt:16-38` | V0.3 证据链输入源；六类型可先用 `type` 字符串扩展，避免首版迁移 |
| 跨书标注流 | `AnnotationFeedScreen.kt` + `AnnotationTagEntity` | 知识库列表视图的 UI 骨架 |
| 摘录长图 / 划线导出 | `ExcerptActivity`、`BookNotesExportBuilder` | Skills 第一版填充物（内置 Skill 1：读书笔记导出；Skill 2：章节摘录卡片） |
| 字体/词典下载管线 | `FontDownloadManager`、`DictionaryWorkers` | 若未来做「模型/资源下载」可复用其断点与校验模式 |
| CI 与迁移纪律 | `DatabaseMigrations.kt`、`check-known-failures.py` | 新表迁移照 1_2→19_20 惯例追加 20_21；AI 代码同样纳入 known-failures 管控 |

## 四、裁剪建议（关键决策点）

方案原文与代码现状/产品形态之间存在三处张力，建议按以下方式裁剪：

### 1. 知识图谱首版：两表 + 列表，不做图

- 首版只建 `knowledge_nodes` + `knowledge_evidence` 两张表；`relations`/`aliases`/`reviews` 后置。
- 视图用「按标签/书籍分组的卡片流」（复用 AnnotationFeed 模式），**不做力导向图谱**——8 类节点 + 8 类关系 + 子图交互的成本放在没有跨书问答能力之前，无法被消费。
- 「AI 提议节点」依赖 V0.5，全部后置；首版只有「用户手动从划线存知识」。

### 2. Skills 首版：本地预设流程，不做导入与进化

- 方案 V1 安全边界（禁任意代码、权限最小化）本身正确，但它事实上排除了「ZIP 导入可执行包」的 V1 可行性。
- 建议首版 Skills 页 = **内置技能列表**：读书笔记导出（Markdown）、摘录长图、章节总结占位等，全部由代码实现、用户点击运行。`skill_runs` 执行记录可后置到有 Trace 需求时。
- 导入中心 / 进化中心 / 版本回滚推迟到服务端上线后（前提已定，见 3.1 节）。

### 3. AI 引擎先于 Skills（方案内部顺序保持），入口先行

- 把「AI 设置」（Provider + API Key，复用翻译那套）先落进设置页，成本约半天，为 V0.5 扫清配置面。
- 首个 AI 能力建议选「书籍问答（带来源）」而不是「知识提取」：前者能直接消费 V0.2 的检索层，验证 Adapter。

### 3.1 已拍板的服务端前提（2026-11-05）

用户确认：后续可自建服务端（方案第四节 Go API + PostgreSQL 由「可选」转「规划内」），但不上架应用商店。由此三项决策落定：

| 决策 | 内容 | 影响的实施步骤 |
| --- | --- | --- |
| 表设计留同步位 | `knowledge_nodes`/`knowledge_evidence` 建表时直接带 `syncId`/`updatedAt`/`deviceId` 字段（同构于 `NoteEntity` 的 syncId 先例），同步协议后补不改表 | V0.3a 建表规范 |
| LLM key 走服务端代理 | AI 调用不在本地存厂商 key，客户端只持服务端令牌；翻译 TokenStore 体系仅作过渡/离线兜底 | V0.5a 形态从「填 key」改为「填服务端地址」；服务端上线前先做本地 key 过渡版 |
| 免注册账号 | 服务端起步用设备码免注册（复用 `SyncStateEntity` 的 deviceId 概念），后续按需升级注册体系 | 服务端建设首版方案 |

Skills 完整形态（导入/Trace/进化/回滚）随之解锁，但排到服务端之后：首版仍为本地内置技能（V0.4a）→ 服务端只读 Skill 包仓库（包由维护者审核签名）→ 导入中心/执行 Trace/反馈中心 → 进化中心（V0.6 原文）最后解锁。

## 五、修正后的执行顺序

```
在途（阅读器内核，先清账）
  1. V 真机冒烟（10 分钟）
  2. P3b 统一三张参数表
  3. 主题色浓度（待拍板 A/B/C，可并行）
方案主线
  4. V0.2a 笔记类型枚举化 + 跨书笔记搜索          ← 最小、独立、零迁移风险
  5. V0.2b 「存为知识」入口（type 派生，不建新表）
  6. V0.5a AI 设置入口 + LlmClient 泛化（先本地 key 过渡，预留服务端代理切换）
  7. V0.3a knowledge_nodes + knowledge_evidence 两表 + 列表视图（MIGRATION 20_21）
  8. V0.4a 内置技能列表页（导出/摘录两个技能）
  9. V0.3b/V0.5b 关系与书籍问答（带来源）
  10. V0.6 自进化（仅在有真实使用数据后评估）

  并行线：服务端建设（设备码免注册起步）——解锁 V0.4b 导入/Trace 与 V0.6 进化中心
```

与方案的差异：把 V0.2 拆成 a/b 两步、V0.5 的配置面前置到 V0.3 之前、V0.4 缩为内置技能列表；其余顺序与方案一致。

## 六、备注

- 方案封面所列仓库 `github.com/a1242691089-source/wangyan` 与本地工作仓 `D:\lumi_ws\lumi-books` 的对应关系未核实（README 现指向 Lumi fork 链路），实施前建议先确认远端仓库命名。
- 方案声明「不代表已完成源码级审计」——本文档即是对该缺口的补齐，但覆盖范围为本文所列项，非全仓库逐文件审计。
- 服务端前提已定（2026-11-05）：自建服务端、不上商店、设备码免注册；原「无服务端导致 Skill 导入不可行」的结论相应放宽为「等服务端」。
