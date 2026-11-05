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
            "zh-rHK" to 267,
            "zh-rMO" to 267,
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
    }

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
}
