package io.github.remmerw.loki.data

import kotlinx.io.Buffer

internal class PortHandler : UniqueMessageHandler(Type.Port) {
    override fun doDecode(peer: Peer, buffer: Buffer): Message {
        return decodePort(buffer)
    }

    override fun doEncode(peer: Peer, message: Message, buffer: Buffer) {
        val port = message as Port
        port.encode(buffer)
    }


    private fun decodePort(buffer: Buffer): Message {
        val port = checkNotNull(buffer.readShort()).toInt() and 0x0000FFFF
        return Port(port)
    }
}
