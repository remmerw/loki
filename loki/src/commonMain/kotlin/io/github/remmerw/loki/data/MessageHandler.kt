package io.github.remmerw.loki.data

import kotlinx.io.Buffer

interface MessageHandler {
    /**
     * Tries to encode the provided message and place the result into the byte buffer.
     */
    fun doEncode(peer: Peer, message: Message, buffer: Buffer)

    /**
     * Tries to decode message from the byte buffer. If decoding is successful, then the
     * message is set into result
     */
    fun doDecode(peer: Peer, buffer: Buffer): Message

    /**
     * @return All message types, supported by this protocol.
     */
    fun supportedTypes(): Collection<Type>
}

