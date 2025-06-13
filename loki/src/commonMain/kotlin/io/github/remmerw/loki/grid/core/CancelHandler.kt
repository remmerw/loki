package io.github.remmerw.loki.grid.core

import kotlinx.io.Buffer

internal class CancelHandler : UniqueMessageHandler(Type.Cancel) {
    override fun doDecode(peer: Peer, buffer: Buffer): Message {
        return decodeCancel(buffer)
    }

    override fun doEncode(peer: Peer, message: Message, buffer: Buffer) {
        val cancel = message as Cancel
        writeCancel(cancel.pieceIndex, cancel.offset, cancel.length, buffer)
    }

    // cancel: <len=0013><id=8><index><begin><length>
    private fun writeCancel(
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

    private fun decodeCancel(buffer: Buffer): Message {

        val pieceIndex = checkNotNull(buffer.readInt())
        val blockOffset = checkNotNull(buffer.readInt())
        val blockLength = checkNotNull(buffer.readInt())
        return Cancel(pieceIndex, blockOffset, blockLength)
    }
}
