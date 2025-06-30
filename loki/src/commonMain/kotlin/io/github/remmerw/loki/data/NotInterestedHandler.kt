package io.github.remmerw.loki.data

import kotlinx.io.Buffer

internal class NotInterestedHandler : UniqueMessageHandler(Type.NotInterested) {
    override fun doDecode(peer: Peer, buffer: Buffer): Message {
        return notInterested()
    }

    override fun doEncode(peer: Peer, message: Message, buffer: Buffer) {

        val size = (MESSAGE_TYPE_SIZE)
        buffer.writeInt(size)
        buffer.writeByte(message.messageId)
    }
}
