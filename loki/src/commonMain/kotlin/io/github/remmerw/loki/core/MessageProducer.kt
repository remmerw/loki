package io.github.remmerw.loki.core

import io.github.remmerw.loki.grid.core.Message

internal interface MessageProducer {
    fun produce(connection: Connection, messageConsumer: (Message) -> Unit)
}
