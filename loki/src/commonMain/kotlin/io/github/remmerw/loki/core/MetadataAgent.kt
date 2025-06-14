package io.github.remmerw.loki.core

import io.github.remmerw.loki.BLOCK_SIZE
import io.github.remmerw.loki.grid.Message
import io.github.remmerw.loki.grid.MetaType
import io.github.remmerw.loki.grid.Type
import io.github.remmerw.loki.grid.UtMetadata
import io.github.remmerw.loki.grid.data
import io.github.remmerw.loki.grid.reject
import kotlin.math.min


internal class MetadataAgent(
    private val dataStorage: DataStorage
) : Produces, Consumers {


    override val consumers: List<MessageConsumer>
        get() {
            val list: MutableList<MessageConsumer> = mutableListOf()
            list.add(object : MessageConsumer {
                override fun consumedType(): Type {
                    return Type.UtMetadata
                }

                override fun consume(
                    message: Message,
                    connection: Connection
                ) {
                    if (dataStorage.initializeDone()) {
                        consumeUtMetadata(message as UtMetadata, connection)
                    }
                }
            })
            return list
        }

    private fun consumeUtMetadata(message: UtMetadata, connection: Connection) {

        if (message.metaType == MetaType.REQUEST) {
            processMetadataRequest(connection, message.pieceIndex)
        }
    }

    private fun processMetadataRequest(connection: Connection, pieceIndex: Int) {
        val response: Message

        if (dataStorage.torrent()!!.isPrivate) {
            // reject all requests if:
            // - torrent is private
            response = reject(pieceIndex)
        } else {

            val size = dataStorage.metadataSize()
            val offset = pieceIndex * BLOCK_SIZE
            if (offset > size) {
                response = reject(pieceIndex) // not valid piece index
            } else {
                val length = min(size - offset, BLOCK_SIZE)

                val data = dataStorage.sliceMetadata(offset, length)

                response = data(pieceIndex, size, data)
            }
        }

        connection.addOutboundMessage(response)
    }


    override fun produce(connection: Connection, messageConsumer: (Message) -> Unit) {
        if (dataStorage.initializeDone()) {
            val msg = connection.firstOutboundMessage()
            if (msg != null) {
                messageConsumer.invoke(msg)
            }
        }
    }
}
