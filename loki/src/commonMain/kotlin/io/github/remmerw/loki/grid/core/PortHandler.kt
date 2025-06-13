package io.github.remmerw.loki.grid.core

import kotlinx.io.Buffer

internal class PortHandler : UniqueMessageHandler(Type.Port) {
    override fun doDecode(peer: Peer, buffer: Buffer): Message {
        return decodePort(buffer)
    }

    override fun doEncode(peer: Peer, message: Message, buffer: Buffer) {
        val port = message as Port
        writePort(port.port, buffer)
    }


    // port: <len=0003><id=9><listen-port>
    private fun writePort(port: Int, buffer: Buffer) {
        if (port < 0 || port > Short.MAX_VALUE * 2 + 1) {
            throw RuntimeException("Invalid port: $port")
        }

        /**
         * 2-bytes binary representation of a [Short].
         */
        buffer.write(
            byteArrayOf(
                (port shr 8).toByte(),
                port.toByte()
            )
        )
    }

    private fun decodePort(buffer: Buffer): Message {
        val port = checkNotNull(buffer.readShort()).toInt() and 0x0000FFFF
        return Port(port)
    }
}
