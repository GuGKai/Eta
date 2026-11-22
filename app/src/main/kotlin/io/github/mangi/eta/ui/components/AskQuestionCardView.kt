package io.github.mangi.eta.ui.components

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.itos.coui.checkbox.Checkbox
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.layout.Divider
import com.itos.coui.radiobutton.RadioButton
import com.itos.coui.segmentbutton.CouiSegmentedControlDefaults
import com.itos.coui.segmentbutton.SegmentedControl
import com.itos.coui.shape.CouiCapsuleShape
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.theme.CouiTheme
import io.github.mangi.eta.agent.model.AskQuestionAnswer
import io.github.mangi.eta.agent.model.AskQuestionCard
import io.github.mangi.eta.ui.components.coui.CouiPreference
import io.github.mangi.eta.ui.components.coui.CouiTextField
import kotlin.math.max

/** 提问卡避让键盘的诊断标签：`adb logcat -s AskReveal` 能看到量到的几何与补偿量。 */
private const val AskRevealTag = "AskReveal"

/**
 * 模型提问卡片（COUI 版，对齐 OpenMausBot 的 QuestionCardView 行为）。
 *
 * - 卡片宽度与聊天正文同宽，不再额外留两侧外边距
 * - 卡片底色取建议胶囊那套半透明色，亮色下不会和会话页背景同色
 * - 多题时顶部是分段控件，答过的题带 ✓；选项行配 RadioButton / Checkbox
 * - 「其他」是行内 COUI 输入框，点一下就算选中，不必等敲进字去
 * - 键盘弹起把这一格盖住时，按重叠量做最小幅度上抬；没被盖住就一点不动
 * - 底部一行「回答并继续」，把所有答案一次发出，只带所选内容
 */
