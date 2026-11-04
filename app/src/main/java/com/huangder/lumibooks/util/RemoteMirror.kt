package com.huangder.lumibooks.util

import kotlinx.coroutines.CancellationException
import java.net.URI

/**
 * GitHub 资源的多源抓取编排。
 *
 * 2026-11-04 实测（本机）：直连 raw.githubusercontent.com 全部失败（0.13s 返回 000）、
 * cdn.jsdelivr.net 返回 403、raw.gitmirror.com 不可用；而 gh-proxy.com 与 ghproxy.net
 * 对 raw 文件与 release 资产均返回 200（0.6-4.7s，111-256 KB/s，支持 Range）。
 *
 * 因此反代只做「直连不通时还能用」的兜底，不承担提速；直连永远是第一顺位。
 * 反代形态固定为 https://<proxy-host>/<原始 https URL>（不编码、不嵌套），
 * 生成与校验都收口在这里，调用方禁止自行拼字符串。
 */
internal object RemoteMirror {

    /** 回退顺序即列表顺序。整体失效时删掉对应一行即可，不需要改调用方。 */
    private val PROXY_PREFIXES = listOf(
        "https://gh-proxy.com/",
        "https://ghproxy.net/"
    )

    private val PROXY_HOSTS = setOf("gh-proxy.com", "ghproxy.net")

    /** 允许直连、也允许被反代包裹的原始 host。 */
    private val UPSTREAM_HOSTS = setOf(
        "github.com",
        "raw.githubusercontent.com",
        "release-assets.githubusercontent.com",
        "objects.githubusercontent.com"
    )

    /** 直连优先，再按固定顺序套反代；非信任域或已经是反代形态时原样返回单条。 */
    fun candidates(url: String): List<String> {
        if (!isAllowedDirect(url)) return listOf(url)
        return buildList {
            add(url)
            PROXY_PREFIXES.forEach { add(it + url) }
        }.distinct()
    }

    /** 多 URL 场景（字体）：先把所有直连试完，再试各自的反代版本。 */
    fun candidates(urls: List<String>): List<String> =
        (urls + urls.flatMap { candidates(it).drop(1) }).distinct()

    /** 抓取白名单：受信直连地址，或形态合法的反代地址。 */
    fun isAllowed(url: String): Boolean = isAllowedDirect(url) || unwrap(url) != null

    /**
     * 反解反代地址。外层必须是受信 host（https、无 userInfo、无端口），
     * 内层必须是不含 query/fragment 的 GitHub https 地址，且禁止嵌套。
     *
     * @param allowQuery 外层是否允许携带 query（配置破缓存需要；词典下载不需要）
     * @return 内层原始 URL；不是合法反代时返回 null
     */
    fun unwrap(url: String, allowQuery: Boolean = true): String? =
        runCatching { parseProxy(url, allowQuery) }.getOrNull()

    /**
     * 逐源尝试，第一个成功即返回；全部失败抛出最后一个异常。
     * [MirrorAbort] 表示「换源也没意义」（如超出大小上限），立即向外抛。
     */
    fun <T> firstSuccess(original: String, attempt: (String) -> T): T {
        var last: Exception? = null
        for (url in candidates(original)) {
            try {
                return attempt(url)
            } catch (error: Exception) {
                if (error is CancellationException || error is MirrorAbort) throw error
                last = error
            }
        }
        throw last ?: IllegalStateException("没有可用的下载源")
    }

    private fun parseProxy(url: String, allowQuery: Boolean): String? {
        val outer = URI(url)
        if (outer.scheme != "https" || outer.userInfo != null || outer.port != -1) return null
        if (outer.host?.lowercase() !in PROXY_HOSTS) return null
        if (!allowQuery && outer.rawQuery != null) return null
        val rawPath = outer.rawPath ?: return null
        if (!rawPath.startsWith("/https://")) return null
        val inner = rawPath.removePrefix("/")
        // 禁止嵌套反代（URI 已把反斜杠归一化为 /，因此只剩这一种形态要挡）
        if (inner.contains("/https://")) return null
        if (!isAllowedDirect(inner)) return null
        val target = URI(inner)
        if (target.rawQuery != null || target.rawFragment != null) return null
        return inner
    }

    fun isAllowedDirect(url: String): Boolean =
        runCatching { isAllowedDirect(URI(url)) }.getOrDefault(false)

    private fun isAllowedDirect(uri: URI): Boolean {
        if (uri.scheme != "https" || uri.userInfo != null || uri.port != -1) return false
        if (uri.host?.lowercase() !in UPSTREAM_HOSTS) return false
        // 路径段不允许 "." / ".."：前缀匹配后浏览器归一化会绕过前缀校验
        return uri.rawPath.orEmpty().split('/').none { it == "." || it == ".." }
    }
}

/** 换源也不会成功的错误（内容超大、格式非法），不要在镜像间重试。 */
internal class MirrorAbort(message: String, cause: Throwable? = null) : Exception(message, cause)
