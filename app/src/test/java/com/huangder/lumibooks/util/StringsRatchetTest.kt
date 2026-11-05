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
 */
class StringsRatchetTest {

    companion object {
        /** locale → allowed missing key count, documented by the V0.3b design note. */
        private val ALLOWED_MISSING = mapOf(
            "en" to 17,
            "ja" to 277,
            "ko" to 277,
            "zh-rCN" to 178,
            "zh-rHK" to 269,
            "zh-rMO" to 269,
            "zh-rTW" to 269
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
        private val STRING_NAME = Regex("<string\\s[^>]*\\bname=\"([^\"]+)\"")
    }

    /** Extracts `<string name="...">` keys, ignoring commented-out entries. */
    fun parseStringKeys(xml: String): Set<String> =
        STRING_NAME.findAll(xml.replace(COMMENT, ""))
            .map { it.groupValues[1] }
            .toSet()

    @Test
    fun missingKeysDoNotExceedBaseline() {
        val base = parseStringKeys(resDir().resolve("values/strings.xml").readText())
        assertTrue("基础语言 values/strings.xml 解析为空，棘轮失效", base.size > 500)

        val failures = ALLOWED_MISSING.mapNotNull { (locale, allowed) ->
            val file = resDir().resolve("values-$locale/strings.xml")
            if (!file.exists()) return@mapNotNull "values-$locale/strings.xml 不存在"
            val missing = base - parseStringKeys(file.readText())
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
}
