package io.github.remmerw.loki.core


internal interface MessageProducer {
    fun produce(connection: Connection)
}
