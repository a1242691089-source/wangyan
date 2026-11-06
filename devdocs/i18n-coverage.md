# 多语言覆盖率：口径、基线与批次路线

`values/strings.xml` 是唯一的源语言文件（简体中文），其余 `values-<locale>` 按需覆盖。缺失的 key 在运行时会回退到源语言，所以漏翻不会崩，只会让界面混语言——这是本文件要防的东西。

## 1. 统计口径

- 可翻译基数 = `values/strings.xml` 里除 `translatable="false"` 之外的全部 `<string>`。当前 **1737** 条（总 1741，排除 4 条：`welcome_brand_name`、`welcome_major_version`、`txt_toc_rule_default_chapter_regex`、`widget_preview_progress`）。
- 某个 locale 的「缺失」= 可翻译基数 − 该文件里出现的可翻译 key。
- 守门测试：`app/src/test/java/com/huangder/lumibooks/util/StringsRatchetTest.kt`。判断是「缺失数 ≤ 基线」，只降不升。
- 基线是测试文件里的常量 map，改它必须出现在 PR diff 里——这是刻意的。

`translatable="false"` 被排除，是因为把品牌名、正则、`0%` 这类占位复制到 8 个文件毫无意义；要求它们齐平只会制造噪音。

## 2. 当前基线（ja/ko 与 diagnostic 补齐之后，全部归零）

| locale | 基线 | 状态 |
|---|---|---|
| en | 0 | 已齐平 |
| zh-rCN | 0 | 已齐平（照抄 values，同一套简体） |
| zh-rTW | 0 | 已齐平（含 `diagnostic_*`） |
| zh-rHK | 0 | 已齐平（港澳书面语，与 zh-rMO 逐条一致） |
| zh-rMO | 0 | 已齐平（与 zh-rHK 逐条一致） |
| ja | 0 | 已齐平（です・ます調） |
| ko | 0 | 已齐平（합니다体） |

七个 locale 全部 1739 条（= 1737 可翻译 + 2 条随 `values` 一起复制的非翻译键）。`baselineIsClosedAtZero` 钉死了「全零」这个状态：谁想把基线重新上调，必须改动测试本身。

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

**三地通用**：简体专用字一律不得出现。判定口径不是手写清单（旧版那份 97 字清单实测对现有语料命中 0，已失效），而是从 `ChineseConverter` 的简→繁映射表推导：`映射键 ∩ 简体语料用字 − 豁免集`，现为 **368 字**。豁免集是 `面 表 只 了 群 台 秘 干` —— 这 8 个字在繁体里同样合法（頁面、表示、只有、了解、群組、平台、秘密、干擾），扫进来会误报。「后/里/准/划/云」虽是部分合法，但本项目语料没有「皇后/公里/批准/划船/詩云」这类用法，留在黑名单里收益大于风险。守门见 `TraditionalChineseTextGuardTest`。

## 4. 写入方式（防翻车）

大批量追加文案必须遵守：

- **值按原文偏移切片搬运，不 parse 再序列化**。`ElementTree` 回写会还原 `&amp;`、把 `\n` 变成真换行、把 `%%` 折成 `%`，一条都不许发生。
- 读写都用 `encoding="utf-8", newline=""`。Windows 下默认文本模式会把整份文件写成 CRLF，417 行改动会变成 6000 行 diff。
- 插入点固定 `</resources>` 之前，4 空格缩进、单行 `<string name="x">text</string>`；插入前后注释数量必须不变。
- 写完必查：XML 良构、key 唯一、格式参数按索引绑定不变（`%1$d` 仍是第 1 个参数、类型不可变），但允许为语序调整出现位置（ja/ko 的 `pdf_convert_*` 5 条即如此）；无裸 `<`/`&`；无半角撇号（aapt2 会报 unescaped apostrophe）。
- 校验命令：`:app:processDebugResources`（aapt2 在 link 阶段才校验格式串，`compileDebugKotlin` 完全不读 strings.xml）。本机约 1 分钟。注意 aapt2 **不跨 locale 比对**格式串类型，跨语言的占位符一致性靠 `placeholderTypesMatchAcrossLocales` 单测。

## 5. 新增一条文案时的动作

