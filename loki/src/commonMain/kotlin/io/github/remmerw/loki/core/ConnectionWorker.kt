package io.github.remmerw.loki.core

import io.github.remmerw.loki.CHOKING_THRESHOLD
import io.github.remmerw.loki.grid.Have
import io.github.remmerw.loki.grid.Message
import io.github.remmerw.loki.grid.Piece
import io.github.remmerw.loki.grid.Type
import io.github.remmerw.loki.grid.choke
import io.github.remmerw.loki.grid.unchoke
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
        if (message != null && Type.Have == message.type) {
            val have = message as Have
            worker.purgedConnections().forEach { connection: Connection ->
                connection.appendHave(have)
            }
        }
        return message
    }


    private fun postMessage(message: Message) {
        if (isUrgent(message)) {
            addUrgent(message)
        } else {
            add(message)
        }
    }

    private fun add(message: Message) {
        outgoingMessages.add(message)
    }

    private fun addUrgent(message: Message) {
        outgoingMessages.addFirst(message)
    }


    private fun postProcessOutgoingMessage(message: Message?): Message? {
        if (message == null) {
            return null
        }

        val messageType = message.type

        if (Type.Piece == messageType) {
            val piece = message as Piece
            // check that peer hadn't sent cancel while we were preparing the requested block
            if (isCancelled(piece)) {
                // dispose of message
                return null
            }
        }
        if (Type.Interested == messageType) {
            isInterested = true
        }
        if (Type.NotInterested == messageType) {
            isInterested = false
        }
        if (Type.Choke == messageType) {
            shouldChoke = true
        }
        if (Type.Unchoke == messageType) {
            shouldChoke = false
        }

        return message
    }

    private fun isCancelled(piece: Piece): Boolean {
        val pieceIndex = piece.pieceIndex
        val offset = piece.offset

        return isCanceled(key(pieceIndex, offset))
    }

    private fun updateConnection() {
        handleConnection { message: Message -> postMessage(message) }
    }

    fun accept(message: Message) {
        worker.consume(message, this as Connection)
        updateConnection()
    }

    fun getMessage(): Message? {
        if (outgoingMessages.isEmpty()) {
            worker.produce(this as Connection) { message: Message -> this.postMessage(message) }
            updateConnection()
        }
        return postProcessOutgoingMessage(outgoingMessages.removeFirstOrNull())
    }

    /**
     * Inspects connection state and yields choke/unchoke messages when appropriate.
     */
    private fun handleConnection(messageConsumer: (Message) -> Unit) {
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
                    messageConsumer.invoke(choke())
                    lastChoked = TimeSource.Monotonic.markNow()
                } else if (mightUnchoke()) {
                    choking = false
                    shouldChoke = null
                    messageConsumer.invoke(unchoke())
                }
            }
        }
    }

    private fun isUrgent(message: Message): Boolean {
        val messageType = message.type
        return Type.Choke == messageType || Type.Unchoke == messageType || Type.Cancel == messageType
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



