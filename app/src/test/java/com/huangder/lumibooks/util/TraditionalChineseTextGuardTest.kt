package com.huangder.lumibooks.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 繁体文案的内容正确性守门。
 *
 * [StringsRatchetTest] 只守「覆盖率」——某个 key 有没有译文。它看不出译文本身是不是
 * 繁体：`welcome_privacy_content` 这类早期从简体半机械转换来的长文案，键齐了、占位符
 * 也对，但正文里混着 100 多个简体字，覆盖率测试完全绿灯。这类错误只能靠逐字扫描发现。
 *
 * 黑名单不硬编码，而是从 [ChineseConverter] 的简→繁映射表推导：
 * `映射键 ∩ 简体语料实际用字 − 豁免字`。这样映射表更新时守门自动跟随，也不会把
 * 语料里根本不会出现的生僻字塞进常量。
 *
 * 口径说明：这是**项目语料口径**，不是语言学口径。[EXEMPT] 里的字在繁体里同样合法
 * （頁面、表示、只有、了解、群組、平台、秘密、干擾），扫进来会误报。反过来说，
 * 「后/里/准/划/云」在特定词里也是合法的（皇后、公里、批准、划船、詩云），但本项目
 * 语料没有这些用法，把它们留在黑名单里收益远大于风险；将来真要用到，在 [EXEMPT]
 * 里加字并在用到的那条加重豁免，而不是把整字从黑名单删掉。
 */
class TraditionalChineseTextGuardTest {

    companion object {
        private val TRADITIONAL_LOCALES = listOf("zh-rTW", "zh-rHK", "zh-rMO")

        /** 两条法律长文案：全仓库仅有的、由简体半机械转换而来的文本。 */
        private val LEGAL_KEYS = listOf("welcome_privacy_content", "welcome_terms_content")

        /** 繁体同样合法、不能进黑名单的字。 */
        private val EXEMPT = setOf('面', '表', '只', '了', '群', '台', '秘', '干')

        private val COMMENT = Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL)
        private val ELEMENT =
            Regex("<string[ \t\r\n]([^>]*)>(.*?)</string>", RegexOption.DOT_MATCHES_ALL)
        private val NAME_ATTR = Regex("\\bname=\"([^\"]+)\"")
        private val HEADING = Regex("^[一二三四五六七八九十]+、")

        private fun resDir(): File =
            sequenceOf(
                File("src/main/res"),
                File(System.getProperty("user.dir"), "src/main/res"),
                File(System.getProperty("user.dir")).let { dir ->
                    if (dir.name == "app") dir.parentFile?.resolve("app/src/main/res") else null
                }
            ).filterNotNull().firstOrNull { it.isDirectory }
                ?: error("找不到 res 目录，user.dir=${System.getProperty("user.dir")}")

        /** locale 名（如 `zh-rTW`，默认语言写 `values`）→ key → 原始文本。 */
        private fun stringValues(locale: String): Map<String, String> {
            val dir = if (locale == "values") "values" else "values-$locale"
            val xml = resDir().resolve(dir).resolve("strings.xml").readText().replace(COMMENT, "")
            return ELEMENT.findAll(xml).mapNotNull { match ->
                NAME_ATTR.find(match.groupValues[1])?.groupValues?.get(1)
                    ?.let { it to match.groupValues[2] }
            }.toMap()
        }

        /** 简体语料里真正用到的字符（values 与 zh-rCN 是同一套简体）。 */
        private fun simplifiedCorpusChars(): Set<Char> =
            listOf("values", "zh-rCN")
                .flatMap { stringValues(it).values }
                .joinToString("")
                .toSet()

        private val simplifiedOnly: Set<Char> by lazy {
            val (source, target) = ChineseConverter.mappingStrings("traditional")
                ?: error("ChineseConverter 未导出简→繁映射")
            // 映射表里混有大量「简繁同形」的恒等对（回→回、分→分），只保留真正有差异的。
            val differing = source.indices
                .filter { source[it] != target[it] }
                .map { source[it] }
                .toSet()
            differing.intersect(simplifiedCorpusChars()) - EXEMPT
        }

        /** 按行拆开文本；值里用的是字面量 `\n`（反斜杠 + n），不是真换行。 */
        private fun lines(value: String): List<String> = value.split("\\n")

        /** TW → HK 的术语规则，顺序敏感（长词在前）。 */
        private val HK_TERM_RULES = listOf(
            "區域網路" to "局域網",
            "本機詞典" to "本地詞典",
            "本機網路" to "本地網絡",
            "字型" to "字體",
            "網路" to "網絡",
            "使用者" to "用戶",
            "上傳" to "上載",
            "驗證" to "認證",
            "連線至" to "連接",
            "連線" to "連接",
            "規範" to "規管",
            "回饋" to "反映",
            "簡訊" to "短訊"
        )

