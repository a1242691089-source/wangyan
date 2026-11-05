# 多语言覆盖率：口径、基线与批次路线

`values/strings.xml` 是唯一的源语言文件（简体中文），其余 `values-<locale>` 按需覆盖。缺失的 key 在运行时会回退到源语言，所以漏翻不会崩，只会让界面混语言——这是本文件要防的东西。

## 1. 统计口径

- 可翻译基数 = `values/strings.xml` 里除 `translatable="false"` 之外的全部 `<string>`。当前 **1737** 条（总 1741，排除 4 条：`welcome_brand_name`、`welcome_major_version`、`txt_toc_rule_default_chapter_regex`、`widget_preview_progress`）。
- 某个 locale 的「缺失」= 可翻译基数 − 该文件里出现的可翻译 key。
- 守门测试：`app/src/test/java/com/huangder/lumibooks/util/StringsRatchetTest.kt`。判断是「缺失数 ≤ 基线」，只降不升。
- 基线是测试文件里的常量 map，改它必须出现在 PR diff 里——这是刻意的。

`translatable="false"` 被排除，是因为把品牌名、正则、`0%` 这类占位复制到 8 个文件毫无意义；要求它们齐平只会制造噪音。

## 2. 当前基线（zh-rHK/MO 补齐之后）

| locale | 基线 | 缺口性质 |
|---|---|---|
| en | 0 | 已齐平 |
| zh-rCN | 0 | 已齐平（照抄 values，同一套简体） |
| zh-rTW | 41 | 只剩 `diagnostic_*`（开发者诊断页，主动不翻） |
| zh-rHK | 41 | 只剩 `diagnostic_*`（226 条已补，港澳书面语） |
| zh-rMO | 41 | 只剩 `diagnostic_*`（与 zh-rHK 逐条一致） |
| ja | 275 | 待补，需实译 |
| ko | 275 | 待补，需实译 |

## 3. 不能机器生成繁体

试过用项目内的 `util/ChineseConverter`（3149 条字级映射）把 `values/` 转成繁体，结果有两类问题：

1. **字级映射会造错词**：「页面」→「頁麵」（面/麵 不分）、「里面」同理。
2. **用词风格对不上**：`zh-rTW` 采用台湾用语——字型（不是字體）、預設（不是默認）、設定（不是設置）、頁面（不是頁麵）、檔案（不是文件）、資料夾（不是文件夾）、逾時（不是超時）。

所以 `zh-rTW` 的 226 条是逐条手写的。`zh-rHK` / `zh-rMO` 以这些文本为底稿，但不是逐字复制：按港澳书面语做术语替换，并把既有文件里的粤语口语改写为书面语。目标是每个 locale 内部语体一致，不追求与 `zh-rTW` 逐字相同。

繁体写作硬规则：

**`zh-rTW` 用词**：字型 / 預設 / 設定 / 資料夾 / 標註 / 劃線 / 檔案 / 資訊 / 網路 / 載入 / 匯入 / 匯出 / 搜尋 / 篩選 / 儲存 / 還原 / 支援 / 偵測 / 影片 / 音訊 / 效能 / 品質 / 相容 / 應用程式 / 使用者 / 連結 / 伺服器 / 快取 / 重新整理 / 逾時

**`zh-rHK` / `zh-rMO` 用词**（港澳书面语）：字體 / 網絡 / 上載 / 二維碼 / 私隱 / 用戶 / 點擊 / 顏色 / 內置 / 資料夾 / 檔案 / 設定 / 儲存 / 還原 / 佈局 / 反映 / 捐款。
「目前」与「當前」、「本機」与「本地」有语义分工（current / this device / local），两者都保留，不强行归一。
不得出现粤语口语：撳、嘅、喺、搵、冇、哋、揀、掣、唔、咩、咁、噉、嚟、佢、睇、諗、咗、啲、嗰，以及「呢條 / 呢個 / 仲差」这类组合。`sponsor_quote` 是引用语，白名单。

**三地通用**：简体专用字（与体侧内写冲准划创删别单双后吗响图声夹实尽属带并扫择换显暂条来标样欢没浅滤灵画盖称笔筛签类纳线绑结给络统绪缘缩网脚节虚观视览觉记证评识试该语调谱败转载辅边过运这酱里钟销锁锐键镜闪阅顶顾预题颜验齐）一律不得出现。