@Composable
internal fun AskQuestionCardView(
    card: AskQuestionCard,
    answeredText: String?,
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val questions = card.questions
    if (questions.isEmpty()) return

    val dark = CouiTheme.isDark
    val colors = CouiPageDefaults.themeColors(dark)
    // 与 EtaChoiceChip（回答末尾的建议胶囊）同一套容器色。
    val chipColor = if (dark) Color(0x1AFFFFFF) else Color(0x0D000000)
    val segmentColor = if (dark) Color(0x1FFFFFFF) else Color(0x14000000)
    val indicatorColor = if (dark) Color(0x33FFFFFF) else Color(0xEEFFFFFF)

    val settledAnswer = answeredText?.takeIf { it.isNotBlank() }

    // 每题勾选的选项，以及「其他」那一格的行内输入框（一题一个，切题不丢）。
    val picked = remember(card) { mutableStateMapOf<Int, Set<String>>() }
    // 「其他」这一格：点一下输入框就算选中，不用等真的敲进字去。
    val otherPicked = remember(card) { mutableStateMapOf<Int, Boolean>() }
    val editors = remember(card) { questions.map { TextFieldState("") } }
    var active by rememberSaveable(card.version) { mutableStateOf(0) }

    fun customText(position: Int): String = editors[position].text.toString().trim()

    fun answersFor(position: Int): List<String> {
        val custom = customText(position)
        // 单选时自由输入直接顶替选项，两者不会同时进答案。
        if (custom.isNotEmpty() && !questions[position].allowsMultiple) return listOf(custom)
        val chosen = picked[position].orEmpty().sorted().toMutableList()
        if (custom.isNotEmpty()) chosen.add(custom)
        return chosen
    }

    val index = active.coerceIn(0, questions.lastIndex)
    val current = questions[index]
    val answeredCount = questions.indices.count { answersFor(it).isNotEmpty() }
    val complete = answeredCount == questions.size

    // 键盘弹起后主输入框整块上移，会把卡片下半部分盖住。列表视口是整屏（内容从输入框背后穿过去），
    // LazyColumn 自己不会为「被浮层盖住」滚动，所以这里自己量窗口几何：
    // 底部被「主输入框 + 键盘」占掉的高度 = 窗口高 − 浮层顶边；
    // 一直抬到「回答并继续」那一行（卡片最下面一行）露出来为止，没被压住时一个像素都不动。
    val reveal = LocalChatReveal.current
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val revealGapPx = with(density) { 8.dp.toPx() }
    val windowHeightPx = with(density) { LocalWindowInfo.current.containerSize.height.toFloat() }
    var focusedPosition by remember { mutableStateOf(-1) }
    var cardBottomPx by remember { mutableStateOf(0f) }
    var fieldHeightPx by remember { mutableIntStateOf(0) }
    val revealActive = settledAnswer == null && focusedPosition == index

    // 只要这一格还在输入状态就一直盯着，而不是「聚焦那一下看几秒」：
    // 键盘落下再弹起、输入换行变高都会重新对齐，不用重新点一次输入框。
    // 触发源只有「浮层顶边」和「输入框自身高度」，单纯滚动不触发，所以盯着也不会和手动滑动抢。
    LaunchedEffect(reveal, revealActive) {
        val env = reveal
        if (env == null) {
            if (focusedPosition == index) {
                Log.i(AskRevealTag, "skip: 卡片没有拿到 ChatRevealEnv（没接上图层的浮层几何）")
            }
            return@LaunchedEffect
        }
        if (!revealActive) return@LaunchedEffect
        snapshotFlow { env.overlayTopPx.value to fieldHeightPx }
            .collect { (top, height) ->
                if (top.isNaN()) return@collect
                // 手指正在滑列表时不补：这时候抬一下等于把用户刚翻走的内容又拽回来。
                if (env.userDragging.value) return@collect
                val cardBottom = cardBottomPx
                if (cardBottom <= 0f) return@collect
                val overlap = cardBottom + revealGapPx - top
                Log.i(
                    AskRevealTag,
                    "check: window=" + windowHeightPx + " occupiedBottom=" + (windowHeightPx - top) +
                        " top=" + top + " cardBottom=" + cardBottom +
                        " overlap=" + overlap + " fieldHeight=" + height,
                )
                if (overlap <= 1f) return@collect
                env.scrollBy(overlap)
                // 滚动要到下一帧才落到布局，这里再确认一次：键盘还在动画时不能欠账。
                withFrameNanos { }
                val rest = cardBottomPx + revealGapPx - env.overlayTopPx.value
                if (!env.overlayTopPx.value.isNaN() && rest > 1f) {
                    Log.i(AskRevealTag, "check2: rest=" + rest)
                    env.scrollBy(rest)
                }
            }
    }

    // 手指一滑列表就把这一格的输入状态丢掉：会话页拖动时只会收键盘、从不清焦点，
    // 留着会让描边一直亮着，也和「切选项卡就丢输入状态」不一致。
    LaunchedEffect(reveal, revealActive) {
        val env = reveal ?: return@LaunchedEffect
        if (!revealActive) return@LaunchedEffect
        snapshotFlow { env.userDragging.value }
            .collect { dragging ->
                if (!dragging) return@collect
                Log.i(AskRevealTag, "drag: 收掉提问卡里的输入状态")
                focusManager.clearFocus()
                keyboard?.hide()
                focusedPosition = -1
            }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(CouiPageDefaults.shape)
                .background(chipColor, CouiPageDefaults.shape)
                // 卡片底边 = 最后一行「回答并继续」的底边；键盘避让就抬到它露出来为止。
                .onGloballyPositioned { cardBottomPx = it.boundsInWindow().bottom },
        ) {
            if (settledAnswer != null) {
                val answers = AskQuestionAnswer.perQuestion(settledAnswer, questions.size)
                questions.forEachIndexed { position, question ->
                    if (position > 0) CardDivider()
                    CouiPreference(
                        title = answers.getOrElse(position) { "" }.ifBlank { "（未作答）" },
                        summary = question.question,
                        showChevron = false,
                    )
                }
            } else {
                if (questions.size > 1) {
                    QuestionTabs(
                        labels = questions.mapIndexed { position, question ->
                            val mark = if (answersFor(position).isNotEmpty()) "✓ " else ""
                            mark + question.tabLabel(position + 1)
                        },
                        selectedIndex = index,
                        onSelected = { next ->
                            if (next != index) {
                                // 换题就把输入状态丢掉：留着会让新选项卡里的那一格看起来还在打字，
                                // 键盘也一直不落。文字本身保留，切回来还能接着改。
                                focusManager.clearFocus()
                                keyboard?.hide()
                                focusedPosition = -1
                                active = next
                            }
                        },
                        dark = dark,
                        containerColor = segmentColor,
                        indicatorColor = indicatorColor,
                    )
                    CardDivider()
                }

                CouiPreference(
                    title = current.question,
                    showChevron = false,
                )
                CardDivider()

                current.options.forEachIndexed { position, option ->
                    if (position > 0) CardDivider()
                    val selected = picked[index].orEmpty().contains(option.label) &&
                        (current.allowsMultiple || customText(index).isEmpty())
                    CouiPreference(
                        title = option.label,
                        summary = option.detail,
                        showChevron = false,
                        onClick = {
                            val chosen = picked[index].orEmpty()
                            picked[index] = when {
                                !current.allowsMultiple -> setOf(option.label)
                                option.label in chosen -> chosen - option.label
                                else -> chosen + option.label
                            }
                            // 单选时点选项等于放弃「其他」，把输入框清空，避免两个答案打架。
                            if (!current.allowsMultiple) {
                                otherPicked[index] = false
                                editors[index].edit { replace(0, length, "") }
                            }
                        },
                        trailing = { Mark(checked = selected, multiple = current.allowsMultiple) },
                    )
                }

                // 其他：模型没想到的说法，点输入框就算选中这一项，写什么在下面那格。
                CardDivider()
                val typed = customText(index)
                CouiPreference(
                    title = "其他",
                    showChevron = false,
                    trailing = {
                        Mark(
                            checked = otherPicked[index] == true || typed.isNotEmpty(),
                            multiple = current.allowsMultiple,
                        )
                    },
                )
                CouiTextField(
                    state = editors[index],
                    modifier = Modifier
                        .padding(
                            start = CouiPageDefaults.rowPadding,
                            end = CouiPageDefaults.rowPadding,
                            top = 6.dp,
                            bottom = 12.dp,
                        )
                        // 只关心自身高度：换行变高时重新对齐一次。
                        .onGloballyPositioned { coordinates ->
                            fieldHeightPx = coordinates.size.height
                        },
                    placeholder = "我的建议",
                    // 固定单行：多行输入框自己会吃竖直拖动，压在它上面时会话列表就滑不动。
                    // 单行时文字超出宽度由输入框自己向左平移，光标始终留在可见区域。
                    singleLine = true,
                    // 单行默认会带一个清除「×」，这里不要。
                    showClearButton = false,
                    onFocusChanged = { focused ->
                        focusedPosition = if (focused) index else -1
                        if (focused) {
                            // 点一下输入框就把「其他」勾上；单选时选项跟着让位，多选允许叠加。
                            otherPicked[index] = true
                            if (!current.allowsMultiple) picked[index] = emptySet()
                        }
                    },
                )

                CardDivider()
                CouiPreference(
                    title = if (complete) "回答并继续" else "还差 ${questions.size - answeredCount} 题",
                    titleColor = if (complete) colors.accent else colors.disabled,
                    showChevron = false,
                    enabled = complete,
                    onClick = {
                        val formatted = AskQuestionAnswer.format(
                            questions = questions,
                            answers = questions.indices.map(::answersFor),
                        )
                        if (formatted.isNotEmpty()) onSubmit(formatted)
                    },
                )
            }
        }
    }
}

