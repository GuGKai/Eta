package com.itos.coui.sheet

import android.graphics.Typeface
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.itos.coui.text.couiTextStyle
import com.itos.coui.theme.CouiTheme
import kotlin.math.roundToInt

/** Panel 操作文字沿 COUIMenuItemStyle，保留 dp 字号。 */
@Composable
internal fun rememberSheetButtonTextStyle(): TextStyle {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val weight = 500
    val family = CouiTheme.typography.dialogButtonFontFamily
    val typeface = if (family != null) {
        LocalFontFamilyResolver.current.resolve(family, FontWeight(weight), fontSynthesis = FontSynthesis.None).value as Typeface
    } else remember(weight) { Typeface.create(Typeface.create("sans-serif-medium", Typeface.NORMAL), weight, false) }
    return remember(configuration, density, typeface) {
        val pixels = 14f * density.density
        val spacing = if (Build.VERSION.SDK_INT >= 35) 1.143f else 1f
        couiTextStyle(typeface, weight, pixels.roundToInt().toFloat(), spacing,
            includeFontPadding = true, density = density).copy(letterSpacing = 0.sp)
    }
}
