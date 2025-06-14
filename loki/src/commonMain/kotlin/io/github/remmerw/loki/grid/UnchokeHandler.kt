package io.github.remmerw.loki.grid

import kotlinx.io.Buffer

internal class UnchokeHandler : UniqueMessageHandler(Type.Unchoke) {
    override fun doDecode(peer: Peer, buffer: Buffer): Message {
        return unchoke()
    }

    override fun doEncode(peer: Peer, message: Message, buffer: Buffer) {
    }
}
