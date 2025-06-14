package io.github.remmerw.loki.core

import io.github.remmerw.loki.grid.Message

internal interface MessageProducer {
    fun produce(connection: Connection, messageConsumer: (Message) -> Unit)
}
