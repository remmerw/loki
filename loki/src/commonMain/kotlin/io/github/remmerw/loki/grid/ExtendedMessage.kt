package io.github.remmerw.loki.grid


internal interface ExtendedMessage : Message {
    override val messageId: Byte
        get() = EXTENDED_MESSAGE_ID
}
