package io.github.remmerw.loki.core

import io.github.remmerw.loki.data.Request
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.Volatile
import kotlin.time.TimeSource.Monotonic.ValueTimeMark


internal open class ConnectionState : ConnectionAgent() {
    private val cancelledRequests: MutableSet<Long> = ConcurrentHashMap.newKeySet()
    private val pendingRequests: MutableSet<Long> = ConcurrentHashMap.newKeySet()
    private val pieces: MutableSet<Int> = ConcurrentHashMap.newKeySet()
    private val haves: MutableSet<Int> = ConcurrentHashMap.newKeySet()
    private val requests: ArrayDeque<Request> = ArrayDeque() // no concurrency


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
        pieces.remove(piece)
    }

    fun addPiece(piece: Int): Boolean {
        return pieces.add(piece)
    }

    fun addHave(piece: Int): Boolean {
        return haves.add(piece)
    }

    fun clearPieces() {
        pieces.clear()
    }


    fun firstRequest(): Request? {
        return requests.removeFirstOrNull()
    }

    fun clearRequests() {
        requests.clear()
    }

    fun addRequests(requests: List<Request>) {
        this.requests.addAll(requests)
    }

    fun cancelRequest(piece: Int, offset: Int) {
        cancelledRequests.add(
            key(
                piece, offset
            )
        )
    }

    fun isCanceled(key: Long): Boolean {
        return cancelledRequests.remove(key)
    }


    fun pendingRequestsAdd(key: Long) {
        pendingRequests.add(key)
    }

    fun pendingRequestsSize(): Int {
        return pendingRequests.size
    }

    fun pendingRequestsHas(key: Long): Boolean {
        return pendingRequests.contains(key)
    }

    fun pendingRequests(): List<Long> {
        return pendingRequests.toList()
    }

    fun pendingRequestsClear() {
        pendingRequests.clear()
    }

    private fun pendingRequestsRemove(key: Long): Boolean {
        return pendingRequests.remove(key)
    }

    protected fun checkBlockIsExpected(piece: Int, offset: Int): Boolean {
        val key = key(piece, offset)
        return pendingRequestsRemove(key)
    }
}
