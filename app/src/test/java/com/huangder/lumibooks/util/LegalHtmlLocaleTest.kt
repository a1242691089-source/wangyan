package com.huangder.lumibooks.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 法律 HTML 的多语言守门。
 *
 * 背景：`assets/html/privacy.html` 与 `terms.html` 原本只有简体一份，WebView 顶栏标题
 * 走的是已翻译的 `R.string.privacy_policy`，于是日/韩/繁中用户看到的是「外文标题 +
 * 简体正文」。1.0.2 之后按语言拆成 `privacy-<locale>.html`，本测试守住三件事：
 *
 * 1. 文件齐不齐——少一个只会回退简体（`WebViewActivity.resolveLegalAsset` 的兜底），
 *    不会崩，所以只能靠测试发现；
 * 2. 结构对不对——条款条数、表格行、列表项、权限名、URL、日期、版本号都是硬事实，
 *    译文可以改字形，不能改结构；
 * 3. 繁中三份有没有简体残留——口径与 [TraditionalChineseTextGuardTest] 一致，
 *    黑名单从 `ChineseConverter` 的简→繁映射表推导，而不是手写清单。
 */
class LegalHtmlLocaleTest {

    companion object {
        /** asset 文件名前缀 → `<h2>` 章节数（硬事实，改条款必须同步改这里）。 */
        private val DOCUMENTS = mapOf("privacy" to 9, "terms" to 12)

        /** 文件名后缀 → `<html lang>`。 */
        private val LOCALES = mapOf(
            "en" to "en",
            "ja" to "ja",
            "ko" to "ko",
            "zh-TW" to "zh-Hant-TW",
            "zh-HK" to "zh-Hant-HK",
            "zh-MO" to "zh-Hant-MO"
        )

        private val TRADITIONAL_SUFFIXES = listOf("zh-TW", "zh-HK", "zh-MO")

        /** 繁体同样合法、不能进黑名单的字（与 [TraditionalChineseTextGuardTest] 同口径）。 */
        private val EXEMPT = setOf('面', '表', '只', '了', '群', '台', '秘', '干')

        private val STYLE = Regex("<style>.*?</style>", RegexOption.DOT_MATCHES_ALL)
        private val TAG = Regex("<[^>]+>")

        private fun assetsDir(): File =
            sequenceOf(
                File("src/main/assets/html"),
                File(System.getProperty("user.dir"), "src/main/assets/html"),
                File(System.getProperty("user.dir")).let { dir ->
                    if (dir.name == "app") dir.parentFile?.resolve("app/src/main/assets/html") else null
                }
            ).filterNotNull().firstOrNull { it.isDirectory }
                ?: error("找不到 assets/html 目录，user.dir=${System.getProperty("user.dir")}")

        private fun read(name: String): String = assetsDir().resolve(name).readText()

        /** 只留正文文本，去掉样式与标签——字形扫描不该命中 CSS。 */
        private fun bodyText(html: String): String =
            TAG.replace(STYLE.replace(html, ""), " ")

        /** 简体正文里真正用到的字，用来把映射表裁成「本项目会踩到的简体字」。 */
        private val simplifiedOnly: Set<Char> by lazy {
            val corpus = DOCUMENTS.keys
                .flatMap { bodyText(read("$it.html")).toList() }
                .toSet()
            val (source, target) = ChineseConverter.mappingStrings("traditional")
                ?: error("ChineseConverter 未导出简→繁映射")
            val differing = source.indices
                .filter { source[it] != target[it] }
                .map { source[it] }
                .toSet()
            differing.intersect(corpus) - EXEMPT
        }
    }

    @Test
    fun allLocaleVariantsExist() {
        val missing = DOCUMENTS.keys.flatMap { doc ->
            LOCALES.keys.map { suffix -> "$doc-$suffix.html" }
        }.filterNot { assetsDir().resolve(it).isFile }
        assertTrue("缺少法律文本译文（缺失会静默回退简体）：$missing", missing.isEmpty())
    }

