package com.huangder.lumibooks.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Ratchet guard for translation coverage.
 *
 * Every locale is allowed to miss a fixed number of keys relative to
 * `values/strings.xml`; the number may only go down. Before this guard existed,
 * adding a string meant hand-editing eight files and silently losing the ones
 * you forgot (the runtime just falls back to the default language). The
 * baseline is a constant on purpose: lowering it shows up in the PR diff.
 *
 * Keys marked `translatable="false"` are excluded from the count: copying a
 * brand name or a regex into other locales is meaningless, so demanding them
 * would only create noise. The `diagnostic_*` block stays untranslated on
 * purpose for now (developer-only screens), which is why most locales keep a
 * 41-key allowance.
 */
class StringsRatchetTest {

    /** One `<string>` declaration plus the attribute that decides if it counts. */
    data class StringEntry(val name: String, val translatable: Boolean)

    companion object {
        /** locale → allowed missing key count, documented by the V0.3b design note. */
        private val ALLOWED_MISSING = mapOf(
            "en" to 0,
            "ja" to 275,
            "ko" to 275,
            "zh-rCN" to 0,
            "zh-rHK" to 41,
            "zh-rMO" to 41,
            "zh-rTW" to 41
        )

        /**
         * Gradle runs unit tests with the module directory as working directory,
         * which makes `src/main/res` relative. IDEs occasionally differ, so fall
         * back to the absolute paths derived from user.dir.
         */
        private fun resDir(): File =
            sequenceOf(
                File("src/main/res"),
                File(System.getProperty("user.dir"), "src/main/res"),
                File(System.getProperty("user.dir")).let { dir ->
                    if (dir.name == "app") dir.parentFile?.resolve("app/src/main/res") else null
                }
            ).filterNotNull().firstOrNull { it.isDirectory }
                ?: error("找不到 res 目录，user.dir=${System.getProperty("user.dir")}")

        private val COMMENT = Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL)
        // Requires whitespace after "string" so <string-array> is not counted.
        private val STRING_TAG = Regex("<string[ \t\r\n][^>]*>")
        private val NAME_ATTR = Regex("\\bname=\"([^\"]+)\"")
        private val NOT_TRANSLATABLE = Regex("\\btranslatable\\s*=\\s*\"false\"")
        /** 完整元素（开标签属性 + 正文），用于取翻译文本本身。 */
        private val STRING_ELEMENT =
            Regex("<string[ \t\r\n]([^>]*)>(.*?)</string>", RegexOption.DOT_MATCHES_ALL)
        private val PLACEHOLDER =
            Regex("%(?:\\d+\\$)?[-#+ 0,(]*\\d*(?:\\.\\d+)?[a-zA-Z]")
    }

    /**
     * name → 原始文本（未反转义）。`&amp;`、`\n` 字面量都按源文件原样保留，
     * 因为这里只比较结构（占位符、locale 间一致性），不消费文本内容。
     */
    fun parseStringValues(xml: String): Map<String, String> =
        STRING_ELEMENT.findAll(xml.replace(COMMENT, ""))
            .mapNotNull { match ->
                val name = NAME_ATTR.find(match.groupValues[1])?.groupValues?.get(1)
                    ?: return@mapNotNull null
                name to match.groupValues[2]
            }
            .toMap()

    /**
     * 占位符多重集。比较「类型 + 索引」而不是顺序：译文可以为了语序把
     * `%1$s` 挪到后面（ja/ko 的 pdf_convert_* 就是如此），只要索引绑定不变。
     */
    private fun placeholders(text: String): List<String> =
        PLACEHOLDER.findAll(text).map { it.value }.toList().sorted()

    /**
     * Parses every `<string>` tag into `name → entry`, ignoring comments.
     * A key declared twice keeps the last declaration, matching aapt2.
     */
    fun parseStringEntries(xml: String): Map<String, StringEntry> =
        STRING_TAG.findAll(xml.replace(COMMENT, ""))
            .mapNotNull { match ->
                val tag = match.value
                val name = NAME_ATTR.find(tag)?.groupValues?.get(1) ?: return@mapNotNull null
                name to StringEntry(name, !NOT_TRANSLATABLE.containsMatchIn(tag))
            }
            .toMap()

    /** Extracts `<string name="...">` keys, ignoring commented-out entries. */
    fun parseStringKeys(xml: String): Set<String> = parseStringEntries(xml).keys

    /** Keys a translator is actually expected to provide for this file. */
    private fun translatableKeys(file: File): Set<String> =
        parseStringEntries(file.readText())
            .values
            .filter { it.translatable }
            .map { it.name }
            .toSet()

    @Test
    fun missingKeysDoNotExceedBaseline() {
        val base = translatableKeys(resDir().resolve("values/strings.xml"))
        assertTrue("基础语言 values/strings.xml 解析为空，棘轮失效", base.size > 500)

        val failures = ALLOWED_MISSING.mapNotNull { (locale, allowed) ->
            val file = resDir().resolve("values-$locale/strings.xml")
            if (!file.exists()) return@mapNotNull "values-$locale/strings.xml 不存在"
            val missing = base - translatableKeys(file)
            if (missing.size <= allowed) null
            else "$locale: 缺失 ${missing.size} 个 key，超过基线 $allowed；请补齐或在测试里下调基线。" +
                "新增缺失（前 30 个）：" + missing.sorted().take(30).joinToString(", ")
        }

        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun everyBaselineLocaleHasResDirectory() {
        val missingDirs = ALLOWED_MISSING.keys.filterNot {
            resDir().resolve("values-$it").isDirectory
        }
        assertTrue("基线里的 locale 目录不存在：$missingDirs", missingDirs.isEmpty())
    }

    @Test
    fun parseStringKeysIgnoresCommentsAndDeduplicates() {
        val sample = """
            <resources>
                <!-- <string name="commented_out">旧文案</string> -->
                <string name="first">一</string>
                <string name="second">%1${'$'}d 条</string>
                <string name="first">重复定义</string>
                <plurals name="not_a_string"><item quantity="one">x</item></plurals>
            </resources>
        """.trimIndent()
        assertEquals(setOf("first", "second"), parseStringKeys(sample))
    }

    @Test
    fun translatableFalseKeysAreExcludedFromBaseline() {
        val entries = parseStringEntries(resDir().resolve("values/strings.xml").readText())
        val excluded = entries.values.filterNot { it.translatable }.map { it.name }.toSet()
        assertTrue("values 里应至少有 4 个 translatable=false，实际 ${excluded.size}", excluded.size >= 4)
        assertTrue(
            "translatable=false 不应出现在可翻译集合里",
            (entries.keys - excluded).intersect(excluded).isEmpty()
        )
    }

    /**
     * aapt2 只在 link 阶段校验单个文件里的格式串，**不跨 locale 比对**：
     * 把 `%1$s` 译成 `%1$d` 照样能编译，运行时才抛 IllegalFormatConversionException。
     * 这条测试是唯一能挡住这类崩溃的环节。
     */
    @Test
    fun placeholderTypesMatchAcrossLocales() {
        val base = parseStringValues(resDir().resolve("values/strings.xml").readText())
        val failures = mutableListOf<String>()
        ALLOWED_MISSING.keys.forEach { locale ->
            val translated = parseStringValues(resDir().resolve("values-$locale/strings.xml").readText())
            translated.forEach { (key, value) ->
                val expected = placeholders(base[key] ?: return@forEach)
                val actual = placeholders(value)
                if (expected != actual) failures += "$locale/$key: 期望 $expected，实际 $actual"
            }
        }
        assertTrue("占位符与源语言不一致：\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    /**
     * zh-rHK 与 zh-rMO 用的是同一套港澳书面语，两份文件应当逐条一致。
     * 抓「只改了 HK 忘了 MO」这类漏改——缺失数棘轮看不到文本级别的不一致。
     */
    @Test
    fun hkAndMoMustStayIdentical() {
        val hk = parseStringValues(resDir().resolve("values-zh-rHK/strings.xml").readText())
        val mo = parseStringValues(resDir().resolve("values-zh-rMO/strings.xml").readText())
        assertEquals("zh-rHK 与 zh-rMO 的 key 集合不同", hk.keys, mo.keys)
        val diff = hk.filter { (key, value) -> mo[key] != value }.keys.sorted()
        assertTrue(
            "zh-rHK 与 zh-rMO 文本不一致（应同步修改）：${diff.take(10)}",
            diff.isEmpty()
        )
    }
}
