package io.github.remmerw.loki.data

import kotlinx.io.Buffer

internal class ChokeHandler : UniqueMessageHandler(Type.Choke) {
    override fun doDecode(peer: Peer, buffer: Buffer): Message {
        return choke()
    }

    override fun doEncode(peer: Peer, message: Message, buffer: Buffer) {
    }
}
