package com.itos.coui.sheet

import android.graphics.Paint
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.toSize
import com.itos.coui.backdrop.CouiBackdrop
import com.itos.coui.backdrop.couiBackdrop
import com.itos.coui.effect.CouiContour
import com.itos.coui.effect.CouiContourStyle
import com.itos.coui.effect.LocalCouiContourGlowEnabled
import com.itos.coui.dialog.CouiDialogColors
import com.itos.coui.dialog.CouiDialogMaterial
import com.itos.coui.effect.rememberCouiMaterialEffect
import com.itos.coui.menu.rememberCouiKawaseBlur
import com.itos.coui.shape.CouiShape

@Composable
internal fun CouiSheetSurface(
    modifier: Modifier,
    material: CouiDialogMaterial?,
    backdrop: CouiBackdrop?,
    colors: CouiDialogColors,
    dark: Boolean,
    themeTint: Color,
    surfaceBackdrop: CouiBackdrop,
    windowCoordinates: () -> LayoutCoordinates?,
    panelCoordinates: () -> LayoutCoordinates?,
    translationY: () -> Float,
    content: @Composable BoxScope.() -> Unit,
) {
    // 表面在组合期间保留外层窗口源，退场结束后与面板一并释放。
    val materialEffect = if (Build.VERSION.SDK_INT >= 33 && material != null) {
        rememberCouiMaterialEffect(
            material.mix, material.blend, material.blendMode,
            rgbMix = true, slightDither = material.blurRadius > 0,
            themeTint = themeTint,
        )
    } else null
    val blur = if (material != null) {
        val source = checkNotNull(backdrop)
        source.RetainWhileComposed()
        rememberCouiKawaseBlur(source, colors.surface, material.blurRadius)
    } else null
    val surfaceEffect = remember(materialEffect) { materialEffect?.asComposeRenderEffect() }
    val materialLayer = if (surfaceEffect != null) {
        val result = rememberGraphicsLayer()
        DisposableEffect(result, surfaceEffect) {
            result.clip = true
            result.renderEffect = surfaceEffect
            onDispose { }
        }
        result
    } else null
    val contour = LocalCouiContourGlowEnabled.current
    val edge = remember(material != null, contour) {
        if (material != null && contour && Build.VERSION.SDK_INT >= 33) CouiContour(style = CouiContourStyle.Panel) else null
    }
    val edgePaint = remember(edge) { edge?.let { Paint(Paint.ANTI_ALIAS_FLAG) } }
    Layout(modifier = modifier.drawWithCache {
        val fullSize = size.copy(height = size.height + CouiSheetDefaults.bottomPadding.roundToPx())
        if (Build.VERSION.SDK_INT >= 33 && edge != null && edgePaint != null) {
            // Clock 的轮廓 Drawable 用 PixelOffset 取半径，线宽用 PixelSize。
            val edgeRadiusPx = CouiSheetDefaults.cornerRadius.toPx().toInt().toFloat()
            edgePaint.shader = edge.shader(fullSize, density, edgeRadiusPx, CouiSheetDefaults.edgeWeight,
                Color.Transparent, CouiSheetDefaults.innerGlowAlpha(dark), CouiSheetDefaults.lineAlpha(dark), 1f,
                CouiSheetDefaults.lineWidth(dark).roundToPx().toFloat(), bottomRadiusPx = 0f)
        }
        onDrawWithContent {
            drawContent()
            // NEW_G2 分支先恢复内容的路径裁剪，再由 Drawable 自身处理轮廓覆盖率。
            if (edgePaint != null) drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawRect(0f, 0f, fullSize.width, fullSize.height, edgePaint)
            }
        }
    }, content = {
        // 材质区域用 PixelOffset 半径；背景采样层保留完整、未裁剪的纯表面。
        Box(Modifier.graphicsLayer().drawWithCache {
            val radius = CouiSheetDefaults.cornerRadius.toPx()
            val path = sheetPath(size, layoutDirection, if (blur != null) radius.toInt().toFloat() else radius)
            onDrawWithContent { clipPath(path) { this@onDrawWithContent.drawContent() } }
        }.couiBackdrop(surfaceBackdrop).drawWithCache {
            val window = if (blur != null) windowCoordinates() else null
            val panel = if (blur != null) panelCoordinates() else null
            if (Build.VERSION.SDK_INT >= 33 && blur != null && materialLayer != null &&
                window?.isAttached == true && panel?.isAttached == true &&
                window.size.width > 0 && window.size.height > 0) {
                // RenderEngine 以窗口与显示区的交集生成模糊图，面板区域再按位移裁出对应位置。
                val windowToPanel = Matrix().also { panel.transformFrom(window, it) }
                val prepared = blur.prepare(this, layoutDirection, window.size.toSize(), window)
                val recordingDensity = Density(density, fontScale)
                if (prepared != null) {
                    val panelSize = IntSize(size.width.toInt(), size.height.toInt())
                    val panelLayoutDirection = layoutDirection
                    onDrawBehind {
                        val offset = translationY()
                        // 先放大模糊图，再在面板像素上执行混色和抖动，避免低分辨率材质量化形成色块。
                        materialLayer.record(recordingDensity, panelLayoutDirection, panelSize) {
                            withTransform({
                                translate(top = -offset)
                                transform(windowToPanel)
                                scale(prepared.scaleX, prepared.scaleY, Offset.Zero)
                            }) {
                                drawLayer(prepared.layer)
                            }
                        }
                        drawLayer(materialLayer)
                    }
                } else if (material?.blurRadius == 0) {
                    materialLayer.record(recordingDensity, layoutDirection, window.size) {
                        blur.draw(this, window)
                    }
                    onDrawBehind {
                        withTransform({
                            translate(top = -translationY())
                            transform(windowToPanel)
                        }) {
                            drawLayer(materialLayer)
                        }
                    }
                } else {
                    onDrawBehind { drawRect(colors.surface) }
                }
            } else {
                onDrawBehind { drawRect(colors.surface) }
            }
        })
        Box(Modifier.drawWithCache {
            val fullSize = size.copy(height = size.height + CouiSheetDefaults.bottomPadding.roundToPx())
            val path = sheetPath(fullSize, layoutDirection, CouiSheetDefaults.cornerRadius.toPx())
            onDrawWithContent { clipPath(path) { this@onDrawWithContent.drawContent() } }
        }, content = content)
    }) { measurables, constraints ->
        val contentPlaceable = measurables[1].measure(constraints)
        val fullHeight = contentPlaceable.height + CouiSheetDefaults.bottomPadding.roundToPx()
        val backgroundPlaceable = measurables[0].measure(Constraints.fixed(contentPlaceable.width, fullHeight))
        // 对父级与运动只报告可见高度，不能把原厂的额外测量 padding 再计进入退场距离。
        layout(contentPlaceable.width, contentPlaceable.height) {
            backgroundPlaceable.place(0, 0)
            contentPlaceable.place(0, 0)
        }
    }
}

private fun Density.sheetPath(size: Size, layoutDirection: LayoutDirection, radiusPx: Float): Path {
    val shape = CouiShape(radiusPx.toDp(), CouiSheetDefaults.edgeWeight)
    val roundedPath = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@sheetPath)) }
    val bottom = Path().apply { addRect(Rect(0f, size.height / 2f, size.width, size.height)) }
    return Path.combine(PathOperation.Union, roundedPath, bottom)
}
