package io.github.remmerw.loki.data

import kotlinx.io.Buffer

internal class HaveHandler : UniqueMessageHandler(Type.Have) {
    override fun doDecode(peer: Peer, buffer: Buffer): Message {
        val pieceIndex = buffer.readInt()
        return Have(pieceIndex)
    }

    override fun doEncode(peer: Peer, message: Message, buffer: Buffer) {
        val have = message as Have
        have.encode(buffer)
    }
}