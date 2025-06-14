package io.github.remmerw.loki.grid

interface ExtendedMessageHandler : MessageHandler {
    fun localTypeId(): Byte
    fun localName(): String
}