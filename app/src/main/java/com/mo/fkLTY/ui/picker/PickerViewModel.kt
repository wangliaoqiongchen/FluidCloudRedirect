package com.mo.fkLTY.ui.picker

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mo.fkLTY.R
import com.mo.fkLTY.engine.RedirectPrefs
import com.mo.fkLTY.service.PrefsBridge
import java.text.Collator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 主屏状态:浏览器枚举/排序/卸载校验(01 号文档 §3.2 行为定义)+
 * 远程偏好读写经 PrefsBridge(模块未激活时写失败给反馈,不再被动 toast)。
 */
class PickerViewModel(app: Application) : AndroidViewModel(app) {

    data class BrowserEntry(
        val label: String,
        val pkg: String,
        val icon: Drawable?,
    )

    data class UiMessage(val res: Int, val arg: String? = null)

    data class UiState(
        val browsers: List<BrowserEntry> = emptyList(),
        val loadingBrowsers: Boolean = true,
        val target: String? = null,
        val bound: Boolean = false,
        val metisInScope: Boolean = false,
        val framework: String? = null,
        val rules: Map<String, Boolean> = emptyMap(),
        val message: UiMessage? = null,
    )

    private val _ui = MutableStateFlow(UiState(rules = defaultRules(null)))
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            PrefsBridge.state.collect { st ->
                val prefs = st.prefs
                _ui.update { current ->
                    // 已绑定时偏好值是权威来源(含被清空的情况);未绑定时保留当前值
                    val remoteTarget = if (st.bound && prefs != null) {
                        prefs.getString(RedirectPrefs.KEY_TARGET, null)?.trim()?.takeIf { it.isNotEmpty() }
                    } else {
                        current.target
                    }
                    current.copy(
                        bound = st.bound,
                        metisInScope = st.metisInScope,
                        framework = st.frameworkLabel,
                        target = remoteTarget,
                        rules = defaultRules(prefs),
                    )
                }
                validateTarget()
            }
        }
        loadBrowsers()
    }

    /** 浏览器枚举:VIEW(https + BROWSABLE, MATCH_DEFAULT_ONLY)∪ WEB_SEARCH,Collator 本地化排序,按包名去重 */
    private fun loadBrowsers() {
        viewModelScope.launch {
            val entries = withContext(Dispatchers.IO) { queryBrowsers() }
            _ui.update { it.copy(browsers = entries, loadingBrowsers = false) }
            validateTarget()
        }
    }

    fun refresh() {
        _ui.update { it.copy(loadingBrowsers = true) }
        loadBrowsers()
    }

    /** 选择目标浏览器;null = 禁用重定向(列表头禁用项) */
    fun select(pkg: String?) {
        val ok = PrefsBridge.saveTarget(pkg)
        if (!ok) {
            _ui.update { it.copy(message = UiMessage(R.string.save_failed)) }
            return
        }
        _ui.update { current ->
            current.copy(
                target = pkg,
                message = pkg?.let {
                    UiMessage(R.string.selection_saved, labelOf(pkg))
                },
            )
        }
    }

    fun toggleRule(key: String, value: Boolean) {
        if (PrefsBridge.saveRule(key, value)) {
            _ui.update { it.copy(rules = it.rules + (key to value)) }
        } else {
            _ui.update { it.copy(message = UiMessage(R.string.save_failed)) }
        }
    }

    fun requestScope() {
        PrefsBridge.requestScope { approved, message ->
            viewModelScope.launch {
                _ui.update {
                    it.copy(
                        message = if (approved) {
                            UiMessage(R.string.request_scope_approved)
                        } else {
                            UiMessage(R.string.request_scope_failed, message ?: "")
                        },
                    )
                }
            }
        }
    }

    fun consumeMessage() {
        _ui.update { it.copy(message = null) }
    }

    /** 已保存目标被卸载时自动清除(旧模块 onResume 校验行为) */
    private fun validateTarget() {
        val state = _ui.value
        val target = state.target ?: return
        if (state.browsers.isEmpty()) return
        if (state.browsers.none { it.pkg == target }) {
            PrefsBridge.saveTarget(null)
            _ui.update { it.copy(target = null, message = UiMessage(R.string.target_uninstalled)) }
        }
    }

    private fun labelOf(pkg: String): String =
        _ui.value.browsers.firstOrNull { it.pkg == pkg }?.label ?: pkg

    private fun defaultRules(prefs: android.content.SharedPreferences?): Map<String, Boolean> = mapOf(
        RedirectPrefs.KEY_RULE_PROTOCOL to (prefs?.getBoolean(RedirectPrefs.KEY_RULE_PROTOCOL, true) ?: true),
        RedirectPrefs.KEY_RULE_TEXT_SEARCH to (prefs?.getBoolean(RedirectPrefs.KEY_RULE_TEXT_SEARCH, true) ?: true),
        RedirectPrefs.KEY_RULE_HEYTAP_HTTP to (prefs?.getBoolean(RedirectPrefs.KEY_RULE_HEYTAP_HTTP, true) ?: true),
        RedirectPrefs.KEY_RULE_BROWSER_CARD to (prefs?.getBoolean(RedirectPrefs.KEY_RULE_BROWSER_CARD, false) ?: false),
        RedirectPrefs.KEY_VERBOSE_LOG to (prefs?.getBoolean(RedirectPrefs.KEY_VERBOSE_LOG, false) ?: false),
    )

    private fun queryBrowsers(): List<BrowserEntry> {
        val context = getApplication<Application>()
        val pm = context.packageManager

        val viewIntent = Intent(Intent.ACTION_VIEW)
            .setData(Uri.parse("https://example.com"))
            .addCategory(Intent.CATEGORY_BROWSABLE)
        val webSearchIntent = Intent(Intent.ACTION_WEB_SEARCH)

        val byPackage = LinkedHashMap<String, ResolveInfo>()
        for (ri in query(pm, viewIntent) + query(pm, webSearchIntent)) {
            val pkg = ri.activityInfo?.packageName ?: continue
            byPackage.putIfAbsent(pkg, ri)
        }

        val collator = Collator.getInstance()
        return byPackage.map { (pkg, ri) ->
            val label = try {
                ri.loadLabel(pm)?.toString()?.trim().takeIf { !it.isNullOrEmpty() } ?: pkg
            } catch (_: Throwable) {
                pkg
            }
            val icon = try {
                ri.loadIcon(pm)
            } catch (_: Throwable) {
                null
            }
            BrowserEntry(label, pkg, icon)
        }.sortedWith(compareBy(collator) { it.label })
    }

    private fun query(pm: PackageManager, intent: Intent): List<ResolveInfo> = try {
        if (Build.VERSION.SDK_INT >= 33) {
            pm.queryIntentActivities(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }
    } catch (_: Throwable) {
        emptyList()
    }
}
