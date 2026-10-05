package com.mo.fkLTY.hook

import android.content.Context
import com.mo.fkLTY.engine.RedirectEngine
import io.github.libxposed.api.XposedInterface

/**
 * 主 hook(02 号文档 §5.1 方案 A):拦截 TextIntent.invoke(Context)。
 * 执行层唯一入口——所有 4 处 UI 调用 + bean 链最终必经的"咽喉"。
 *
 * 我们不改方法参数、只改 bean 字段,所以一律 chain.proceed() 原样放行;
 * 拦截器自身的异常显式 catch,保证宿主行为可控(框架 PROTECTIVE 模式兜底之外的双保险)。
 */
class RedirectHooker : XposedInterface.Hooker {

    override fun intercept(chain: XposedInterface.Chain): Any? {
        try {
            val bean = chain.thisObject
            val context = chain.getArg(0) as? Context
            if (bean != null && context != null) {
                RedirectEngine.apply(context, ReflectedTextIntent(bean), "invoke")
            }
        } catch (t: Throwable) {
            HookLog.once("err:invoke", "RedirectHooker error: ${t.javaClass.simpleName}: ${t.message}")
        }
        return chain.proceed()
    }
}
