package io.github.remmerw.loki.data

import kotlinx.io.Buffer

internal class HaveHandler : UniqueMessageHandler(Type.Have) {
    override fun doDecode(peer: Peer, buffer: Buffer): Message {
        return decodeHave(buffer)
    }

    override fun doEncode(peer: Peer, message: Message, buffer: Buffer) {
        val have = message as Have
        writeHave(have.pieceIndex, buffer)
    }


    private fun writeHave(pieceIndex: Int, buffer: Buffer) {
        require(pieceIndex >= 0) { "Invalid piece index: $pieceIndex" }
        buffer.writeInt(pieceIndex)
    }

    private fun decodeHave(buffer: Buffer): Message {
        val pieceIndex = checkNotNull(buffer.readInt())
        return Have(pieceIndex)
    }
}