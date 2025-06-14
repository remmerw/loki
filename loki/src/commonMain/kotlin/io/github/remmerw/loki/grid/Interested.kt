package io.github.remmerw.loki.grid

internal class Interested : Message {
    override val messageId: Byte
        get() = INTERESTED_ID
    override val type: Type
        get() = Type.Interested

}