## 4. 写入方式（防翻车）

大批量追加文案必须遵守：

- **值按原文偏移切片搬运，不 parse 再序列化**。`ElementTree` 回写会还原 `&amp;`、把 `\n` 变成真换行、把 `%%` 折成 `%`，一条都不许发生。
- 读写都用 `encoding="utf-8", newline=""`。Windows 下默认文本模式会把整份文件写成 CRLF，417 行改动会变成 6000 行 diff。
- 插入点固定 `</resources>` 之前，4 空格缩进、单行 `<string name="x">text</string>`；插入前后注释数量必须不变。
- 写完必查：XML 良构、key 唯一、格式参数按索引绑定不变（`%1$d` 仍是第 1 个参数、类型不可变），但允许为语序调整出现位置（ja/ko 的 `pdf_convert_*` 5 条即如此）；无裸 `<`/`&`；无半角撇号（aapt2 会报 unescaped apostrophe）。
- 校验命令：`:app:processDebugResources`（aapt2 在 link 阶段才校验格式串，`compileDebugKotlin` 完全不读 strings.xml）。本机约 1 分钟。注意 aapt2 **不跨 locale 比对**格式串类型，跨语言的占位符一致性靠 `placeholderTypesMatchAcrossLocales` 单测。

## 5. 新增一条文案时的动作

1. 写进 `values/strings.xml`。
2. 至少同步 `values-en`、`values-zh-rCN`、`values-zh-rTW`、`values-zh-rHK`、`values-zh-rMO`——这五个基线现在都是 0 冗余，漏一条就红。
3. `ja` / `ko` 仍有 275 条余量，但不要把余量当缓冲区：补齐时同步下调基线。

## 6. 批次路线（剩余）

1. `zh-rTW` → `zh-rHK` / `zh-rMO`：已完成（2026-11-05）。226 条以 `zh-rTW` 文本为底稿、按港澳用词替换后追加，两边各降到 41。
   现存文本差异：`zh-rHK` vs `zh-rTW` 95 处、`zh-rMO` vs `zh-rTW` 84 处、`zh-rHK` vs `zh-rMO` 0 处（两份文件现在逐条一致）。
2. `ja` / `ko`：各 234 条实译（275 减 41 条 diagnostic）。
3. `diagnostic_*` 41 条：目前主动不翻，若要翻，6 个 locale 一起补完即可把基线全部降到 0。
4. 已知债务：`welcome_privacy_content` / `welcome_terms_content` 在 `zh-rTW`、`zh-rHK`、`zh-rMO` 三份文件里都混有简体字（书、们、会、关、应、读、设），是早期直接复制 `zh-rCN` 留下的。修正需要重排两千字法律文本，单独一批处理。

## 7. 本批实测结果

- `zh-rCN` 176 条：从 `values` 逐字复制（同语言，副本语义与回退一致）。
- `en` 15 条：实译，句式避开缩写以规避 aapt2 的撇号校验。
- `zh-rTW` 226 条：分四组（高亮规则 60 / 阅读器与朗读 50 / 书籍书架导入 EPUB 61 / 标注笔记导航主题 55）手写。
- 验证：`:app:processDebugResources` 通过（含 aapt2 格式串校验）、`StringsRatchetTest` 4 用例全绿；4 个 strings.xml 均 `0 deleted`，行尾全部 LF。

### zh-rHK / zh-rMO（2026-11-05）

- 226 条 × 2 文件，从 `values-zh-rTW` 尾部第 1543–1768 行的连续块按原文偏移整行切片搬运，9 条做港澳术语覆盖（字型→字體 5、網路→網絡 2、上傳→上載 1、點選→點擊 1）。
- 语体统一：术语归一 46 条（HK）/ 48 条（MO），去粤语口语 26 条（HK）/ 16 条（MO）；归一后两文件逐条一致。
- 两文件 1543 → 1769 行、1472 → 1698 键、注释 34 条未变、`git diff --numstat` 追加段均为 `226 0`。
- 基线：267 → 41。新增单测 `placeholderTypesMatchAcrossLocales`（跨语言占位符多重集）、`hkAndMoMustStayIdentical`（两文件逐条一致），共 6 用例全绿。
- 搬运脚本 `tools/i18n_port_tw_block.py`（`check` / `port` / `normalize` / `verify`），ja/ko 批次可复用。
