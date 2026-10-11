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
        val onStop: () -> Unit,
    )

    private val leases = linkedMapOf<String, Lease>()
    private var activeOwner: Long? = null

    @Synchronized fun acquire(
        id: String,
        kind: Kind = Kind.TASK,
        allowBoundFallback: Boolean = false,
        onStop: () -> Unit,
    ): Boolean {
        require(id.isNotBlank())
        if (id in leases) return false
        leases[id] = Lease(activeOwner, allowBoundFallback, kind, onStop)
        return true
    }

    @Synchronized fun release(id: String) { leases.remove(id) }
    @Synchronized fun count(): Int = leases.size

    /** 仍在运行的任务数；保活租约不计入，避免空闲会话把「Eta 正在运行」钉在通知栏上。 */
    @Synchronized fun taskCount(): Int = leases.values.count { it.kind == Kind.TASK }

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

    @Synchronized fun drain(startFailed: Boolean = false): List<() -> Unit> = leases.values
        .filterNot { startFailed && it.allowBoundFallback }
        .map { it.onStop }
        .also { leases.clear() }
}
