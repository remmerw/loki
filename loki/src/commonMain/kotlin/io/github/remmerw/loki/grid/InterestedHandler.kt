package io.github.remmerw.loki.grid

import kotlinx.io.Buffer

internal class InterestedHandler : UniqueMessageHandler(Type.Interested) {
    override fun doDecode(peer: Peer, buffer: Buffer): Message {
        return interested()
    }

    override fun doEncode(peer: Peer, message: Message, buffer: Buffer) {

    }
}
