package io.github.remmerw.loki.grid.core

import io.github.remmerw.loki.grid.unchoke
import kotlinx.io.Buffer

internal class UnchokeHandler : UniqueMessageHandler(Type.Unchoke) {
    override fun doDecode(peer: Peer, buffer: Buffer): Message {
        return unchoke()
    }

    override fun doEncode(peer: Peer, message: Message, buffer: Buffer) {
    }
}
