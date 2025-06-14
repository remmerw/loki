package io.github.remmerw.loki.grid

import kotlinx.io.Buffer
import kotlinx.io.readByteArray

internal class BitfieldHandler : UniqueMessageHandler(Type.Bitfield) {
    override fun doDecode(peer: Peer, buffer: Buffer): Message {
        return Bitfield(buffer.readByteArray())
    }

    override fun doEncode(peer: Peer, message: Message, buffer: Buffer) {
        val bitfield = message as Bitfield
        buffer.write(bitfield.bitfield)
    }
}
