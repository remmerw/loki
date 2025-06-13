package io.github.remmerw.loki.grid.core

import io.github.remmerw.loki.grid.choke
import kotlinx.io.Buffer

internal class ChokeHandler : UniqueMessageHandler(Type.Choke) {
    override fun doDecode(peer: Peer, buffer: Buffer): Message {
        return choke()
    }

    override fun doEncode(peer: Peer, message: Message, buffer: Buffer) {
    }
}
