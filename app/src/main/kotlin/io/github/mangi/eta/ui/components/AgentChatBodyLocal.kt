package io.github.mangi.eta.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import top.yukonga.miuix.kmp.theme.MiuixTheme
import io.github.mangi.eta.ui.components.chat.AgentTimelineEntry

// fork 本地新增：底部跟随/滚动指示器调参与组件、首行文字探测、时间线缓存。

// 键盘抬升用更快的一套位移：系统键盘弹起约 0.25s 走完全程，正文要贴着输入框一起上去，
// 不能沿用流式跟底那套「慢慢追持续增长文字」的曲线（原地 0.5s 左右才收口，观感迟钝）。
internal const val KEYBOARD_LIFT_RESPONSE_SECONDS = 0.06f

internal const val KEYBOARD_LIFT_MAX_SPEED_DP_PER_SECOND = 2000f

// 停靠后正文首行与顶栏底边的缝：0 就是贴死顶栏，这里按用户指定整体下移 15dp。
// 落点 target、守卫门槛、历史落点三处共用它，改一处即全局一致。
internal val DockGap = 15.dp

// 提前量：守卫比落点早这么多就冻结跟底，用来吃掉「一帧测量滞后 + 一帧跟底位移」。
// 必须小于一个正文行高，否则矮正文会被误判成需要停靠。
internal val DockLookahead = 16.dp

// 列表内容顶部内边距（顶栏高度之外的额外留白）。
internal val ChatTopContentPadding = 14.dp

// 回答条目自身的上下内边距：与 ChatMessageItem 里回答列的 padding(vertical = 8.dp) 必须一致，
// 历史会话首帧落点用它把「条目顶」换算成「首行文本顶」。
internal val AnswerBodyTopInset = 8.dp

// （落点容差已并入 DOCK_FOLLOW 的逐帧闭环：|delta| <= 1px 即视为贴住，不再需要单独的常量。）
// 首行字顶的经验值（dp）：条目顶到首行墨迹上沿。实测在 15 次打开里稳定为 9dp
// （20sp 标题与 16sp 正文同值），把它算进首帧落点，第一帧就贴住目标线，
// 一次 scrollBy 都不用做——滚动修正本身是一次可见位移，能省则省。
// 首行字顶到条目顶的距离（dp）。以前写的 9dp 是估的，20261123 的日志反算出来不对：
// off=56 时首行稳定在 414、目标 428，差 14px，说明实际约 11.5dp。落点整体偏上就是这个少吃掉的 14px。
internal val LandingInkTopEstimate = 11.5.dp

/**
 * 首个 markdown 块的「首行」是不是文字：段落和标题算文字，能靠条目 index 预定位；
 * 代码块、表格、引用、图片、公式、列表的首行各有固定版式（有的根本不是文字），
 * 预定位不成立，直接走原来的逐帧探测。
 */
internal fun firstMarkdownBlockIsText(content: String): Boolean {
    val text = content.trimStart()
    if (text.isEmpty()) return false
    if (text.startsWith("#")) {
        val level = text.takeWhile { it == '#' }.length
        // ATX 标题最多 6 级，且 # 之后必须是空格或行尾；否则只是段落里的普通 #。
        val next = text.getOrNull(level)
        return level in 1..6 && (next == null || next == ' ' || next == '\t')
    }
    return when {
        text.startsWith("```") -> false
        text.startsWith("|") -> false
        text.startsWith(">") -> false
        text.startsWith("![") -> false
        text.startsWith("$$") -> false
        text.startsWith("- ") || text.startsWith("* ") || text.startsWith("+ ") -> false
        Regex("^\\d+\\.\\s").containsMatchIn(text) -> false
        else -> true
    }
}

// 历史落点改成首行基准后的探测帧数（约 0.3 秒）：首块是代码/表格/引用时
// 永远量不到首行字顶，等够这些帧就退回结构落点，不要一直挂在这里。
internal const val SnapFirstLineProbeFrames = 20

