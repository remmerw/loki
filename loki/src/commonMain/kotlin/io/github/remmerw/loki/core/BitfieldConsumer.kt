package io.github.remmerw.loki.core

import io.github.remmerw.loki.grid.Bitfield
import io.github.remmerw.loki.grid.Have
import io.github.remmerw.loki.grid.Message
import io.github.remmerw.loki.grid.Type


internal class BitfieldConsumer(
    private val dataStorage: DataStorage
) : Consumers {

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
                    if (dataStorage.initializeDone()) {
                        consumeBitfield(message as Bitfield, connection)
                    }
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
                    if (dataStorage.initializeDone()) {
                        consumeHave(message as Have, connection)
                    }
                }
            })
            return list
        }


    private fun consumeBitfield(
        message: Bitfield,
        connection: Connection
    ) {
        val piecesTotal = dataStorage.piecesTotal()
        val peerDataBitfield = DataBitfield(
            piecesTotal, Bitmask.decode(message.bitfield, piecesTotal)
        )
        dataStorage.pieceStatistics()!!.addBitfield(connection, peerDataBitfield)
    }

    private fun consumeHave(have: Have, connection: Connection) {
        dataStorage.pieceStatistics()!!.addPiece(connection, have.pieceIndex)
    }
}
