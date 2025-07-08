package io.github.remmerw.loki.core

import io.github.remmerw.loki.data.Message
import io.github.remmerw.loki.data.Piece
import io.github.remmerw.loki.data.Request
import io.github.remmerw.loki.data.Type

internal class PeerRequestAgent(
    private val dataStorage: DataStorage
) :
    Produces, Consumers {

    override val consumers: List<MessageConsumer>
        get() {
            val list: MutableList<MessageConsumer> = mutableListOf()
            list.add(object : MessageConsumer {
                override fun consumedType(): Type {
                    return Type.Request
                }

                override fun consume(
                    message: Message,
                    connection: Connection
                ) {
                    if (dataStorage.initializeDone()) {
                        consumeRequest(message as Request, connection)
                    }
                }
            })

            return list
        }

    private fun consumeRequest(request: Request, connection: Connection) {

        if (!connection.choking) {
            if (dataStorage.isVerified(request.piece)) {
                connection.addRequest(request)
            }
        }
    }


    override fun produce(connection: Connection, messageConsumer: (Message) -> Unit) {
        if (dataStorage.initializeDone()) {
            do {
                val request = connection.firstCompleteRequest()

                if (request != null) {
                    val length = request.length
                    val offset = request.offset
                    val piece = request.piece
                    messageConsumer.invoke(Piece(piece, offset, length))
                }
            } while (request != null)
        }
    }
}
