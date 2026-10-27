package io.github.mangi.eta.agent.runtime

/** 用户任务的运行引用；通知停止只消费这里登记的任务，不接管 Root daemon。 */
internal class ExecutionLeaseRegistry {
    /**
     * 租约类型。只有 [Kind.TASK] 会出现在“任务正在运行”的通知文案里；
     * [Kind.KEEP_ALIVE] 只为保住前台服务（虚拟屏会话等），不能被说成有任务在跑。
     */
    internal enum class Kind { TASK, KEEP_ALIVE }

    private data class Lease(
        val owner: Long?,
        val allowBoundFallback: Boolean,
        val kind: Kind,
        val label: String?,
        val startedAt: Long,
        val onStop: () -> Unit,
    )

    private val leases = linkedMapOf<String, Lease>()
    private var activeOwner: Long? = null

    @Synchronized fun acquire(
        id: String,
        kind: Kind = Kind.TASK,
        allowBoundFallback: Boolean = false,
        label: String? = null,
        onStop: () -> Unit,
    ): Boolean {
        require(id.isNotBlank())
        if (id in leases) return false
        leases[id] = Lease(
            owner = activeOwner,
            allowBoundFallback = allowBoundFallback,
            kind = kind,
            label = label,
            startedAt = android.os.SystemClock.elapsedRealtime(),
            onStop = onStop,
        )
        return true
    }

    @Synchronized fun release(id: String) { leases.remove(id) }
    @Synchronized fun count(): Int = leases.size

    /** 仍在运行的任务数；保活租约不计入，避免空闲会话把「Eta 正在运行」钉在通知栏上。 */
    @Synchronized fun taskCount(): Int = leases.values.count { it.kind == Kind.TASK }

    /** 正在运行的任务名与开始时刻（elapsedRealtime），用于通知上显示“到底在跑什么”。 */
    @Synchronized fun runningTasks(): List<Pair<String?, Long>> = leases.values
        .filter { it.kind == Kind.TASK }
        .map { it.label to it.startedAt }

    /** 后台命令换了描述时更新租约上的名字，不重新登记租约。 */
    @Synchronized fun updateLabel(id: String, label: String) {
        leases[id]?.let { leases[id] = it.copy(label = label) }
    }

    @Synchronized fun attachOwner(owner: Long) {
        activeOwner = owner
        leases.replaceAll { _, lease -> if (lease.owner == null) lease.copy(owner = owner) else lease }
    }

    @Synchronized fun closeOwnerIfIdle(owner: Long): Boolean {
        if (leases.isNotEmpty()) return false
        if (activeOwner == owner) activeOwner = null
        return true
    }

    /** 旧服务销毁时，不能取消在其 stopSelf 之后为下一次启动登记的任务。 */
    @Synchronized fun drainOwner(owner: Long): List<() -> Unit> {
        if (activeOwner == owner) activeOwner = null
        val owned = leases.filterValues { it.owner == owner }
        owned.keys.forEach(leases::remove)
        return owned.values.map { it.onStop }
    }

    /**
     * 服务被系统停止（而非用户主动停止）时的收尾。
     *
     * [Kind.TASK] 是用户正在等待的回答：服务没了不代表你想要它停，所以只解除归属、不触发 onStop，
     * 任务在 runtime 侧继续跑完，下次进入界面由恢复流程接管结果；保活/终端这类租约照旧回收。
     */
    @Synchronized fun detachOwner(owner: Long): List<() -> Unit> {
        if (activeOwner == owner) activeOwner = null
        val callbacks = mutableListOf<() -> Unit>()
        for ((id, lease) in leases.entries.toList()) {
            if (lease.owner != owner) continue
            if (lease.kind == Kind.TASK) {
                leases[id] = lease.copy(owner = null)
            } else {
                leases.remove(id)
                callbacks += lease.onStop
            }
        }
        return callbacks
    }

    @Synchronized fun drain(startFailed: Boolean = false): List<() -> Unit> = leases.values
        .filterNot { startFailed && it.allowBoundFallback }
        .map { it.onStop }
        .also { leases.clear() }

    /**
     * 只停止指定 id 的租约。
     *
     * 并行任务各自一张通知卡片，卡片上的「停止」必须只结束对应的那一个任务，
     * 不能像整条通知的停止那样把所有活跃任务一起收掉。
     */
    @Synchronized fun drainIds(ids: Set<String>): List<() -> Unit> = ids
        .mapNotNull { id -> leases.remove(id)?.onStop }
}
