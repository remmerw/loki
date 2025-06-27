package io.github.remmerw.loki.core

import io.github.remmerw.loki.data.Message
import io.github.remmerw.loki.data.Type

internal interface MessageConsumer {
    fun consumedType(): Type

    fun consume(message: Message, connection: Connection)
}
