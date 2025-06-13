package io.github.remmerw.loki.core

import io.github.remmerw.loki.debug
import io.github.remmerw.loki.grid.core.Have
import io.github.remmerw.loki.grid.core.Message
import io.github.remmerw.loki.grid.core.Piece
import io.github.remmerw.loki.grid.core.Type
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock

internal class PieceAgent(
    private val dataStorage: DataStorage
) : Produces, Consumers {
    private val completedPieces: MutableList<Int> = mutableListOf()
    private val lock = reentrantLock()


    override val consumers: List<MessageConsumer>
        get() {
            val list: MutableList<MessageConsumer> = mutableListOf()
            list.add(object : MessageConsumer {
                override fun consumedType(): Type {
                    return Type.Piece
                }

                override fun consume(
                    message: Message,
                    connection: Connection
                ) {
                    if (dataStorage.initializeDone()) {
                        consumePiece(message as Piece, connection)
                    }
                }
            })

            return list
        }

    private fun consumePiece(piece: Piece, connection: Connection) {

        // check that this block was requested in the first place
        if (!checkBlockIsExpected(connection, piece)) {
            return
        }

        // discard blocks for pieces that have already been verified
        if (dataStorage.isComplete(piece.pieceIndex)) {
            return
        }

        addBlock(connection, piece)
    }

    private fun checkBlockIsExpected(connection: Connection, piece: Piece): Boolean {
        val key = key(piece.pieceIndex, piece.offset)
        return connection.pendingRequestsRemove(key)
    }

    private fun addBlock(connection: Connection, piece: Piece) {
        try {
            val assignment = connection.assignment
            if (assignment != null) {
                if (assignment.isAssigned(piece.pieceIndex)) {
                    assignment.check()
                }
            }

            val chunk = dataStorage.chunk(piece.pieceIndex)

            if (chunk.isComplete) {
                return
            }

            chunk.writeBlock(piece.offset, piece.data)

            if (chunk.isComplete) {
                val verified = chunk.verify()
                if (verified) {
                    dataStorage.storeChunk(piece.pieceIndex, chunk)
                    dataStorage.markVerified(piece.pieceIndex)
                    lock.withLock {
                        completedPieces.add(piece.pieceIndex)
                    }
                } else {
                    // reset data
                    chunk.reset()
                }
            }
        } catch (throwable: Throwable) {
            debug("PieceAgent", throwable)
        }
    }


    override fun produce(connection: Connection, messageConsumer: (Message) -> Unit) {
        if (dataStorage.initializeDone()) {
            lock.withLock {
                do {
                    val completedPiece = completedPieces.removeFirstOrNull()
                    if (completedPiece != null) {
                        messageConsumer.invoke(Have(completedPiece))
                    }
                } while (completedPiece != null)
            }
        }
    }
}
