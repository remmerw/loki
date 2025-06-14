package io.github.remmerw.loki.grid

import kotlinx.io.Buffer

internal class NotInterestedHandler : UniqueMessageHandler(Type.NotInterested) {
    override fun doDecode(peer: Peer, buffer: Buffer): Message {
        return notInterested()
    }

    override fun doEncode(peer: Peer, message: Message, buffer: Buffer) {
    }
}
