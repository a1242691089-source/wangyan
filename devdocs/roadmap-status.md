# 开发路线图进度（更新于 2026-11-07，V0.5b 完成后）

> 依据：`devdocs/gap-analysis-plan-vs-code.md`（2026-11-05 方案 V1.0 差距分析）的修正执行顺序，
> 对照当前仓库实际代码核实。**下次开发前先读本文档，避免重复探查。**

## 一、修正后的执行顺序与完成状态

| 顺序 | 路线图项 | 状态 | 关键证据 |
|---|---|---|---|
| 0 | 在途：V 真机冒烟 / P3b 统一参数表 / 主题色浓度拍板 | ⚠️ 未核实 | 无对应记录，下次先确认是否已被后续工作覆盖 |
| 1 | V0.2a 笔记类型枚举化 + 跨书笔记搜索 | ✅ 完成 | `NoteType.kt`、`NoteTypeUi.kt`、`AnnotationListFilters.kt` |
| 2 | V0.2b 「存为知识」入口 | ✅ 完成 | v1.0.2 CHANGELOG：选区菜单「存为知识」 |
| 3 | V0.5a AI 设置入口 + LlmClient 泛化 | ✅ 完成（未发版） | `ai/` 包：`AiSettings`（复用翻译 Provider/Auth 枚举，独立存储+独立密钥仓）/`AiTokenStore`/`LlmClient`（OpenAI 兼容 chat/completions，错误语义与翻译同构）/`AiRepository`/`AiSettingsViewModel`；`AiSettingsScreen` + 设置双入口（`SettingsScreen` 行 + `DetailActivity` 路由 `ai_settings`）；8 locale `ai_settings.xml` 全补；`AiSettingsTest`/`LlmClientTest` 全绿 |
| 4 | V0.3a knowledge 两表 + 列表视图 | ✅ 超额完成 | `KnowledgeNodeEntity`/`KnowledgeEvidenceEntity`/`KnowledgeDao`（提交 `7caea2e`）；`knowledge_relations` 九类关系 + 节点详情页 + 证据定位跳转也做了（`devdocs/v0.3a-knowledge-relations.md`） |
| 5 | V0.4a 内置技能列表页 | ✗ 未开始 | `SkillsPlaceholderScreen.kt` 仍为占位页；两个候选技能代码已存在：`BookNotesExportBuilder`（划线导出 Markdown）、`ExcerptActivity`（摘录长图） |
| 6 | V0.3b 按书筛选/关系计数/i18n 棘轮 | ✅ 完成 | `devdocs/v0.3b-knowledge-filter-i18n-ratchet.md` + V0.3c i18n 口径修订（`devdocs/i18n-coverage.md`） |
| 7 | V0.3b/V0.5b 书籍问答（带来源） | ✅ 完成（未发版） | 持久化会话：`ChatSessionEntity`/`ChatMessageEntity` 两表 + `MIGRATION_22_23` + `ChatDao`/`ChatRepository`（beginTurn 事务双写 + pending 占位防中断丢答）；检索管线：`ai/qa` 包（QaTokenizer 中文 bigram 分词 / QaScorer BM25-lite / QaBudget 8k token 预算 / CitationParser 引用+推断句切分 / QaPromptBuilder 7 条引用协议硬约束）+ `data/qa`（QaSourceLoader 三层用户数据快照 + BookBodyReader 正文窗口，PDF/CBZ raster 跳过）；UI：`ui/knowledge/KnowledgeQaScreen`（角标点击走 V0.3a 证据跳转链路）+ 书详情页 FloatingDetailTag 第 6 动作与 InfoCard 入口；28 条 `qa_*` × 8 locale；11 个新测试类全绿（含 MockWebServer 端到端、迁移、ViewModel）；完整交接见 `devdocs/v0.5b-knowledge-qa.md` |
| 8 | V0.6 自进化 | ✗ 未开始 | 按报告建议：有真实使用数据后再评估 |

## 二、V0.3 遗留小账（不阻塞，随手清）

- 标签多选 / 与书筛选的联合计数（当前单选）
- 知识页按书分组视图（现只有平铺 + 筛选）
- 关系计数卡片只显数量，不显类型与对端（详情页承担）
- i18n 棘轮基线需随翻译补齐定期下调

## 三、工程质量线（2026-11-06 ~ 11-07 已清账，与 V0.x 主线并行）

- v1.0.2 正式版发布（含 P0 液态玻璃知识详情页崩溃热修，rc2 真机 2/2 存活）
- 应用内法律文档 7 语言；站点法律页 12 个语言页 + 切换器 + 自动跳转（`docs/tools/build-legal-pages.py`）
- 仪器测试根治：6 个 compose 测试迁 Robolectric、Orchestrator 进程隔离、androidx.test 栈升级；
  全量真机 135 条 6m44s 一次跑完；11 条既有环境基线失败 + 4 条新失败全部分诊修复
- 法律译文人工/法务审校：**用户明确跳过**（译文按现稿定稿）
- V0.5b CI 零改动落地：UI 落 `ui/knowledge`、ai/qa 与 data/qa 前缀均已被守门前缀表覆盖，分片 yml 无需改动
- 登记 TODO：`NoteDao.insertNotes` REPLACE 静默吞行加固（牵动同步链路，待评估）

## 四、下次开发的建议起点

1. **V0.4a（推荐）**：内置技能列表页——壳 + 运行入口即可，技能本体（`BookNotesExportBuilder`/`ExcerptActivity`）已存在；
   且 V0.5b 已为「技能调 LLM」准备好 `LlmClient`/`AiRepository` 底座
2. V0.5b 真机端到端验证：配置真实 AI key 后走通 提问 → 引用角标 → 跳原文 → 书详情入口，
   验证后重装 `/d/wb-tc/device-test/restore-1.0.2.apk`
3. 清在途账：确认 V 真机冒烟 / P3b 是否仍悬

## 五、环境备忘

- 仓库：`D:/wb-tc/lumi-books`（GitHub: a1242691089-source/wangyan，main）
- gradle：`/d/wb-tc/gradle-8.14.3/bin/gradle`，`JAVA_HOME=D:/wb-tc/jdk/jdk-21.0.13+9`，`GRADLE_USER_HOME=D:/wb-tc/gradle-home`
- 真机：SM-S9280（S24 Ultra / Android 16）；测试后 AGP 会卸载应用，重装 `/d/wb-tc/device-test/restore-1.0.2.apk`
- 推送偶发代理 502：后台重试循环（20s 间隔）即可恢复
- CI 双工作流均需盯绿；本地 JVM 全量单测曾卡死 31 分钟，JVM 验证可交 CI 承担