    @Test
    fun langAttributeMatchesFileName() {
        val problems = DOCUMENTS.keys.flatMap { doc ->
            LOCALES.mapNotNull { (suffix, lang) ->
                val html = read("$doc-$suffix.html")
                if (!html.contains("""<html lang="$lang">""")) "$doc-$suffix.html 的 <html lang> 不是 $lang" else null
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun structureMatchesSimplifiedBase() {
        fun count(html: String, token: String) = html.split(token).size - 1

        val failures = mutableListOf<String>()
        DOCUMENTS.forEach { (doc, headings) ->
            val base = read("$doc.html")
            assertEquals("$doc.html 的章节数应为 $headings", headings, count(base, "<h2>"))
            LOCALES.keys.forEach { suffix ->
                val html = read("$doc-$suffix.html")
                val where = "$doc-$suffix.html"
                if (count(html, "<h2>") != headings) {
                    failures += "$where 章节数 ${count(html, "<h2>")} ≠ $headings"
                }
                listOf("<h3>", "<table>", "<tr>", "<li>", "<p ").forEach { token ->
                    if (count(html, token) != count(base, token)) {
                        failures += "$where 的 $token 数量 ${count(html, token)} ≠ 基准 ${count(base, token)}"
                    }
                }
                // 法律事实：URL、日期、版本号、包名、权限名都不能被「翻译」掉。
                val facts = listOf(
                    "https://github.com/a1242691089-source/wangyan/issues",
                    "https://webpub.shlab.tech/dps/opendatalab-web/odl_v5.1690/service.html",
                    "https://webpub.shlab.tech/dps/opendatalab-web/odl_v5.1690/privacy.html",
                    "com.wangyan.reader",
                    "READ_EXTERNAL_STORAGE", "READ_MEDIA_IMAGES", "WRITE_SETTINGS",
                    "SYSTEM_ALERT_WINDOW", "WAKE_LOCK"
                )
                facts.forEach { fact ->
                    if (count(html, fact) != count(base, fact)) failures += "$where 丢失或重复了硬事实 $fact"
                }
                // 版本与日期按语言本地化写法不同，只校验数字与年份存在。
                if (!html.contains("2026")) failures += "$where 缺少 2026 年份"
                if (doc == "privacy" && !html.contains("3")) failures += "$where 缺少政策版本号 3"
            }
        }
        assertTrue("法律文本结构被改动：\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun traditionalVariantsContainNoSimplifiedOnlyChars() {
        // 防止映射表改坏或语料读空导致扫描变成空转。
        assertTrue(
            "简体字黑名单只有 ${simplifiedOnly.size} 字，疑似推导失败",
            simplifiedOnly.size >= 50
        )
        // 抽查几个必然出现在法律正文里的简体字，确认推导方向没反。
        for (ch in "设书为页读网数记处权") {
            assertTrue("「$ch」应在法律文本的简体黑名单里", ch in simplifiedOnly)
        }
        val problems = mutableListOf<String>()
        DOCUMENTS.keys.forEach { doc ->
            TRADITIONAL_SUFFIXES.forEach { suffix ->
                val text = bodyText(read("$doc-$suffix.html"))
                val hits = text.filter { it in simplifiedOnly }
                if (hits.isNotEmpty()) {
                    val detail = hits.toSet().joinToString(" ") { ch ->
                        "$ch→${ChineseConverter.toTraditional(ch.toString())}"
                    }
                    problems += "  $doc-$suffix.html: ${hits.length} 字 —— $detail"
                }
            }
        }
        assertTrue(
            "繁中法律文本里出现简体专用字：\n" + problems.joinToString("\n"),
            problems.isEmpty()
        )
    }

    /**
     * 港澳两份除 `<html lang>` 与自引用链接外必须逐字相同——和 `values-zh-rHK` /
     * `values-zh-rMO` 的规矩一致；抓到「只改 HK 忘了 MO」。
     */
    @Test
    fun hongKongAndMacaoStayIdentical() {
        fun normalize(html: String) = html
            .replace("""lang="zh-Hant-HK"""", """lang="zh-Hant-X"""")
            .replace("""lang="zh-Hant-MO"""", """lang="zh-Hant-X"""")
            .replace("-zh-HK.html", "-zh-X.html")
            .replace("-zh-MO.html", "-zh-X.html")

        val failures = DOCUMENTS.keys.filter {
            normalize(read("$it-zh-HK.html")) != normalize(read("$it-zh-MO.html"))
        }
        assertTrue("zh-HK 与 zh-MO 法律文本不一致（应同步修改）：$failures", failures.isEmpty())
    }

    /** 条款页里的「隐私政策」链接必须指向同语言的隐私页，不能把人送回简体。 */
    @Test
    fun termsLinksToSameLocalePrivacyPage() {
        val failures = LOCALES.keys.filterNot { suffix ->
            read("terms-$suffix.html").contains("""href="privacy-$suffix.html"""")
        }
        assertTrue("terms-<locale> 未链接到同语言的隐私页：$failures", failures.isEmpty())
    }
}
