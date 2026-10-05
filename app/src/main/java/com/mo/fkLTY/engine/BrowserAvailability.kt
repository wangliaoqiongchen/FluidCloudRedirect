package com.mo.fkLTY.engine

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build

/** 目标应用可用性校验(旧模块 isApplicationAvailable 思路:存在 + enabled) */
object BrowserAvailability {

    fun isAvailable(context: Context, packageName: String): Boolean = try {
        val pm = context.packageManager
        val info = if (Build.VERSION.SDK_INT >= 33) {
            pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getApplicationInfo(packageName, 0)
        }
        info.enabled
    } catch (_: PackageManager.NameNotFoundException) {
        false
    } catch (_: Throwable) {
        false
    }

    /** 规则 4 用:pkg 是否能解析为浏览器(ACTION_VIEW + https + BROWSABLE + MATCH_DEFAULT_ONLY) */
    fun isBrowserPackage(context: Context, packageName: String): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))
            .addCategory(Intent.CATEGORY_BROWSABLE)
        return try {
            val flags = PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
            context.packageManager
                .queryIntentActivities(intent, flags)
                .any { it.activityInfo?.packageName == packageName }
        } catch (_: Throwable) {
            false
        }
    }
}
