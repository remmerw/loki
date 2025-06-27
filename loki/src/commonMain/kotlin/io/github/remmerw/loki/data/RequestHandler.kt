package io.github.remmerw.loki.data

import kotlinx.io.Buffer

internal class RequestHandler : UniqueMessageHandler(Type.Request) {
    override fun doDecode(peer: Peer, buffer: Buffer): Message {
        return decodeRequest(buffer)
    }

    override fun doEncode(peer: Peer, message: Message, buffer: Buffer) {
        val request = message as Request
        writeRequest(request.piece, request.offset, request.length, buffer)
    }

    // request: <len=0013><id=6><index><begin><length>
    private fun writeRequest(
        pieceIndex: Int,
        offset: Int,
        length: Int,
        buffer: Buffer
    ) {
        require(!(pieceIndex < 0 || offset < 0 || length <= 0)) {
            ("Invalid arguments: pieceIndex (" + pieceIndex
                    + "), offset (" + offset + "), length (" + length + ")")
        }

        buffer.writeInt(pieceIndex)
        buffer.writeInt(offset)
        buffer.writeInt(length)

    }

    private fun decodeRequest(buffer: Buffer): Message {
        val pieceIndex = checkNotNull(buffer.readInt())
        val blockOffset = checkNotNull(buffer.readInt())
        val blockLength = checkNotNull(buffer.readInt())

        return Request(pieceIndex, blockOffset, blockLength)
    }
}