// 首行闭环的总帧预算（约 1 秒）：历史打开本身是瞬移，够用即可。
internal const val SnapFirstLineFrameBudget = 60

// 自己发的消息气泡在条目内部的上边距：回溯落点要把它吃掉，气泡才是「贴着顶栏」。
internal val UserBubbleTopInset = 8.dp

// 停靠位移的时间常数（秒）：逐帧指数缓出，前段快、后段收敛，避免观感生硬或像瞬移。
internal const val DockTimeConstantSeconds = 0.085f

// 停靠收口的一次对齐阈值（px）：残差小于它就一次 scrollBy 到位，不再逐帧微调。
// 列表滚动是整像素的，残差不到 1px 时「向上 1px / 向下 1px」会逐帧交替，
// 看上去就是整段正文高频上下抽动。
internal const val DockSnapPx = 3f

// 首行字顶相对条目顶边的合理上限（px）：超过说明这一帧的测量还是上一屏的旧值，不能用。
// 「等正文继续长高」的帧预算：约 0.4 秒。首行在跟底冻结后不会再动，长等无意义。
internal const val WaitForGrowthFrameBudget = 24

// 流式期间的同一份预算：正文一定还在长，这里只防死循环（约 5 秒）。
internal const val StreamingGrowthFrameBudget = 300

// awaitAnswerReachTop 的死等上限（约 15 秒）：落点物理不可达时及时交回外层重试。
internal const val AwaitReachTopFrameBudget = 900

// 收尾落点判定前的稳定帧数：等 LazyColumn 按新正文重新测量完再读滚动几何。
internal const val TailSettleFrameBudget = 3

// 「首行越过顶栏下沿」的判定容差（px）：吃掉整像素舍入，别把刚贴顶误判成越过。
internal const val DockExceedTolerancePx = 2f

// 停靠重试的退避：前几次快速重来，之后指数放大到上限，避免空转烧帧。
internal const val DockRetryBackoffStart = 4

internal const val DockRetryMaxBackoffFrames = 60

// 「用户接管」状态的存活时间：手指离开并静置这么久之后就恢复自动停靠。
// 取 8 秒：连续阅读时会不断拖动、每次拖动都刷新计时，等于一直保护；读完停手后自然恢复。
// 以前这个状态只在 isStreaming false→true 时清除，同一次 run 的多段回答期间根本不清，
// 于是一次拖动 / 一次打字就把后续每一轮都变成「一路跟到底」。
internal const val DockUserInterruptTtlMs = 8_000L

// 停靠看门狗：间隔、偏离容差与纠正次数上限。
internal const val DockWatchdogIntervalMs = 500L

internal val DockWatchdogTolerance = 8.dp

internal const val DockWatchdogLimit = 3

/** 停靠看门狗开关：自动跟顶到流式结束为止，结束后不再自动纠正视口。 */
internal const val DockWatchdogEnabled = false

// 抖动熔断：无人操作时视口方向翻转这么多次，就认定是自动滚动在拉锯。
internal const val ScrollJitterFlipLimit = 5

// 两次翻转之间隔了这么多帧就把计数清零，正常的一次性跳转/缓慢浏览不会被误判。
internal const val ScrollJitterFlipWindowFrames = 30

internal const val ScrollJitterTag = "EtaChatScroll"

// 停靠闭环的进展守卫：连续这么多帧残差没变小就收手（约 0.35 秒）。
internal const val DockStallFrameLimit = 20

internal const val EXPAND_REANCHOR_SUPPRESS_MS = 350L

internal const val BOTTOM_SETTLE_TIMEOUT_MS = 600L

internal class TimelineEntriesCache {
    var entries: List<AgentTimelineEntry> = emptyList()
}

/** 空白首页问候语的放大倍数（约两档字号）。 */
internal const val EmptyGreetingFontScale = 1.3f