        private fun toHongKong(text: String): String =
            HK_TERM_RULES.fold(text) { acc, (from, to) -> acc.replace(from, to) }
    }

    @Test
    fun blacklistIsNotDegenerate() {
        // 防止有人把映射表改坏或豁免集写错，让下面的扫描变成空转。
        assertTrue(
            "简体字黑名单只有 ${simplifiedOnly.size} 字，疑似推导失败（应 ≥ 300）",
            simplifiedOnly.size >= 300
        )
        // 抽查几个必然在内的高频字，确认推导方向没反。
        for (ch in "应读设书们会关为个当时点电发对开产从") {
            assertTrue("「$ch」应在简体黑名单里", ch in simplifiedOnly)
        }
    }

    @Test
    fun traditionalLocalesContainNoSimplifiedOnlyChars() {
        val problems = mutableListOf<String>()
        for (locale in TRADITIONAL_LOCALES) {
            for ((key, value) in stringValues(locale)) {
                val hits = value.filter { it in simplifiedOnly }
                if (hits.isEmpty()) continue
                val detail = hits.toSet().joinToString(" ") { ch ->
                    "$ch→${ChineseConverter.toTraditional(ch.toString())}"
                }
                problems += "  $locale / $key: ${hits.length} 字 —— $detail"
            }
        }
        assertTrue(
            "繁体 locale 的文案里出现了简体专用字，说明这段文本没有真正转成繁体：\n" +
                problems.joinToString("\n") +
                "\n（黑名单共 ${simplifiedOnly.size} 字，由 ChineseConverter 映射推导）",
            problems.isEmpty()
        )
    }

    @Test
    fun legalBundlesKeepStructureAndIdentifiers() {
        // 字形可以修，结构不行：条款条数、项目符号、权限名、URL、日期、版本号都是硬事实。
        data class Spec(
            val key: String,
            val lineCount: Int,
            val bulletCount: Int,
            val headingCount: Int,
            val clauseCount: Int,
            val urlCount: Int,
            val dates: List<String>
        )

        val specs = listOf(
            Spec(
                key = "welcome_privacy_content",
                lineCount = 45,
                bulletCount = 18,
                headingCount = 10,
                clauseCount = 2,
                urlCount = 2,
                dates = listOf("2026年6月29日", "2026年10月1日")
            ),
            Spec(
                key = "welcome_terms_content",
                lineCount = 43,
                bulletCount = 6,
                headingCount = 13,
                clauseCount = 5,
                urlCount = 2,
                dates = listOf("2026年10月1日")
            )
        )

        val permissions = listOf(
            "READ_EXTERNAL_STORAGE", "READ_MEDIA_IMAGES", "INTERNET",
            "ACCESS_NETWORK_STATE", "WRITE_SETTINGS", "SYSTEM_ALERT_WINDOW", "WAKE_LOCK"
        )

        for (locale in TRADITIONAL_LOCALES) {
            val values = stringValues(locale)
            for (spec in specs) {
                val value = values[spec.key] ?: error("$locale 缺少 ${spec.key}")
                val body = lines(value)
                val where = "$locale / ${spec.key}"

                assertEquals("$where 行数变了", spec.lineCount, body.size)
                assertEquals("$where 项目符号数变了", spec.bulletCount, value.count { it == '•' })
                assertEquals(
                    "$where 章节数变了",
                    spec.headingCount,
                    body.count { HEADING.containsMatchIn(it) }
                )
                assertEquals("$where 「條款」与「協議」的层级被改乱了", spec.clauseCount, value.split("條款").size - 1)

                // 章节序号必须仍是「一、」到「十、」，不能丢节或合并。
                val numerals = body.filter { HEADING.containsMatchIn(it) }
                    .map { it.substringBefore("、") }
                assertEquals("$where 章节序号不连续", spec.headingCount, numerals.toSet().size)

                for (p in permissions) {
                    // 权限清单只出现在隐私政策的「二、權限用途」；协议里没有。
                    val expected = if (spec.key == "welcome_privacy_content") 1 else 0
                    assertEquals("$where 权限名 $p 出现次数不对", expected, value.split(p).size - 1)
                }
                assertEquals("$where issue 链接被改了", spec.urlCount, value.split("https://github.com/a1242691089-source/wangyan/issues").size - 1)
                for (date in spec.dates) {
                    assertTrue("$where 缺少日期 $date", value.contains(date))
                }
            }
        }

        // 版本号不递增：这次是字形规范化，不是条款变更。
        for (locale in TRADITIONAL_LOCALES) {
            val values = stringValues(locale)
            assertTrue("$locale 政策版本应为 3", values["welcome_privacy_content"]!!.contains("政策版本：3"))
            assertTrue("$locale 协议版本应为 2", values["welcome_terms_content"]!!.contains("協議版本：2"))
        }

        // 「協議」在隐私政策里只允许出现在「協議連結」（第三方协议链接）这一处。
        // 如果把「條款」批量替换成「協議」，这里会立刻红。
        for (locale in TRADITIONAL_LOCALES) {
            val privacy = stringValues(locale)["welcome_privacy_content"]!!
            assertEquals(
                "$locale 隐私政策里的「協議」只应出现在「協議連結」处",
                privacy.split("協議連結").size - 1,
                privacy.split("協議").size - 1
            )
        }
    }

    @Test
    fun hkAndMoFollowTwTermRules() {
        val tw = stringValues("zh-rTW")
        val hk = stringValues("zh-rHK")
        val mo = stringValues("zh-rMO")

        for (key in LEGAL_KEYS) {
            val expected = toHongKong(tw.getValue(key))
            assertEquals(
                "zh-rHK 的 $key 与 zh-rTW 按术语规则派生后不一致——多半是只改了 TW 忘了 HK",
                expected,
                hk.getValue(key)
            )
            assertEquals(
                "zh-rMO 的 $key 与 zh-rHK 不一致；这两份文件必须逐字节相同",
                hk.getValue(key),
                mo.getValue(key)
            )
        }
    }
}
