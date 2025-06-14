package io.github.remmerw.loki.core

import io.github.remmerw.loki.CHOKING_THRESHOLD
import io.github.remmerw.loki.grid.choke
import io.github.remmerw.loki.grid.Have
import io.github.remmerw.loki.grid.Message
import io.github.remmerw.loki.grid.Piece
import io.github.remmerw.loki.grid.Type
import io.github.remmerw.loki.grid.unchoke
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlin.time.TimeSource

internal data class ConnectionWorker(
    private val connection: Connection,
    private val worker: Worker
) {
    private val outgoingMessages: ArrayDeque<Message> = ArrayDeque()
    private val pieceAnnouncements: MutableList<Have> = mutableListOf()
    private val lock = reentrantLock()

    private fun appendHave(have: Have) {
        lock.withLock {
            pieceAnnouncements.add(have)
        }
    }


    fun message(): Message? {
        var message: Message? = lock.withLock { pieceAnnouncements.removeFirstOrNull() }
        if (message != null) {
            return message
        }

        message = getMessage()
        if (message != null && Type.Have == message.type) {
            val have = message as Have
            worker.purgedConnections().forEach { connection: Connection ->
                val worker = connection.connectionWorker
                if (this !== worker) {
                    worker?.appendHave(have)
                }
            }
        }
        return message
    }

    fun accept(message: Message) {
        worker.consume(message, connection)
        updateConnection()
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

    fun getMessage(): Message? {
        if (outgoingMessages.isEmpty()) {
            worker.produce(connection) { message: Message -> this.postMessage(message) }
            updateConnection()
        }
        return postProcessOutgoingMessage(outgoingMessages.removeFirstOrNull())
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
            connection.isInterested = true
        }
        if (Type.NotInterested == messageType) {
            connection.isInterested = false
        }
        if (Type.Choke == messageType) {
            connection.shouldChoke = true
        }
        if (Type.Unchoke == messageType) {
            connection.shouldChoke = false
        }

        return message
    }

    private fun isCancelled(piece: Piece): Boolean {
        val pieceIndex = piece.pieceIndex
        val offset = piece.offset

        return connection.isCanceled(key(pieceIndex, offset))
    }

    private fun updateConnection() {
        handleConnection(connection) { message: Message -> postMessage(message) }
    }


    /**
     * Inspects connection state and yields choke/unchoke messages when appropriate.
     */
    private fun handleConnection(connection: Connection, messageConsumer: (Message) -> Unit) {
        var shouldChoke = connection.shouldChoke
        val choking = connection.choking
        val peerInterested = connection.isPeerInterested

        if (shouldChoke == null) {
            if (peerInterested && choking) {
                if (mightUnchoke(connection)) {
                    shouldChoke = false // should unchoke
                }
            } else if (!peerInterested && !choking) {
                shouldChoke = true
            }
        }

        if (shouldChoke != null) {
            if (shouldChoke != choking) {
                if (shouldChoke) {
                    // choke immediately
                    connection.choking = true
                    connection.shouldChoke = null
                    messageConsumer.invoke(choke())
                    connection.lastChoked = TimeSource.Monotonic.markNow()
                } else if (mightUnchoke(connection)) {
                    connection.choking = false
                    connection.shouldChoke = null
                    messageConsumer.invoke(unchoke())
                }
            }
        }
    }

    private fun isUrgent(message: Message): Boolean {
        val messageType = message.type
        return Type.Choke == messageType || Type.Unchoke == messageType || Type.Cancel == messageType
    }

    private fun mightUnchoke(connection: Connection): Boolean {
        // unchoke depending on last choked time to avoid fibrillation
        val time = connection.lastChoked
        if (time == null) {
            return true
        }
        return time.elapsedNow().inWholeMilliseconds >= CHOKING_THRESHOLD
    }

}



