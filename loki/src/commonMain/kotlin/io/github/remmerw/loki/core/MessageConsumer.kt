package io.github.remmerw.loki.core

import io.github.remmerw.loki.grid.core.Message
import io.github.remmerw.loki.grid.core.Type

internal interface MessageConsumer {
    fun consumedType(): Type

    fun consume(message: Message, connection: Connection)
}
