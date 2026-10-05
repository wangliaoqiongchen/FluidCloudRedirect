package com.mo.fkLTY.engine

import android.content.SharedPreferences

/**
 * 远程偏好键(hook 端读取 / 设置 App 端写入),group 名沿用旧模块的 "redirect_config"。
 */
object RedirectPrefs {
    /** 远程偏好组名,与 01 号文档旧模块语义一致 */
    const val GROUP = "redirect_config"

    /** 重定向目标浏览器包名;不存在或空白 = 禁用重定向 */
    const val KEY_TARGET = "target_browser_package"

    /** 规则 1:heytapbrowser://webpage?url= 协议卡片,默认开 */
    const val KEY_RULE_PROTOCOL = "rule_heytap_protocol"

    /** 规则 2:文字搜索卡片(textType == 29),默认开 */
    const val KEY_RULE_TEXT_SEARCH = "rule_text_search"

    /** 规则 3:OPPO/Heytap 浏览器 + 合法 http(s) 网页卡片,默认开 */
    const val KEY_RULE_HEYTAP_HTTP = "rule_heytap_http"

    /** 规则 4(实验性,默认关):目标是其他浏览器的 VIEW+http(s) 卡片 */
    const val KEY_RULE_BROWSER_CARD = "rule_browser_card"

    /** 详细日志(hook 端 verbose 输出,含监控位) */
    const val KEY_VERBOSE_LOG = "verbose_log"

    /** 规则开关集;设置页与 hook 端共用同一组键与默认值 */
    fun toggles(prefs: SharedPreferences): RuleToggles = RuleToggles(
        protocol = prefs.getBoolean(KEY_RULE_PROTOCOL, true),
        textSearch = prefs.getBoolean(KEY_RULE_TEXT_SEARCH, true),
        heytapHttp = prefs.getBoolean(KEY_RULE_HEYTAP_HTTP, true),
        anyBrowserCard = prefs.getBoolean(KEY_RULE_BROWSER_CARD, false),
    )
}

/** 重定向规则开关(数据驱动,替代旧模块的硬编码三规则) */
data class RuleToggles(
    val protocol: Boolean,
    val textSearch: Boolean,
    val heytapHttp: Boolean,
    val anyBrowserCard: Boolean,
)
