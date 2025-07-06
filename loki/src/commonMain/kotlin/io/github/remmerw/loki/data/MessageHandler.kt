package io.github.remmerw.loki.data

import io.ktor.network.sockets.InetSocketAddress
import kotlinx.io.Buffer

interface MessageHandler {
    /**
     * Tries to encode the provided message and place the result into the byte buffer.
     */
    fun doEncode(message: ExtendedMessage, buffer: Buffer)

    /**
     * Tries to decode message from the byte buffer. If decoding is successful, then the
     * message is set into result
     */
    fun doDecode(address: InetSocketAddress, buffer: Buffer): ExtendedMessage

    /**
     * @return All message types, supported by this protocol.
     */
    fun supportedTypes(): Collection<Type>
}