/**
 * 题目切换条：放得下就用原厂分段控件，放不下就换成可横向滑动的胶囊行。
 *
 * 分段控件按水位分配把宽度切成 N 份，题一多、标签一长，每段只剩两三个字，
 * 其余全是省略号，根本读不出来是哪一题。这里先按原厂同一套公式（minWidth 52dp、
 * 左右各 12dp 文字内边距、容器 2dp）量一遍自然宽度：装得下就照旧用分段控件，
 * 装不下就把每段换成按内容自适应、整行可滑动的胶囊——标签完整显示，滑一下能看到后面的题。
 */
@Composable
private fun QuestionTabs(
    labels: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    dark: Boolean,
    containerColor: Color,
    indicatorColor: Color,
) {
    val style = CouiSegmentedControlDefaults.textStyle()
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    // COUI 把 minWidth / labelPadding / smallPadding 声明成 internal，这里按同值镜像一份，
    // 只用于估算"要不要换布局"，不参与实际测量与绘制。
    val minWidth = with(density) { 52.dp.toPx() }
    val labelPadding = with(density) { 12.dp.toPx() }
    val containerPadding = with(density) { 2.dp.toPx() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CouiPageDefaults.rowPadding, vertical = 10.dp),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val available = constraints.maxWidth.toFloat()
            val naturalWidth = remember(labels, available, style) {
                labels.fold(0f) { sum, label ->
                    val text = measurer.measure(label, style, maxLines = 1).size.width.toFloat()
                    sum + max(minWidth, text + labelPadding * 2f)
                } + containerPadding * 2f
            }
            if (naturalWidth <= available) {
                SegmentedControl(
                    modifier = Modifier.fillMaxWidth(),
                    labels = labels,
                    selectedIndex = selectedIndex,
                    onSelected = onSelected,
                    containerColor = containerColor,
                    indicatorColor = indicatorColor,
                    small = true,
                )
            } else {
                val colors = CouiPageDefaults.themeColors(dark)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    labels.forEachIndexed { position, label ->
                        val selected = position == selectedIndex
                        Box(
                            modifier = Modifier
                                .clip(CouiCapsuleShape)
                                .background(
                                    if (selected) indicatorColor else containerColor,
                                    CouiCapsuleShape,
                                )
                                .clickable { onSelected(position) }
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                        ) {
                            Text(
                                text = label,
                                kind = CouiTextKind.GroupFooter,
                                color = if (selected) colors.title else colors.summary,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 卡片内的分隔线：两侧留白一致，不再按「带图标行」的缩进往右偏。 */
@Composable
private fun CardDivider() {
    Divider(
        startIndent = CouiPageDefaults.rowPadding,
        endIndent = CouiPageDefaults.rowPadding,
    )
}

/** 选项右侧的勾选标记：多选用方框、单选用圆点。 */
@Composable
private fun Mark(checked: Boolean, multiple: Boolean) {
    if (multiple) {
        Checkbox(checked = checked)
    } else {
        RadioButton(selected = checked)
    }
}