1. 写进 `values/strings.xml`。
2. **七个 locale 全部同步**：`values-en`、`values-ja`、`values-ko`、`values-zh-rCN`、`values-zh-rHK`、`values-zh-rMO`、`values-zh-rTW`。基线已全部归零，漏任何一个都会红。
3. 批量补的时候用台账：写 `tools/i18n_ja_ko/<批次>.tsv`（`key ⇥ ja ⇥ ko`，或繁体的 `key ⇥ 文本`），再跑 `python tools/i18n_fill.py fill --batch <批次> --apply`（繁体用 `fillzh`）。脚本会按 base 顺序插入、断言占位符多重集、注释数与 key 唯一性，并拒绝裸 `&`/`<`/半角撇号。

## 6. 批次路线

1. `zh-rTW` → `zh-rHK` / `zh-rMO`：已完成（2026-11-05）。226 条以 `zh-rTW` 文本为底稿、按港澳用词替换后追加。
   现存文本差异：`zh-rHK` vs `zh-rTW` 95 处、`zh-rMO` vs `zh-rTW` 84 处、`zh-rHK` vs `zh-rMO` 0 处（两份文件现在逐条一致）。
2. `ja` / `ko`：已完成（2026-11-05）。各 275 条实译（234 功能文案 + 41 诊断页），基线 275 → 0。
3. `diagnostic_*` 41 条：已完成（2026-11-05）。ja/ko 实译，zh-rTW/HK/MO 共用一份繁体文本（纯技术文案，三地用词无分歧）。
4. 已知债务（**已清**，2026-11-05）：`welcome_privacy_content` / `welcome_terms_content` 曾是「半繁半简」——早期从 `zh-rCN` 半机械转换，正文混着 122 / 172 个简体字，且「应用/應用程式」「文件/檔案」「反馈/回饋」「协议/條款」混用。已逐行手工重写（TW 24 + 25 行），HK/MO 由 TW 按 13 条术语规则派生，三份文件各只改 2 行。
   这批属**内容错误**而非覆盖率缺口，棘轮看不到；现由 `TraditionalChineseTextGuardTest` 三条用例守住：字形扫描、结构冻结（行数/项目符号/权限名/URL/日期/版本号）、HK 与 MO 按规则派生一致。
   隐私用词已一并统一：`zh-rTW` 全用「隱私」（14 处），`zh-rHK` / `zh-rMO` 全用「私隱」（14 处）。旧文本两边都混着两种写法（TW 的「兒童私隱」、HK 的「隱私政策」），本次理清。派生规则因此多一条「隱私→私隱」，pre-flight 比较前先把两种写法拉平。

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

### ja / ko 与 diagnostic（2026-11-05，终局批次）

- ja / ko 各 275 条，分五批：highlight 60 / reader+tts 47 / book+epub+folder+bookshelf 48 / annotation 等 79 / diagnostic 41。
- 语体从既有 1464 条实测得出并延续：**ja です・ます調**（标签用体言止め）、**ko 합니다体**（标签用名词短语）。术语表：ハイライト / 注釈 / しおり / ブックマーク / テーマ / フォルダー / 目次；하이라이트 / 주석 / 책갈피 / 북마크 / 테마 / 폴더 / 목차。
- ko 新文案**避免「忘言 + 助词」直接相接**：既有文件里有 `忘言를` / `忘言가`，助词与「言」的收音不一致，不再扩散，改用「忘言 앱이」这类句式绕开。
- 41 条 diagnostic：ja/ko 实译（含两段合规长文，逐项对照 en：记录时长 20 分钟、上限约 2 MB、脱敏对象、仅手动分享/保存时才离开设备）；zh-rTW / zh-rHK / zh-rMO 共用同一份繁体文本——这批是纯技术文案，三地用词无分歧，不需要港澳覆盖。
- 台账 `tools/i18n_ja_ko/A–F.tsv`（中→日→韩对照、繁体），写入脚本 `tools/i18n_fill.py`（`list` / `fill` / `fillzh` / `verify`）。
- 验证：`:app:processDebugResources` 通过；`StringsRatchetTest` 7 用例全绿，新增 `baselineIsClosedAtZero`；七个 locale 均 1739 条、缺 0。

### 繁体法律文案净化（2026-11-05）

