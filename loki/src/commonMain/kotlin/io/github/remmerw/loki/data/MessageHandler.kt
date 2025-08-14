package io.github.remmerw.loki.data

import io.github.remmerw.buri.BEReader
import kotlinx.io.Buffer
import java.net.InetSocketAddress

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

