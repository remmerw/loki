package io.github.remmerw.loki.core

import io.github.remmerw.loki.data.Have
import io.github.remmerw.loki.data.Message
import io.github.remmerw.loki.data.Piece
import io.github.remmerw.loki.data.Type
import io.github.remmerw.loki.debug
import io.ktor.util.collections.ConcurrentSet

internal class PieceAgent(
    private val dataStorage: DataStorage
) : Produces, Consumers {
    private val completedPieces: MutableSet<Int> = ConcurrentSet()


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
        if (dataStorage.isComplete(piece.piece)) {
            return
        }

        addBlock(connection, piece)
    }

    private fun checkBlockIsExpected(connection: Connection, piece: Piece): Boolean {
        val key = key(piece.piece, piece.offset)
        return connection.pendingRequestsRemove(key)
    }

    private fun addBlock(connection: Connection, piece: Piece) {

        val assignment = connection.assignment
        if (assignment != null) {
            if (assignment.isAssigned(piece.piece)) {
                assignment.check()
            }
        }

        val chunk = dataStorage.chunk(piece.piece)

        if (chunk.isComplete) {
            return
        }

        chunk.writeBlock(piece.offset, piece.data)

        if (chunk.isComplete) {
            if (dataStorage.storeChunk(piece.piece, chunk)) {
                completedPieces.add(piece.piece)
            } else {
                // chunk was shit (for testing now - close connection)
                debug("Received shit chunk, close connection -> " + connection.address())
                connection.close()
            }
        }

    }


    override fun produce(connection: Connection, messageConsumer: (Message) -> Unit) {
        if (dataStorage.initializeDone()) {
            completedPieces.forEach { index ->
                messageConsumer.invoke(Have(index))
            }
        }
    }
}
