package io.github.remmerw.loki.core

import io.github.remmerw.loki.grid.Cancel
import io.github.remmerw.loki.grid.Request
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlin.concurrent.Volatile
import kotlin.time.TimeSource.Monotonic.ValueTimeMark


internal open class ConnectionState : ConnectionAgent() {
    private val cancelledPeerRequests: MutableSet<Long> = mutableSetOf()
    private val pendingRequests: MutableSet<Long> = mutableSetOf()
    private val pieces: MutableSet<Int> = mutableSetOf()
    private val requests: ArrayDeque<Request> = ArrayDeque()

    private val lock = reentrantLock()

    @Volatile
    var isInterested = false

    @Volatile
    var isPeerInterested = false

    @Volatile
    var choking = true

    @Volatile
    var isPeerChoking = true


    @Volatile
    var shouldChoke: Boolean? = null

    @Volatile
    var lastChoked: ValueTimeMark? = null


    fun removePiece(piece: Int) {
        lock.withLock {
            pieces.remove(piece)
        }
    }

    fun addPiece(piece: Int): Boolean {
        lock.withLock {
            return pieces.add(piece)
        }
    }

    fun clearPieces() {
        lock.withLock {
            pieces.clear()
        }
    }

    fun firstRequest(): Request? {
        lock.withLock {
            return requests.removeFirstOrNull()
        }
    }

    fun clearRequests() {
        lock.withLock {
            requests.clear()
        }
    }

    fun addRequests(requests: List<Request>) {
        lock.withLock {
            this.requests.addAll(requests)
        }
    }


    /**
     * Signal that remote peer has cancelled a previously issued block request.
     */
    fun onCancel(cancel: Cancel) {
        lock.withLock {
            cancelledPeerRequests.add(
                key(
                    cancel.pieceIndex, cancel.offset
                )
            )
        }
    }

    fun isCanceled(key: Long): Boolean {
        lock.withLock {
            return cancelledPeerRequests.remove(key)
        }
    }


    fun pendingRequestsAdd(key: Long) {
        lock.withLock {
            pendingRequests.add(key)
        }
    }

    fun pendingRequestsSize(): Int {
        lock.withLock {
            return pendingRequests.size
        }
    }

    fun pendingRequestsHas(key: Long): Boolean {
        lock.withLock {
            return pendingRequests.contains(key)
        }
    }

    fun pendingRequests(): Set<Long> {
        lock.withLock {
            return pendingRequests
        }
    }

    fun pendingRequestsClear() {
        lock.withLock {
            pendingRequests.clear()
        }
    }

    fun pendingRequestsRemove(key: Long): Boolean {
        lock.withLock {
            return pendingRequests.remove(key)
        }
    }
}
