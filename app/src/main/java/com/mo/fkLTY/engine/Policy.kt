package com.mo.fkLTY.engine

import java.net.URI
import java.net.URLDecoder

/**
 * 重定向判定策略,逐行对位旧模块混淆类 a/m.d 的还原实现(01 号文档 §2.4)。
 *
 * 纯 JVM 逻辑(不依赖 android),便于单元测试。四个条件满足其一即返回 Scene:
 *  1. heytapbrowser://webpage?url=<真实URL> 协议卡片 → 提取 url 参数(URLDecode);
 *  2. textType == 29 文字搜索卡片(WebData.parseIntent 的产物)→ 不改 URL;
 *  3. packageName == com.heytap.browser 且 dataString 是合法 http(s) → 不改 URL;
 *  4. (实验性)目标包是其他浏览器且 dataString 是合法 http(s) → 不改 URL;
 * 未命中返回 null → 完全不干预(保留应用胶囊、淘口令等非浏览器卡片行为)。
 */
object Policy {

    /** metis WebData/SchemeData 硬编码的默认浏览器 */
    const val HEYTAP_BROWSER = "com.heytap.browser"

    private const val PROTOCOL_PREFIX = "heytapbrowser:"

    /**
     * 命中的场景。extractedUrl 非 null 时引擎会把 intent 改写为 ACTION_VIEW + 该 URL,
     * 为 null 时仅改写目标包名、intent 原样放行。
     */
    data class Scene(val rule: String, val extractedUrl: String?)

    /**
     * @param pkg             TextIntent.packageName(可能为 null)
     * @param textType        TextIntent.textType(可能为 null)
     * @param dataString      intent.dataString(可能为 null)
     * @param rules           规则开关
     * @param isBrowserPackage 规则 4 的"目标包是否为本机浏览器"回调,由引擎注入 PackageManager 查询
     */
    fun evaluate(
        pkg: String?,
        textType: Int?,
        dataString: String?,
        rules: RuleToggles,
        isBrowserPackage: (String) -> Boolean = { false },
    ): Scene? {
        val data = dataString?.trim()?.takeIf { it.isNotEmpty() }

        // 规则 1:协议卡片。与旧实现一致——一旦命中协议前缀,后续规则不再尝试,
        // 协议解析失败(URI 非法 / host 非 webpage / url 参数缺失或非法)直接放弃干预。
        if (rules.protocol && data != null &&
            data.regionMatches(0, PROTOCOL_PREFIX, 0, PROTOCOL_PREFIX.length, ignoreCase = true)
        ) {
            val url = extractProtocolUrl(data) ?: return null
            return Scene("rule1_protocol", url)
        }

        if (rules.textSearch && textType == 29) return Scene("rule2_text_search", null)

        if (rules.heytapHttp && pkg == HEYTAP_BROWSER && isValidHttpUrl(data)) {
            return Scene("rule3_heytap_http", null)
        }

        if (rules.anyBrowserCard && data != null && isValidHttpUrl(data) &&
            pkg != null && pkg != HEYTAP_BROWSER && isBrowserPackage(pkg)
        ) {
            return Scene("rule4_browser_card", null)
        }

        return null
    }

    /** heytapbrowser://webpage?url=… → 提取并校验 url 参数 */
    private fun extractProtocolUrl(data: String): String? {
        val uri = try {
            URI(data)
        } catch (_: Exception) {
            return null
        }
        if (!uri.host.equals("webpage", ignoreCase = true)) return null
        val rawQuery = uri.rawQuery ?: return null
        val url = rawQuery.split('&').asSequence()
            .mapNotNull { pair ->
                val eq = pair.indexOf('=')
                if (eq < 0) return@mapNotNull null
                if (decode(pair.substring(0, eq)) != "url") return@mapNotNull null
                decode(pair.substring(eq + 1))
            }
            .firstOrNull() ?: return null
        return url.takeIf { isValidHttpUrl(it) }
    }

    /**
     * 旧模块 a/m.b 还原:java.net.URI 解析,scheme 限 http/https,host 非空且含非空白字符。
     */
    fun isValidHttpUrl(s: String?): Boolean {
        val trimmed = s?.trim()?.takeIf { it.isNotEmpty() } ?: return false
        val uri = try {
            URI(trimmed)
        } catch (_: Exception) {
            return false
        }
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return false
        val host = uri.host ?: return false
        return host.indexOfFirst { !Character.isWhitespace(it) && !Character.isSpaceChar(it) } >= 0
    }

    /** 旧模块 a/m.a 还原:trim 后 URLDecode(UTF-8),非法序列返回 null */
    private fun decode(s: String): String? = try {
        URLDecoder.decode(s.trim(), "UTF-8")
    } catch (_: IllegalArgumentException) {
        null
    } catch (_: Exception) {
        null
    }
}
