package com.mo.fkLTY.hook

import android.content.Context
import android.content.Intent
import android.os.Bundle
import io.github.libxposed.api.XposedInterface.ExceptionMode
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 模块入口,由 META-INF/xposed/java_init.list 按名加载。
 *
 * 进程过滤:仅 com.oplus.metis 的 :text_intent 子进程挂钩(02 号文档 §1.1——
 * TextIntentWorkerService / RouterActivity / TextIntentActionProvider 等 10 个组件全在该子进程)。
 *
 * hook 布局(03 号文档 §4.4):
 *  A 主:TextIntent.invoke(Context)                 —— 行为咽喉
 *  B 冗余:SchemeData/WebData.parseIntent(Context)  —— packageName 产生点,卡片 UI 同步变对
 *  C 监控:ZoomOpenHandler.startByZoom(仅记录)     —— 未来旁路预警
 */
class MainHook : XposedModule() {

    /**
     * 真实进程名以 onModuleLoaded(param).processName 为准。
     * ⚠️ 不能用 PackageLoadedParam.applicationInfo.processName 判断子进程:
     * 该 appInfo 处于 PM 层,processName 是 "com.oplus.metis",不带 ":text_intent" 后缀,
     * 会把所有包生命周期回调全部误滤掉(1.0.0 真机踩坑)。
     */
    @Volatile
    private var loadedProcessName: String? = null

    /** onPackageLoaded / onPackageReady 双回调兜底,只装一次 */
    private val installed = AtomicBoolean(false)

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        HookLog.bind(this)
        loadedProcessName = param.processName
        HookLog.log("loaded in ${param.processName}")
        if (param.processName.endsWith(TARGET_PROCESS_SUFFIX)) {
            HookLog.log("target process detected; waiting for package lifecycle to install hooks")
        }
    }

    override fun onPackageLoaded(param: PackageLoadedParam) {
        handlePackageLifecycle(param, phase = "onPackageLoaded")
    }

    /** 兜底:个别框架实现对 scope 应用自身只派发 PackageReady */
    override fun onPackageReady(param: PackageReadyParam) {
        handlePackageLifecycle(param, phase = "onPackageReady")
    }

    private fun handlePackageLifecycle(param: PackageLoadedParam, phase: String) {
        try {
            val pkg = param.packageName
            val procName = loadedProcessName ?: param.applicationInfo.processName
            // 诊断日志:任何包生命周期回调都留痕,杜绝静默漏挂
            HookLog.once("lc:$phase:$pkg", "package lifecycle $phase: package=$pkg, process=$procName")

            if (pkg != TARGET_PACKAGE) return
            if (procName == null || !procName.endsWith(TARGET_PROCESS_SUFFIX)) return
            if (!installed.compareAndSet(false, true)) return

            HookLog.log("matched target process via $phase; installing hooks")
            installHooks(param.getDefaultClassLoader())
        } catch (t: Throwable) {
            HookLog.log("lifecycle error in $phase: ${t.javaClass.simpleName}: ${t.message}", t)
        }
    }

    private fun installHooks(classLoader: ClassLoader) {
        installInvokeHook(classLoader)
        installParseIntentHooks(classLoader)
        installMonitorHook(classLoader)
    }

    /** A 主 hook:TextIntent.invoke(Context) */
    private fun installInvokeHook(classLoader: ClassLoader) {
        try {
            val cls = classLoader.loadClass(CLASS_TEXT_INTENT)
            val method = cls.getDeclaredMethod("invoke", Context::class.java)
            hook(method).setExceptionMode(ExceptionMode.PROTECTIVE).intercept(RedirectHooker())
            HookLog.once("installed:invoke", "Hook installed: TextIntent.invoke(Context)")
        } catch (t: Throwable) {
            HookLog.once("fail:invoke", "Cannot hook TextIntent.invoke: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    /** B 冗余 hook:SchemeData.parseIntent / WebData.parseIntent(签名均为 public (Context) -> TextIntent) */
    private fun installParseIntentHooks(classLoader: ClassLoader) {
        for (name in PARSE_INTENT_CLASSES) {
            try {
                val cls = classLoader.loadClass(name)
                val method = cls.getDeclaredMethod("parseIntent", Context::class.java)
                hook(method).setExceptionMode(ExceptionMode.PROTECTIVE).intercept(ParseIntentHooker())
                HookLog.once("installed:$name", "Hook installed: ${cls.simpleName}.parseIntent(Context)")
            } catch (t: Throwable) {
                HookLog.once("fail:$name", "Cannot hook $name.parseIntent: ${t.javaClass.simpleName}: ${t.message}")
            }
        }
    }

    /** C 监控 hook:ZoomOpenHandler.startByZoom(Context, Intent, Bundle, String),仅记录 */
    private fun installMonitorHook(classLoader: ClassLoader) {
        try {
            val cls = classLoader.loadClass(CLASS_ZOOM_HANDLER)
            val method = cls.getDeclaredMethod(
                "startByZoom",
                Context::class.java, Intent::class.java, Bundle::class.java, String::class.java,
            )
            hook(method).setExceptionMode(ExceptionMode.PROTECTIVE).intercept(MonitorHooker())
            HookLog.once("installed:monitor", "Hook installed: ZoomOpenHandler.startByZoom (monitor only)")
        } catch (t: Throwable) {
            HookLog.once("fail:monitor", "Cannot hook ZoomOpenHandler.startByZoom: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    companion object {
        const val TARGET_PACKAGE = "com.oplus.metis"
        const val TARGET_PROCESS_SUFFIX = ":text_intent"

        private const val CLASS_TEXT_INTENT = "com.oplus.textintent.distribution.bean.TextIntent"
        private const val CLASS_ZOOM_HANDLER = "com.oplus.textintent.common.utils.ZoomOpenHandler"
        private val PARSE_INTENT_CLASSES = arrayOf(
            "com.oplus.textintent.distribution.bean.SchemeData",
            "com.oplus.textintent.distribution.bean.WebData",
        )
    }
}
