package io.github.remmerw.loki.data

import kotlinx.io.Buffer


internal interface ExtendedMessage : Message {
    override val messageId: Byte
        get() = EXTENDED_MESSAGE_ID

    override fun encode(buffer: Buffer) {
        // not yet used
    }
}
