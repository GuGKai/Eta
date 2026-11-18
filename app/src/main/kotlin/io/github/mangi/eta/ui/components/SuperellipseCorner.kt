package io.github.mangi.eta.ui.components

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * 会话页外框的超椭圆（continuous curvature / squircle）圆角。
 *
 * 正圆弧的曲率在「直边接圆角」那一点是突变的，超椭圆把曲率摊开，过渡更连续；
 * COUI 的输入框用的就是这一族形状（库里的参数叫 cornerWeight）。
 * 形状由 `|x/r|^n + |y/r|^n = 1` 描述：n = 2 是正圆，n 越大越方。
 *
 * 指数怎么来的（来自本机截图实测，不是拍脑袋）：
 * - 输入框现在是 `CouiShape(25.25dp, 2.3f)`，实测圆角直边处半径 101px、
 *   45° 斜角处 106.2px，外凸 +5.15% → 反解等效 n ≈ 2.34；
 * - 外框半径恒等于机身圆角（133px），内框半径固定 101px（33.25 - 8 = 25.25dp），
 *   唯一能调的就是外框指数。取 n = 2.25 时径向间距 31.96~32.02px、
 *   法向最短间距 31.96~32.02px，也就是**整条轮廓恒等 8.00dp**；
 * - 对照：外框仍是正圆时斜角处只有 26.8px（6.70dp），差 1.3dp，
 *   正是肉眼看到的那道「不贴合」。
 */
internal object SuperellipseCorner {
    /** 会话页外框指数，推导见上面注释。 */
    const val PageExponent = 2.25f

    /** 每个圆角的采样段数；133px 半径下每段约 2px，配合抗锯齿看不出折线。 */
    const val Segments = 48
}

/**
 * 追加一段超椭圆圆角。
 *
 * [cx]、[cy] 是这段圆角的圆心；[sx]、[sy] 是圆心指向「外」的符号（左/上为 -1，右/下为 +1）。
 * θ 从 0 扫到 π/2：起点落在一条直边上，终点落在另一条直边上；[reverse] 用于反向遍历。
 */
private fun Path.superellipseCorner(
    cx: Float,
    cy: Float,
    radius: Float,
    exponent: Float,
    sx: Float,
    sy: Float,
    reverse: Boolean = false,
) {
    if (radius <= 0f) return
    val power = 2f / exponent
    val steps = SuperellipseCorner.Segments
    for (i in 0..steps) {
        val t = if (reverse) (steps - i) else i
        val theta = (PI / 2.0) * t / steps
        val c = cos(theta).toFloat().pow(power)
        val s = sin(theta).toFloat().pow(power)
        val x = cx + sx * radius * c
        val y = cy + sy * radius * s
        if (i == 0 && !reverse) moveTo(x, y) else lineTo(x, y)
    }
}

/**
 * 会话页外框路径：左侧两个角是超椭圆，右侧两个角保持直角。
 *
 * 侧栏打开时这张卡片被推到右边、右侧落到屏幕外，只有左缘两个角露在画面里，
 * 所以只需要（也只应该）把这两个角画出来。
 */
internal fun superellipsePagePath(size: Size, radius: Float, exponent: Float): Path {
    val r = radius.coerceIn(0f, minOf(size.width, size.height) / 2f)
    val w = size.width
    val h = size.height
    return Path().apply {
        if (r <= 0f) {
            addRect(Rect(0f, 0f, w, h))
            return@apply
        }
        moveTo(0f, h - r)
        lineTo(0f, r)
        superellipseCorner(cx = r, cy = r, radius = r, exponent = exponent, sx = -1f, sy = -1f)
        lineTo(w, 0f)
        lineTo(w, h)
        lineTo(r, h)
        superellipseCorner(
            cx = r,
            cy = h - r,
            radius = r,
            exponent = exponent,
            sx = -1f,
            sy = 1f,
            reverse = true,
        )
        close()
    }
}

/**
 * 分界阴影带的外圈路径：轮廓整体向外膨 [inflate]，四角都圆。
 *
 * 严格说超椭圆的等距外扩不再是超椭圆，这里用「半径加大、指数不变」近似；
 * 这条带子是羽化渐变，肉眼分辨不出这层误差。
 */
internal fun superellipseInflatedPath(
    size: Size,
    radius: Float,
    inflate: Float,
    exponent: Float,
): Path {
    val w = size.width + inflate * 2f
    val h = size.height + inflate * 2f
    val rr = (radius + inflate).coerceIn(0f, minOf(w, h) / 2f)
    return Path().apply {
        if (rr <= 0f) {
            addRect(Rect(-inflate, -inflate, size.width + inflate, size.height + inflate))
            return@apply
        }
        moveTo(-inflate, h - inflate - rr)
        lineTo(-inflate, -inflate + rr)
        superellipseCorner(
            cx = -inflate + rr,
            cy = -inflate + rr,
            radius = rr,
            exponent = exponent,
            sx = -1f,
            sy = -1f,
        )
        lineTo(w - inflate, -inflate)
        lineTo(w - inflate, h - inflate)
        lineTo(-inflate + rr, h - inflate)
        superellipseCorner(
            cx = -inflate + rr,
            cy = h - inflate - rr,
            radius = rr,
            exponent = exponent,
            sx = -1f,
            sy = 1f,
            reverse = true,
        )
        close()
    }
}

/**
 * 分界描边路径：只描左竖边和上下两个圆角，顶边、底边、右侧都不描。
 *
 * [inset] 是半个线宽；半径取 r - inset、圆心不动，等于把轮廓整体内缩，
 * 整条描边都落在页面内侧，不会被裁掉一半而发虚。
 */
internal fun superellipsePageEdgeStrokePath(
    size: Size,
    radius: Float,
    exponent: Float,
    inset: Float,
): Path {
    val r = radius.coerceIn(0f, minOf(size.width, size.height) / 2f)
    val inner = r - inset
    val h = size.height
    return Path().apply {
        if (inner <= 0f) {
            moveTo(inset, 0f)
            lineTo(inset, h)
            return@apply
        }
        superellipseCorner(cx = r, cy = r, radius = inner, exponent = exponent, sx = -1f, sy = -1f)
        moveTo(inset, r)
        lineTo(inset, h - r)
        superellipseCorner(
            cx = r,
            cy = h - r,
            radius = inner,
            exponent = exponent,
            sx = -1f,
            sy = 1f,
        )
    }
}
