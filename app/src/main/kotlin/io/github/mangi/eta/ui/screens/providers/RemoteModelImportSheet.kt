package io.github.mangi.eta.ui.screens.providers

// 本文件是上游文件，只保留上游自己的声明（RemoteModelImport）。
// 本地新增的双主题分支、面板高度与留白调整等已按「挂载点化」搬到 RemoteModelImportSheetLocal.kt。

import io.github.mangi.eta.data.model.Model
import io.github.mangi.eta.data.repository.catalog.RemoteModelCatalog

/** 一次拉取的待选结果；确认时保留连接和账号绑定，避免将过期目录写到另一连接。 */
internal data class RemoteModelImport(
    val catalog: RemoteModelCatalog,
    val candidates: List<Model>,
)
