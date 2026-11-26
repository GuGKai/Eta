package io.github.mangi.eta.ui.app

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Density
import io.github.mangi.eta.data.model.AppearanceSettings
import io.github.mangi.eta.data.model.AppearanceUiStyle
import io.github.mangi.eta.ui.components.TopLayerState
import top.yukonga.miuix.kmp.blur.Backdrop

internal val LocalAppearanceSettings = staticCompositionLocalOf { AppearanceSettings() }
internal val LocalUiStyle = staticCompositionLocalOf { AppearanceUiStyle.Default }
internal val LocalPlatformDensity = staticCompositionLocalOf<Density?> { null }
internal val LocalGlassSceneBackdrop = staticCompositionLocalOf<Backdrop?> { null }
internal val LocalGlassPageBackdrop = staticCompositionLocalOf<Backdrop?> { null }
internal val LocalTopLayer = staticCompositionLocalOf<TopLayerState?> { null }
