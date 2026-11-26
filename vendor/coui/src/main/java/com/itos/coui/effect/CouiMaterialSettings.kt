package com.itos.coui.effect

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

internal data class CouiMaterialSettings(val blur: Boolean, val stroke: Boolean)

@Composable
internal fun rememberCouiMaterialSettings(): CouiMaterialSettings {
    val resolver = LocalContext.current.contentResolver
    fun read(): CouiMaterialSettings {
        val powerSave = Settings.Global.getInt(resolver, "low_power", 0) == 1 ||
            Settings.System.getInt(resolver, "super_powersave_mode_state", 0) == 1
        // 没有 Oplus 材质设置的系统继续使用本库公开渲染后端；不按厂商能力表禁用。
        return CouiMaterialSettings(
            blur = !powerSave && Settings.System.getInt(resolver, "system_material_blur_enable", 1) == 1,
            stroke = !powerSave && Settings.System.getInt(resolver, "system_material_stroke_enable", 1) == 1,
        )
    }
    var settings by remember(resolver) { mutableStateOf(read()) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                settings = read()
            }
        }
        resolver.registerContentObserver(Settings.System.getUriFor("system_material_blur_enable"), false, observer)
        resolver.registerContentObserver(Settings.System.getUriFor("system_material_stroke_enable"), false, observer)
        resolver.registerContentObserver(Settings.System.getUriFor("super_powersave_mode_state"), false, observer)
        resolver.registerContentObserver(Settings.Global.getUriFor("low_power"), false, observer)
        settings = read()
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return settings
}
