package io.github.remmerw.loki.grid

import io.ktor.utils.io.core.readBytes
import kotlinx.io.Buffer

internal class PieceHandler : UniqueMessageHandler(Type.Piece) {
    override fun doDecode(peer: Peer, buffer: Buffer): Message {
        val pieceIndex = checkNotNull(buffer.readInt())
        val blockOffset = checkNotNull(buffer.readInt())
        val data = buffer.readBytes()

        return Piece(pieceIndex, blockOffset, data)
    }

    override fun doEncode(peer: Peer, message: Message, buffer: Buffer) {
        val piece = message as Piece

        // piece: <len=0009+X><id=7><index><begin><block>
        val pieceIndex = piece.pieceIndex
        val offset = piece.offset

        buffer.writeInt(pieceIndex)
        buffer.writeInt(offset)
        buffer.write(piece.data)
    }
}