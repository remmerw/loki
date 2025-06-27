package io.github.remmerw.loki.data

import kotlinx.io.Buffer

internal class UnchokeHandler : UniqueMessageHandler(Type.Unchoke) {
    override fun doDecode(peer: Peer, buffer: Buffer): Message {
        return unchoke()
    }

    override fun doEncode(peer: Peer, message: Message, buffer: Buffer) {
    }
}
