package io.github.remmerw.loki.core

import io.github.remmerw.loki.grid.Message
import io.github.remmerw.loki.grid.Type

internal class ExtendedHandshakeConsumer : Consumers {

    override val consumers: List<MessageConsumer>
        get() {
            val list: MutableList<MessageConsumer> = mutableListOf()
            list.add(object : MessageConsumer {
                override fun consumedType(): Type {
                    return Type.ExtendedHandshake
                }

                override fun consume(
                    message: Message,
                    connection: Connection
                ) {
                    // do nothing here
                }
            })
            return list
        }


}