- 病灶：`welcome_privacy_content`（45 行 / 2530 字符）与 `welcome_terms_content`（43 行 / 2154 字符）在 TW/HK/MO 里是「半繁半简」，24 / 25 行含简体字，合计 122 / 172 字，且用词混着「应用/應用程式」「文件/檔案」「协议/條款」。这是仓库里唯一的内容错误，覆盖率测试完全看不到。
- 做法：**逐行手工重写 49 行**（其余行原样照抄，diff 最小），不调用 `ChineseConverter.toTraditional()`——它会产出 `幹擾`/`隻`/`麵`/`臺`，且不知道「应用」该是「應用程式」。
- 统一用词（旧文本里这些词只出现在这 49 行）：应用→應用程式 8、协议→協議 12、云端→雲端 3、反馈→回饋 2、短信→簡訊、历史→歷史、参数→參數、质量→品質、连接→連線。
- **不**统一的词（集合外也在用，属项目既有约定）：保存（全文 15 次）、支援（全文 21 次）、文件（`mineru_risk_upload` 1 次）、配置（`backup_content_library` 1 次）。
- 顺带对齐项目既定用词：螢幕（11:0）、視窗（6:0）、位址（22:0）、「」（36:0）、儲存庫、營業秘密、散布（GPL 术语）。
- HK/MO 由 TW 派生，规则从旧文本反推并验证 `R(TW_old) == HK_old` 逐字节成立（11 条基础 + 回饋→反映、簡訊→短訊）。`本機/本地` 用复合词定位（`本機詞典`→`本地詞典`、`本機網路`→`本地網絡`），不做全局替换。
- 不改政策版本号（`政策版本：3`、`協議版本：2`）——这是字形规范化，条款实质未变，不构成需重新确认的重大变更。
- 验证：`git diff --numstat` 三文件均 `2 2`；`:app:processDebugResources` 通过；`TraditionalChineseTextGuardTest` 4 用例 + `StringsRatchetTest` 7 用例共 11 全绿。
- 工具：定稿块文件 `tools/i18n_legal/*.tw.txt`（一行一逻辑行）、写入脚本 `tools/i18n_legal_replace.py`（`check` / `verify --pre` / `write --apply`），按 `<string>` 值区间切片替换，断言 ASCII 词元多重集、7 个权限名、URL、日期、`條款` 计数不变。

## 8. 拆分 strings 文件与法律 HTML（2026-11-06）

前七节的口径只覆盖 `values/strings.xml`。同一 `res/` 下的 13 个拆分文件从未进入任何校验器（`i18n_fill.py` 的 `locale_path()`、`StringsRatchetTest`、`TraditionalChineseTextGuardTest` 全都写死 `strings.xml`），于是「1502 → 0」与「实际仍缺 416」可以同时成立。

### 8.1 缺口与修法

| 文件 | 可译基数 | 修法 |
| --- | --- | --- |
| `remote_library_strings.xml` | 91 | 6 个 locale 各缺 8 条（远程书库法律声明标题/正文等），实译 |
| `dictionary_strings.xml` | 33 | 6 个 locale **整文件缺失**，ja/ko 实译、TW 手写、HK/MO 派生、zh-rCN 照抄 |
| `listening_strings.xml` | 14 | 5 个 locale 整文件缺失（TW 原有），ja/ko 实译、HK/MO 由 TW 派生 |
| `excerpt.xml` | 8 | 6 个 locale 整文件缺失 |
| `selection_menu.xml` | 5 | 6 个 locale 整文件缺失 |
| `native_epub_strings.xml` | 2 | 5 个 locale 整文件缺失 |
| `authorized_folders_strings.xml` | 4 | zh-rCN / zh-rHK / zh-rMO 各缺 4 条 |

- 合计 **416 个 locale×key**：ja 70 / ko 70 / zh-rCN 74 / zh-rTW 54 / zh-rHK 74 / zh-rMO 74。修完后 `verify` 全零。
- ja です・ます調、ko 합니다体延续第 6 节口径；`zh-rTW` 手写（字型／預設／資料夾／檔案／逾時）；`zh-rCN` 是同一套简体，直接照抄 `values/`。
- `zh-rHK` / `zh-rMO` 由 `zh-rTW` 派生，规则与 `TraditionalChineseTextGuardTest.HK_TERM_RULES` 同口径：`本機詞典→本地詞典`、`本機詞庫→本地詞庫`、`字型→字體`、`網路→網絡`、`使用者→用戶`、`上傳→上載`、`驗證→認證`、`連線→連接`、`隱私→私隱`、`品質→質素`、`專案→項目`；两文件逐字节相同。

