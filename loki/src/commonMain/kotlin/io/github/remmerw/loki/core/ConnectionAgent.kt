package io.github.remmerw.loki.core

import io.github.remmerw.loki.grid.core.Message
import io.github.remmerw.loki.grid.core.Request
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlin.concurrent.Volatile
import kotlin.time.TimeSource.Monotonic.ValueTimeMark

internal open class ConnectionAgent {
    @Volatile
    var dataBitfield: DataBitfield? = null

    @Volatile
    var assignment: Assignment? = null

    @Volatile
    var hasUtMetadata = false

    @Volatile
    var requestedAllPeers = false

    @Volatile
    var requestedFirst: ValueTimeMark? = null

    @Volatile
    var withoutMetadata: ValueTimeMark? = null

    @Volatile
    var interestUpdate: Message? = null

    @Volatile
    var connectionWorker: ConnectionWorker? = null

    private val outboundMessages: MutableList<Message> = mutableListOf()
    private val completedReads: MutableList<Request> = mutableListOf()

    private val lock = reentrantLock()

    internal fun addOutboundMessage(message: Message) {
        lock.withLock {
            outboundMessages.add(message)
        }
    }

    fun hasDataBitfield(): Boolean {
        return dataBitfield() != null
    }

    internal fun dataBitfield(): DataBitfield? {
        return dataBitfield
    }

    internal fun setDataBitfield(bitfield: DataBitfield) {
        this.dataBitfield = bitfield
    }

    internal fun removeDataBitfield() {
        this.dataBitfield = null
    }

    internal fun firstOutboundMessage(): Message? {
        return lock.withLock {
            outboundMessages.firstOrNull()
        }
    }

    internal fun addRequest(message: Request) {
        lock.withLock {
            completedReads.add(message)
        }
    }

    internal fun firstCompleteRequest(): Request? {
        return lock.withLock {
            completedReads.firstOrNull()
        }
    }
}