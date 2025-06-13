package io.github.remmerw.loki.grid.core

interface ExtendedMessageHandler : MessageHandler {
    fun localTypeId(): Byte
    fun localName(): String
}