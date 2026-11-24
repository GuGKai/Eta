package io.github.mangi.eta.data.repository

import android.content.Context
import io.github.mangi.eta.data.db.EtaDatabase
import io.github.mangi.eta.data.db.openTextStream
import java.io.Reader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** 只返回命中的会话标识，搜索不把未打开会话的正文带回界面状态。 */
internal object ConversationSearchRepository {
    suspend fun search(context: Context, query: String, matchingNoticeCodes: List<String>): Set<String> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptySet()
        val database = EtaDatabase.get(context.applicationContext)
        val dao = database.conversationDao()
        // 刻意不开 withTransaction：全库正文扫描要几秒，包进写事务后，点开会话所需的
        // 读窗口（loadConversationMessages / loadConversationCheckpoint 也是 withTransaction）
        // 只能排在它后面，表现为「从搜索结果点进去加载特别久，从侧栏点却秒开」。
        // 这里每条查询各自成事务，读到的快照可能跨毫秒级，对搜索结果没有影响。
        //
        // 顺序：先用一条 SQL 把 inline 正文与工具明细扫完，再补上分块存储的正文。
        // 之前是游标逐行 + Kotlin 层建流比较，12 万条消息光分配缓冲区就要十几秒，
        // 慢到用户以为「搜了没反应」。
        val matches = dao.searchConversationIdsByText(query).toMutableSet()
        val cancellation = currentCoroutineContext()
        for (ref in dao.chunkedMessageRefs()) {
            cancellation.ensureActive()
            if (ref.conversationId in matches) continue
            val hit = dao.openTextStream("conversation_messages", ref.id, "content", ref.content)
                .reader(Charsets.UTF_8).use { it.containsText(query, cancellation::ensureActive) }
            if (hit) matches += ref.conversationId
        }
        matches + dao.searchSystemNotices(matchingNoticeCodes)
    }

    private fun Reader.containsText(query: String, checkCancelled: () -> Unit): Boolean {
        val buffer = CharArray(DEFAULT_BUFFER_SIZE)
        var overlap = ""
        // 查询串里没有任何 Unicode 字母（纯中文、数字、符号）时，忽略大小写没有意义：
        // 走大小写敏感的比较，省掉 contains(ignoreCase) 的逐字符折叠。
        val caseSensitive = query.none { it.isLetter() }
        while (true) {
            checkCancelled()
            val count = read(buffer)
            if (count < 0) return false
            val candidate = if (overlap.isEmpty()) {
                String(buffer, 0, count)
            } else {
                overlap + String(buffer, 0, count)
            }
            val matched = if (caseSensitive) {
                candidate.contains(query)
            } else {
                candidate.contains(query, ignoreCase = true)
            }
            if (matched) return true
            overlap = candidate.takeLast(query.length - 1)
        }
    }
}
