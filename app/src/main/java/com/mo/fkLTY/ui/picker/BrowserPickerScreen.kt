package com.mo.fkLTY.ui.picker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mo.fkLTY.R
import com.mo.fkLTY.engine.RedirectPrefs

/**
 * 主屏(单屏,03 号文档 §6.1):
 * LargeTopAppBar → 状态卡(激活/作用域/目标)→ 规则开关 → 浏览器列表(禁用项在头)→ 诊断卡。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserPickerScreen(viewModel: PickerViewModel = viewModel()) {
    val ui by viewModel.ui.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    LaunchedEffect(ui.message) {
        val msg = ui.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(
            msg.arg?.let { context.getString(msg.res, it) } ?: context.getString(msg.res)
        )
        viewModel.consumeMessage()
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        // 顶部 padding 动态取自折叠中的大标题栏:静止时内容完整显示在栏下方,
        // 上滑时内容自然滑入栏后(否则列表从 y=0 开始,首屏内容藏在栏后,上滑时"冒出来")
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 8.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "status") {
                StatusCardContent(ui, onRequestScope = viewModel::requestScope)
            }

            item(key = "rules-header") {
                SectionLabel(stringResource(R.string.section_rules))
            }
            items(RULE_KEYS, key = { "rule-$it" }) { key ->
                RuleRowContent(key, ui, onToggle = viewModel::toggleRule)
            }

            item(key = "list-header") {
                SectionLabel(stringResource(R.string.browser_list_header))
            }
            item(key = "disable") {
                BrowserRow(
                    label = stringResource(R.string.disable_redirect),
                    pkg = null,
                    icon = null,
                    selected = ui.target == null,
                    enabled = true,
                    onClick = { viewModel.select(null) },
                )
            }
            if (ui.browsers.isEmpty() && ui.loadingBrowsers) {
                item(key = "loading") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else if (ui.browsers.isEmpty()) {
                item(key = "empty") {
                    BrowserRow(
                        label = stringResource(R.string.no_browsers),
                        pkg = null,
                        icon = null,
                        selected = false,
                        enabled = false,
                        onClick = {},
                    )
                }
            } else {
                items(ui.browsers, key = { "browser-${it.pkg}" }) { entry ->
                    BrowserRow(
                        label = entry.label,
                        pkg = entry.pkg,
                        icon = entry.icon,
                        selected = ui.target == entry.pkg,
                        enabled = true,
                        onClick = { viewModel.select(entry.pkg) },
                    )
                }
            }

            item(key = "diag-header") {
                SectionLabel(stringResource(R.string.section_diagnostics))
            }
            item(key = "diag") {
                DiagnosticsCard(
                    framework = ui.framework,
                    scope = if (ui.bound) {
                        stringResource(R.string.diag_scope, ui.scopeLabel())
                    } else {
                        null
                    },
                )
            }
            item(key = "about") {
                val context = LocalContext.current
                val versionName = remember(context.packageName) {
                    runCatching {
                        context.packageManager.getPackageInfo(context.packageName, 0).versionName
                    }.getOrNull() ?: ""
                }
                AboutCard(versionName, context.packageName)
            }
            item(key = "footer-space") {
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

private fun PickerViewModel.UiState.scopeLabel(): String =
    if (metisInScope) "com.oplus.metis" else ""

private val RULE_KEYS = listOf(
    RedirectPrefs.KEY_RULE_PROTOCOL,
    RedirectPrefs.KEY_RULE_TEXT_SEARCH,
    RedirectPrefs.KEY_RULE_HEYTAP_HTTP,
    RedirectPrefs.KEY_RULE_BROWSER_CARD,
    RedirectPrefs.KEY_VERBOSE_LOG,
)

@Composable
private fun StatusCardContent(ui: PickerViewModel.UiState, onRequestScope: () -> Unit) {
    when {
        !ui.bound -> StatusCard(
            leading = Icons.Outlined.Warning,
            title = stringResource(R.string.status_inactive_title),
            subtitle = stringResource(R.string.status_inactive_subtitle),
        )

        !ui.metisInScope -> StatusCard(
            leading = Icons.Outlined.Extension,
            title = stringResource(R.string.status_scope_missing_title),
            subtitle = stringResource(R.string.status_scope_missing_subtitle),
            trailing = { RequestScopeButton(onClick = onRequestScope) },
        )

        ui.target == null -> StatusCard(
            leading = Icons.Outlined.PowerSettingsNew,
            title = stringResource(R.string.status_disabled_title),
            subtitle = stringResource(R.string.status_disabled_subtitle),
        )

        else -> {
            val label = ui.browsers.firstOrNull { it.pkg == ui.target }?.label ?: ui.target
            StatusCard(
                leading = Icons.Outlined.CheckCircle,
                title = stringResource(R.string.status_active_title),
                subtitle = stringResource(R.string.status_active_subtitle, label),
            )
        }
    }
}

@Composable
private fun RuleRowContent(key: String, ui: PickerViewModel.UiState, onToggle: (String, Boolean) -> Unit) {
    val checked = ui.rules[key] ?: false
    when (key) {
        RedirectPrefs.KEY_RULE_PROTOCOL -> RuleRow(
            title = stringResource(R.string.rule_protocol_title),
            subtitle = stringResource(R.string.rule_protocol_subtitle),
            icon = Icons.Outlined.Link,
            checked = checked,
            onCheckedChange = { onToggle(key, it) },
        )

        RedirectPrefs.KEY_RULE_TEXT_SEARCH -> RuleRow(
            title = stringResource(R.string.rule_text_search_title),
            subtitle = stringResource(R.string.rule_text_search_subtitle),
            icon = Icons.Outlined.Search,
            checked = checked,
            onCheckedChange = { onToggle(key, it) },
        )

        RedirectPrefs.KEY_RULE_HEYTAP_HTTP -> RuleRow(
            title = stringResource(R.string.rule_heytap_http_title),
            subtitle = stringResource(R.string.rule_heytap_http_subtitle),
            icon = Icons.Outlined.Public,
            checked = checked,
            onCheckedChange = { onToggle(key, it) },
        )

        RedirectPrefs.KEY_RULE_BROWSER_CARD -> RuleRow(
            title = stringResource(R.string.rule_browser_card_title),
            subtitle = stringResource(R.string.rule_browser_card_subtitle),
            icon = Icons.Outlined.Explore,
            checked = checked,
            onCheckedChange = { onToggle(key, it) },
        )

        RedirectPrefs.KEY_VERBOSE_LOG -> RuleRow(
            title = stringResource(R.string.verbose_log_title),
            subtitle = stringResource(R.string.verbose_log_subtitle),
            icon = Icons.Outlined.Terminal,
            checked = checked,
            onCheckedChange = { onToggle(key, it) },
        )
    }
}
