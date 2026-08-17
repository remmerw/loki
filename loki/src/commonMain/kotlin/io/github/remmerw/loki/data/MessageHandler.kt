package io.github.remmerw.loki.data

import io.github.remmerw.buri.BEReader
import io.github.remmerw.nott.Address
import kotlinx.io.Sink

interface MessageHandler {
    /**
     * Tries to encode the provided message and place the result into the byte buffer.
     */
    fun doEncode(
        message: ExtendedMessage,
        sink: Sink,
    )

    fun doDecode(
        address: Address,
        reader: BEReader,
    ): ExtendedMessage

    /**
     * @return All message types, supported by this protocol.
     */
    fun supportedTypes(): Collection<Type>
}
