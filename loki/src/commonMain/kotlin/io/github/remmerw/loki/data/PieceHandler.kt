package io.github.remmerw.loki.data

import io.ktor.network.sockets.InetSocketAddress
import io.ktor.utils.io.core.readBytes
import kotlinx.io.Buffer

internal class PieceHandler : UniqueMessageHandler(Type.Piece) {
    override fun doDecode(address: InetSocketAddress, buffer: Buffer): Message {
        val pieceIndex = checkNotNull(buffer.readInt())
        val blockOffset = checkNotNull(buffer.readInt())
        val data = buffer.readBytes()

        return Piece(pieceIndex, blockOffset, data)
    }

    override fun doEncode(address: InetSocketAddress, message: Message, buffer: Buffer) {
        val piece = message as Piece
        piece.encode(buffer)
    }
}