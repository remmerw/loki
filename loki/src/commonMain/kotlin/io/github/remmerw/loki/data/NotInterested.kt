package io.github.remmerw.loki.data

internal class NotInterested : Message {
    override val messageId: Byte
        get() = NOT_INTERESTED_ID
    override val type: Type
        get() = Type.NotInterested

}
