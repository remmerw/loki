package io.github.remmerw.loki.core

import io.github.remmerw.loki.data.Cancel
import io.github.remmerw.loki.data.Message
import io.github.remmerw.loki.data.Type

internal class GenericConsumer(private val dataStorage: DataStorage) : Consumers {
    override val consumers: List<MessageConsumer>
        get() {
            val list: MutableList<MessageConsumer> = mutableListOf()
            list.add(object : MessageConsumer {
                override fun consumedType(): Type {
                    return Type.KeepAlive
                }

                override fun consume(
                    message: Message,
                    connection: Connection
                ) {
                    // nothing to do here
                }
            })
            list.add(object : MessageConsumer {
                override fun consumedType(): Type {
                    return Type.Choke
                }

                override fun consume(
                    message: Message,
                    connection: Connection
                ) {
                    if (dataStorage.initializeDone()) {
                        connection.isPeerChoking = true
                    }
                }
            })
            list.add(object : MessageConsumer {
                override fun consumedType(): Type {
                    return Type.Unchoke
                }

                override fun consume(
                    message: Message,
                    connection: Connection
                ) {
                    if (dataStorage.initializeDone()) {
                        connection.isPeerChoking = false
                    }
                }
            })
            list.add(object : MessageConsumer {
                override fun consumedType(): Type {
                    return Type.Interested
                }

                override fun consume(
                    message: Message,
                    connection: Connection
                ) {
                    if (dataStorage.initializeDone()) {
                        connection.isPeerInterested = true
                    }
                }
            })

            list.add(object : MessageConsumer {
                override fun consumedType(): Type {
                    return Type.NotInterested
                }

                override fun consume(
                    message: Message,
                    connection: Connection
                ) {
                    if (dataStorage.initializeDone()) {
                        connection.isPeerInterested = false
                    }
                }
            })
            list.add(object : MessageConsumer {
                override fun consumedType(): Type {
                    return Type.Cancel
                }

                override fun consume(
                    message: Message,
                    connection: Connection
                ) {
                    if (dataStorage.initializeDone()) {
                        connection.onCancel(message as Cancel)
                    }
                }
            })
            return list
        }

}
