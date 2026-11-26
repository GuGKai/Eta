package io.github.mangi.eta.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import io.github.mangi.eta.R
import io.github.mangi.eta.ui.screens.settings.SettingsSystemStatus

// fork 本地新增：设置页系统状态的内存缓存。

@Volatile
internal var cachedSettingsSystemStatus: SettingsSystemStatus? = null
