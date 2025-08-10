package io.github.remmerw.loki.core

import io.github.remmerw.loki.CHOKING_THRESHOLD
import io.github.remmerw.loki.data.Cancel
import io.github.remmerw.loki.data.Choke
import io.github.remmerw.loki.data.Have
import io.github.remmerw.loki.data.Interested
import io.github.remmerw.loki.data.Message
import io.github.remmerw.loki.data.NotInterested
import io.github.remmerw.loki.data.Piece
import io.github.remmerw.loki.data.Unchoke
import io.github.remmerw.loki.data.choke
import io.github.remmerw.loki.data.unchoke
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlin.time.TimeSource

internal open class ConnectionWorker(
    private val worker: Worker
) : ConnectionState() {
    private val outgoingMessages: ArrayDeque<Message> = ArrayDeque()
    private val pieceAnnouncements: MutableList<Have> = mutableListOf()
    private val lock = reentrantLock()

    internal fun appendHave(have: Have) {
        lock.withLock {
            pieceAnnouncements.add(have)
        }
    }

    fun nextMessage(): Message? {
        var message: Message? = lock.withLock { pieceAnnouncements.removeFirstOrNull() }
        if (message != null) {
            return message
        }

        message = getMessage()
        if (message != null && message is Have) {
            worker.connections().forEach { connection: Connection ->
                connection.appendHave(message)
            }
        }
        return message
    }


    fun postMessage(message: Message) {
        if (isUrgent(message)) {
            outgoingMessages.addFirst(message)
        } else {
            outgoingMessages.add(message)
        }
    }

    private fun postProcessOutgoingMessage(message: Message?): Message? {
        if (message == null) {
            return null
        }


        if (message is Piece) {
            // check that peer hadn't sent cancel while we were preparing the requested block
            if (isCancelled(message)) {
                // dispose of message
                return null
            }
        }
        if (message is Interested) {
            isInterested = true
        }
        if (message is NotInterested) {
            isInterested = false
        }
        if (message is Choke) {
            shouldChoke = true
        }
        if (message is Unchoke) {
            shouldChoke = false
        }

        return message
    }

    private fun isCancelled(piece: Piece): Boolean {
        val pieceIndex = piece.piece
        val offset = piece.offset

        return isCanceled(key(pieceIndex, offset))
    }


    fun getMessage(): Message? {
        if (outgoingMessages.isEmpty()) {
            worker.produce(this as Connection)
            handleConnection()
        }
        return postProcessOutgoingMessage(outgoingMessages.removeFirstOrNull())
    }

    /**
     * Inspects connection state and yields choke/unchoke messages when appropriate.
     */
    protected fun handleConnection() {
        var shouldChokeCheck = shouldChoke
        val chokingCheck = choking

        if (shouldChokeCheck == null) {
            if (isPeerInterested && chokingCheck) {
                if (mightUnchoke()) {
                    shouldChokeCheck = false // should unchoke
                }
            } else if (!isPeerInterested && !chokingCheck) {
                shouldChokeCheck = true
            }
        }

        if (shouldChokeCheck != null) {
            if (shouldChokeCheck != chokingCheck) {
                if (shouldChokeCheck) {
                    // choke immediately
                    choking = true
                    shouldChoke = null
                    postMessage(choke())
                    lastChoked = TimeSource.Monotonic.markNow()
                } else if (mightUnchoke()) {
                    choking = false
                    shouldChoke = null
                    postMessage(unchoke())
                }
            }
        }
    }

    private fun isUrgent(message: Message): Boolean {
        return message is Choke || message is Unchoke || message is Cancel
    }

    private fun mightUnchoke(): Boolean {
        // unchoke depending on last choked time to avoid fibrillation
        val time = lastChoked
        if (time == null) {
            return true
        }
        return time.elapsedNow().inWholeMilliseconds >= CHOKING_THRESHOLD
    }

}



