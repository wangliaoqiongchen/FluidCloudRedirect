package com.mo.fkLTY.engine

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.mo.fkLTY.hook.HookLog
import com.mo.fkLTY.hook.ReflectedTextIntent

/**
 * 重定向执行体(旧模块 redirectBrowser 的重写):只改 bean 字段与 Intent 对象本身,
 * 不改方法返回值、不拦截调用,原方法后续的 startActivity/startByZoom 自然使用改写后的值
 * ——这是旧模块被验证能覆盖所有下游路径的关键(01 号文档 §2.3)。
 */
object RedirectEngine {

    /**
     * @param source 调用来源(主 hook "invoke" / 冗余 hook "parseIntent"),用于日志区分
     */
    fun apply(context: Context, bean: ReflectedTextIntent, source: String) {
        val prefs = HookLog.prefs() ?: run {
            HookLog.once("prefs", "Remote preferences unavailable; redirection disabled")
            return
        }

        val target = prefs.getString(RedirectPrefs.KEY_TARGET, null)
            ?.trim()?.takeIf { it.isNotEmpty() } ?: return

        // 幂等键:metis 自己选中的就是目标浏览器,或冗余 hook 已改写过 → 跳过(03 号文档 §4.3)
        val pkg = bean.packageName
        if (pkg == target) return

        val intent = bean.intent ?: return

        val toggles = RedirectPrefs.toggles(prefs)
        val scene = Policy.evaluate(pkg, bean.textType, intent.dataString, toggles) { candidate ->
            BrowserAvailability.isBrowserPackage(context, candidate)
        } ?: return

        if (!BrowserAvailability.isAvailable(context, target)) {
            HookLog.once("browser:$target", "Selected browser is unavailable: $target (rule=${scene.rule}, via=$source)")
            return
        }

        // ★ 核心三连:先清精确目标(组件名/选择器优先级高于包名),再 setPackage(01 号文档 §2.3)
        bean.setPackageName(target)
        intent.component = null
        intent.selector = null
        intent.setPackage(target)
        scene.extractedUrl?.let { url ->
            intent.action = Intent.ACTION_VIEW
            intent.data = Uri.parse(url)
        }

        HookLog.log(
            "Redirected package=$target, rule=${scene.rule}, via=$source, " +
                "action=${intent.action}, data=${intent.dataString}"
        )
    }
}