/** TextUnit 未指定时不能参与算术，原样返回，避免行高未设置时报错。 */
internal fun TextUnit.scaleIfSpecified(factor: Float): TextUnit =
    if (this == TextUnit.Unspecified) this else this * factor

/** 滚动条宽度、与屏幕边缘的距离与上下留白。 */
private val ScrollIndicatorWidth = 3.dp

internal val ScrollIndicatorEndInset = 2.dp

internal val ScrollIndicatorVerticalInset = 10.dp

private val ScrollIndicatorMinThumb = 36.dp

/** 完全静止后等待多久开始渐隐，避免惯性滚动尾巴让滚动条闪一下。 */
private const val ScrollIndicatorIdleDelayMs = 550L

/**
 * 滚动条跟随采样值的平滑时长。
 *
 * LazyColumn 不暴露总内容高度，采样只能由「已测量过消息项的高度」累计推算；新消息项进入视口时
 * 推算值会小幅跳变，直接绘制就是用户看到的「长度突然一变」。这里用很短的线性补间把跳变磨平，
 * 又不足以让位置明显滞后于手指。
 */
private const val ScrollIndicatorFollowMs = 120

/** 采样结果：滚动进度与滑块长度占比；null 表示内容不足一屏。 */
private data class ScrollIndicatorMetrics(
    val progress: Float,
    val thumbFraction: Float,
)

/**
 * 滚动条采样器：按消息项索引缓存已测量的高度。
 *
 * 只用「当前可见项平均高度 × 总项数」估算总高度时，可见项集合每变一次平均高度就跳一次，
 * 滑块长度随之忽长忽短。缓存后总高度 = 已测量项之和 + 未测量项 × 平均值，随滚动单调收敛，
 * 位置也按像素而不是按项数推进，滚动速度才均匀。
 */
private class ScrollIndicatorSampler {
    /** 已测量高度，按条目 key 而不是 index 缓存。 */
    private val heights = HashMap<Any, Int>()

    /** 在底部实测到的滚动总距离（px）；0 表示还没到底过。 */
    private var calibratedRangePx = 0f
    private var lastTotalItems = 0

    fun sample(state: LazyListState, keys: List<Any>): ScrollIndicatorMetrics? {
        val info = state.layoutInfo
        val totalItems = info.totalItemsCount
        val visible = info.visibleItemsInfo
        if (totalItems <= 0 || visible.isEmpty()) return null

        // 会话切换或整体替换（项数骤减）：缓存与校准都失效，重新估算。
        val shrank = lastTotalItems > 0 && totalItems < lastTotalItems - 20 &&
            totalItems < lastTotalItems * 3 / 5
        if (shrank) {
            heights.clear()
            calibratedRangePx = 0f
        }

        if (heights.size > 6000) heights.clear()
        visible.forEach { item ->
            keys.getOrNull(item.index)?.let { key -> heights[key] = item.size }
        }

        val measuredSum = heights.values.sum().toFloat()
        val average = (measuredSum / heights.size).coerceAtLeast(1f)
        val viewportPx = (info.viewportEndOffset - info.viewportStartOffset)
            .coerceAtLeast(1)
            .toFloat()

        // 分页加载更早消息后项数增加：把新增部分按平均高度并入已校准的总距离。
        // 之前是直接丢弃校准回到推算值，分母一换滑块就会瞬移。
        if (!shrank && calibratedRangePx > 0f && lastTotalItems > 0 && totalItems > lastTotalItems) {
            calibratedRangePx += (totalItems - lastTotalItems) * average
        }
        lastTotalItems = totalItems

        val first = visible.first()
        // 视口顶部内边距（顶栏高度）不算滚动距离，减掉后滑到顶时进度才是 0。
        val firstTopInViewport = (first.offset - info.viewportStartOffset).toFloat()
        val insideFirst = (-firstTopInViewport).coerceIn(0f, first.size.toFloat())
        var beforeFirst = 0f
        for (index in 0 until first.index) {
            beforeFirst += keys.getOrNull(index)?.let { key -> heights[key] }?.toFloat() ?: average
        }
        val scrolledPx = beforeFirst + insideFirst

        // canScrollForward == false 表示已经真的到底：此时「已滚动距离 + 视口高度」就是真实内容高度。
        // 用探底实测值校准，避免高度推算偏高时进度永远停在中间、滑到底也到不了 100%。
        val atBottom = !state.canScrollForward && scrolledPx > 0f
        if (atBottom) calibratedRangePx = scrolledPx
        val contentPx = if (calibratedRangePx > 0f) {
            calibratedRangePx + viewportPx
        } else {
            measuredSum + (totalItems - heights.size).coerceAtLeast(0) * average
        }
        if (contentPx <= viewportPx * 1.02f) return null
        val maxScrollPx = (contentPx - viewportPx).coerceAtLeast(1f)
        return ScrollIndicatorMetrics(
            progress = if (atBottom) 1f else (scrolledPx / maxScrollPx).coerceIn(0f, 1f),
            thumbFraction = (viewportPx / contentPx).coerceIn(0.06f, 1f),
        )
    }
}

