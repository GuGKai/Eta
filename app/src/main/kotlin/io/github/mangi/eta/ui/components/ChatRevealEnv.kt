package io.github.mangi.eta.ui.components

import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf

/**
 * 会话页「浮在列表上方的主输入框」的几何信息 + 最小滚动接口。
 *
 * 主输入框是 Scaffold 的 bottomBar，但消息列表仍然铺满整屏（从输入框背后穿过去），
 * 所以列表里的控件其实一直落在 LazyColumn 的视口里，`bringIntoView` 认为它「看得见」，
 * 一个像素都不会滚，结果就是键盘弹起后整块被输入框和键盘盖住。
 *
 * 这里把浮层顶边和滚动能力交给列表里的控件，由控件自己判断有没有被压住：
 * 没压住就不动，压住了才按重叠量做最小幅度上抬。
 */
internal class ChatRevealEnv(
    /** 主输入框顶边在窗口坐标系里的 y（px）；键盘弹起时会跟着上移。 */
    val overlayTopPx: State<Float>,
    /** 列表是否正被手指拖动：拖动期间不抢滚动，也用来收掉输入状态。 */
    val userDragging: State<Boolean>,
    /** 内容上移量（px，正数 = 把列表内容往上推）；用户正在拖动时这次滚动会被放弃。 */
    val scrollBy: suspend (Float) -> Unit,
)

internal val LocalChatReveal = compositionLocalOf<ChatRevealEnv?> { null }
