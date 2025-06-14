package io.github.remmerw.loki.core

import io.github.remmerw.loki.grid.Message
import io.github.remmerw.loki.grid.Type

internal interface MessageConsumer {
    fun consumedType(): Type

    fun consume(message: Message, connection: Connection)
}
