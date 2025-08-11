package io.github.remmerw.loki.data

import io.github.remmerw.buri.BEReader
import io.ktor.network.sockets.InetSocketAddress
import kotlinx.io.Buffer

interface MessageHandler {
    /**
     * Tries to encode the provided message and place the result into the byte buffer.
     */
    fun doEncode(message: ExtendedMessage, buffer: Buffer)

    fun doDecode(address: InetSocketAddress, reader: BEReader): ExtendedMessage

    /**
     * @return All message types, supported by this protocol.
     */
    fun supportedTypes(): Collection<Type>
}

