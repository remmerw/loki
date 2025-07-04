package io.github.remmerw.loki.data

import io.ktor.network.sockets.InetSocketAddress
import kotlinx.io.Buffer
import kotlinx.io.readByteArray

internal class BitfieldHandler : UniqueMessageHandler(Type.Bitfield) {
    override fun doDecode(address: InetSocketAddress, buffer: Buffer): Message {
        return Bitfield(buffer.readByteArray())
    }

    override fun doEncode(address: InetSocketAddress, message: Message, buffer: Buffer) {
        val bitfield = message as Bitfield
        bitfield.encode(buffer)
    }
}