/**
 * 会话滚动条。
 *
 * - 只在滚动（拖动或惯性）时显示，停止后渐隐；
 * - 两端圆角，不是直角；
 * - 采样与绘制分离：采样在 snapshotFlow 里做补间，绘制在 Canvas 的 draw 阶段读动画值，
 *   不触发重组，滚动过程中每帧连续更新。
 */
@Composable
internal fun ChatScrollIndicator(
    scrollState: LazyListState,
    itemKeys: List<Any>,
    modifier: Modifier = Modifier,
) {
    val sampler = remember(scrollState) { ScrollIndicatorSampler() }
    val progress = remember { Animatable(0f) }
    val length = remember { Animatable(0f) }
    val visibility = remember { Animatable(0f) }
    val thumbColor = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.32f)

    // 采样流共享给两条补间：位置跟手（直接落位），长度做短补间磨平跳变。
    val samples = remember(scrollState, itemKeys) {
        snapshotFlow { sampler.sample(scrollState, itemKeys) }.distinctUntilChanged()
    }
    LaunchedEffect(samples) {
        samples.collectLatest { sample ->
            val target = sample?.progress ?: return@collectLatest
            progress.snapTo(target)
        }
    }
    LaunchedEffect(samples) {
        samples.collectLatest { sample ->
            val target = sample?.thumbFraction ?: return@collectLatest
            // 差值很大说明是一次跳转（如打开长会话直接到底），直接落位，不做扫动。
            if (abs(target - length.value) > 0.4f) {
                length.snapTo(target)
            } else {
                length.animateTo(
                    targetValue = target,
                    animationSpec = tween(ScrollIndicatorFollowMs, easing = LinearEasing),
                )
            }
        }
    }

    val isScrolling by remember(scrollState) {
        derivedStateOf { scrollState.isScrollInProgress }
    }
    LaunchedEffect(isScrolling) {
        if (isScrolling) {
            visibility.animateTo(1f, tween(durationMillis = 110))
        } else {
            delay(ScrollIndicatorIdleDelayMs)
            visibility.animateTo(0f, tween(durationMillis = 420))
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxHeight()
            .width(ScrollIndicatorWidth),
    ) {
        val opacity = visibility.value
        val fraction = length.value
        if (opacity <= 0.01f || fraction <= 0f) return@Canvas
        val track = size.height
        if (track <= 0f) return@Canvas
        val minThumb = ScrollIndicatorMinThumb.toPx().coerceAtMost(track)
        val thumb = (track * fraction).coerceIn(minThumb, track)
        val top = ((track - thumb) * progress.value).coerceIn(0f, track - thumb)
        drawRoundRect(
            color = thumbColor.copy(alpha = thumbColor.alpha * opacity),
            topLeft = Offset(0f, top),
            size = Size(size.width, thumb),
            cornerRadius = CornerRadius(thumb / 2f),
        )
    }
}
