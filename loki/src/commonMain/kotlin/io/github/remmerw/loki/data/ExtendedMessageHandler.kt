package io.github.remmerw.loki.data


interface ExtendedMessageHandler : MessageHandler {
    fun localTypeId(): Byte
    fun localName(): String

}