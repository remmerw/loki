package io.github.remmerw.loki.core

import io.github.remmerw.loki.grid.core.Bitfield
import io.github.remmerw.loki.grid.core.Have
import io.github.remmerw.loki.grid.core.Message
import io.github.remmerw.loki.grid.core.Type
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock

internal class BitfieldCollectingConsumer(private val dataStorage: DataStorage) : Agent, Consumers {
    private val bitfields: MutableMap<Connection, ByteArray> = mutableMapOf()
    private val haves: MutableMap<Connection, MutableSet<Int>> = mutableMapOf()
    private val lock = reentrantLock()

    private fun doConsume(message: Message, connection: Connection) {
        if (message is Bitfield) {
            consume(message, connection)
        }
        if (message is Have) {
            consume(message, connection)
        }
    }

    // process bitfields and haves that we received while fetching metadata
    fun processMessages() {
        lock.withLock {
            val pieceStatistics = dataStorage.pieceStatistics()!!
            val piecesTotal = dataStorage.piecesTotal()

            require(piecesTotal > 0) { "Pieces total not yet defined" }
            bitfields.forEach { (connection: Connection, bitfieldBytes: ByteArray) ->
                if (!connection.hasDataBitfield()) { // the else case should never happen
                    pieceStatistics.addBitfield(
                        connection,
                        DataBitfield(piecesTotal, Bitmask.decode(bitfieldBytes, piecesTotal))
                    )
                }
            }

            haves.forEach { (connection: Connection, pieces: MutableSet<Int>) ->
                pieces.forEach { piece: Int -> pieceStatistics.addPiece(connection, piece) }
            }
        }
    }

    override val consumers: List<MessageConsumer>
        get() {
            val list: MutableList<MessageConsumer> = mutableListOf()
            list.add(object : MessageConsumer {
                override fun consumedType(): Type {
                    return Type.Bitfield
                }

                override fun consume(
                    message: Message,
                    connection: Connection
                ) {
                    doConsume(message, connection)
                }
            })
            list.add(object : MessageConsumer {
                override fun consumedType(): Type {
                    return Type.Have
                }

                override fun consume(
                    message: Message,
                    connection: Connection
                ) {
                    doConsume(message, connection)
                }
            })
            return list
        }


    private fun consume(bitfieldMessage: Bitfield, connection: Connection) {
        lock.withLock {
            bitfields[connection] = bitfieldMessage.bitfield
        }
    }

    private fun consume(have: Have, connection: Connection) {
        lock.withLock {
            val peerHaves = haves.getOrPut(connection) { mutableSetOf() }
            peerHaves.add(have.pieceIndex)
        }
    }
}
