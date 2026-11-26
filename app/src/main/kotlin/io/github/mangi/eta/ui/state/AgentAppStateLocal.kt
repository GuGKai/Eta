package io.github.mangi.eta.ui.state

import kotlinx.coroutines.flow.MutableStateFlow

// fork 本地新增：输入器聚焦请求信号。

/** 新建对话的聚焦请求通道：输入栏直接订阅，绕开逐层传参在重建时丢信号。 */
internal val composerFocusRequests = MutableStateFlow(0)
