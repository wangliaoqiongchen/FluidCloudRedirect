package com.mo.fkLTY.hook

import android.content.Intent
import io.github.libxposed.api.XposedInterface

/**
 * 监控位(02 号文档 §5.3 方案 C):仅记录 ZoomOpenHandler.startByZoom,
 * 不改任何行为。若未来 OPPO 把"打开动作"下沉到检测层、出现绕过 TextIntent 的直开路径,
 * 这里会第一时间在日志暴露(观测到未经 RedirectEngine 改写的 heytap 包名即旁路证据)。
 *
 * 签名已核实:startByZoom(Context, Intent, Bundle, String),ZoomOpenHandler 为 Kotlin object。
 */
class MonitorHooker : XposedInterface.Hooker {

    override fun intercept(chain: XposedInterface.Chain): Any? {
        try {
            val intent = chain.getArg(1) as? Intent
            val target = chain.getArg(3)
            HookLog.verbose(
                "startByZoom pkg=$target, action=${intent?.action}, " +
                    "data=${intent?.dataString}, component=${intent?.component}"
            )
        } catch (_: Throwable) {
            // 监控位绝不影响宿主
        }
        return chain.proceed()
    }
}
