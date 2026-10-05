package com.mo.fkLTY.hook

import android.content.Context
import com.mo.fkLTY.engine.RedirectEngine
import io.github.libxposed.api.XposedInterface

/**
 * 冗余 hook(02 号文档 §5.2 方案 B):拦截 SchemeData.parseIntent / WebData.parseIntent。
 * 这两处是 packageName 硬编码 com.heytap.browser 的"产生点"——在这里改写,
 * 流体云卡片 UI 上显示的目标应用名/图标也会同步变正确(旧模块只改 invoke,卡片图标仍是他家)。
 *
 * 与主 hook 并挂,幂等改写互为双保险:任何一层失效另一层兜底。
 */
class ParseIntentHooker : XposedInterface.Hooker {

    override fun intercept(chain: XposedInterface.Chain): Any? {
        val result = chain.proceed()
        try {
            val context = chain.getArg(0) as? Context
            if (result != null && context != null) {
                RedirectEngine.apply(context, ReflectedTextIntent(result), "parseIntent")
            }
        } catch (t: Throwable) {
            HookLog.once("err:parseIntent", "ParseIntentHooker error: ${t.javaClass.simpleName}: ${t.message}")
        }
        return result
    }
}
