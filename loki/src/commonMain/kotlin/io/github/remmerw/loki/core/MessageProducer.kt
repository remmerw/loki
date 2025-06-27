package io.github.remmerw.loki.core

import io.github.remmerw.loki.data.Message

internal interface MessageProducer {
    fun produce(connection: Connection, messageConsumer: (Message) -> Unit)
}