### 8.2 工具与守门

- 新脚本 `tools/i18n_split_fill.py`，与 `i18n_fill.py` 同源（直接 import 它的 `entries` / `read` / `write` / `placeholders` / `FORBIDDEN`，避免两份转义口径漂移），只多一个「文件」维度：`list` / `fill` / `fillzh` / `verify`，另有 `--copy-values`（zh-rCN 照抄）与 `--derive-from zh-rTW`（港澳派生）。台账在 `tools/i18n_split/<file-stem>/<batch>.tsv`，目标 locale 文件不存在时按需创建（`<?xml…?><resources></resources>` 骨架）。
- `StringsRatchetTest` 新增 `splitStringFilesAreFullyTranslated`（按「文件 × locale」求差集，「整文件缺失」单独报），并把 `placeholderTypesMatchAcrossLocales`、`hkAndMoMustStayIdentical` 的扫描范围从 `strings.xml` 扩到 `values/` 下全部含 `<string>` 的文件。
- `TraditionalChineseTextGuardTest.stringValues()` 改为合并目录下所有 xml，字形扫描与结构冻结自动覆盖拆分文件。

### 8.3 法律 HTML（`app/src/main/assets/html/`）

- 病灶：`privacy.html` / `terms.html` 只有简体一份，`<html lang="zh-CN">`、零语言切换；WebView 顶栏标题走已翻译的 `R.string.privacy_policy`，正文永远简体——日/韩/繁中用户看到「外文标题 + 简体正文」。
- 命名：`privacy.html` / `terms.html` 作简体基准与兜底，新增 `privacy-{en,ja,ko,zh-TW,zh-HK,zh-MO}.html` 与 `terms-*`。`licenses.html` 不本地化。
- 加载：`WebViewActivity.resolveLegalAsset()` 由运行时 `configuration.locales[0]` 映射后缀（zh-CN 无后缀），`assets.open` 探测存在性，缺失回退基准文件；不认的系统语言也跟着回退简体，避免标题正文两种语言。
- 内容：结构逐行对齐基准（privacy 9 章、terms 12 章、表格行、列表项、`<h3>` 数量全等）；版本事实全语言一致——政策版本 3 / 协议版本 2 / 生效 2026-06-29 / 更新 2026-10-01；条款页里的隐私链接指向同语言页（`privacy-zh-HK.html` 等）。
- 语体：TW 手写（隱私、資料夾、檔案、網路、預設、匯入、字型→字型）；HK/MO 由 TW 按上表规则 + `使用者條款→使用條款`、`智慧財產權→知識產權`、`開放原始碼→開放源碼` 派生，两份只差 `<html lang>` 与自链接。
- **待人工/法务审校**：六种语言的正文属本轮新译，未经法务确认；en/ja/ko 用词与已发布的 `welcome_privacy_content` / `welcome_terms_content` 保持同一批术语，但两份文本的章节数量不同（HTML 9/12 章，字符串版 10/13 章，字符串版多「本次更新补充」一节），后续修订需两边同时改。
- 守门：新增 `LegalHtmlLocaleTest` 六条用例——变体齐全、`<html lang>` 匹配、结构/硬事实（URL、日期、包名、权限名）与基准一致、繁中三份无简体专用字（黑名单同 `ChineseConverter` 推导口径）、HK/MO 归一后逐字相同、条款页链接同语言隐私页。
- 遗留：站点副本 `docs/privacy.html` / `docs/terms.html`（GitHub Pages 单页，自带 `lumi-nav.js`）仍是纯简体，需另一套导航改造，登记为待办。

### 8.4 本批实测

- `python tools/i18n_split_fill.py verify` → 13 个文件 × 7 locale 全 0（改前 416）。
- XML 良构 98 个 strings 文件全通过；HK 与 MO 拆分文件逐字节相同。
- `StringsRatchetTest` 8 用例 + `TraditionalChineseTextGuardTest` 4 用例 + `LegalHtmlLocaleTest` 6 用例 = **18 全绿**。
