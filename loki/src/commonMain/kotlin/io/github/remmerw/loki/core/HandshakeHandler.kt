package io.github.remmerw.loki.core

import io.github.remmerw.loki.grid.core.Handshake

internal interface HandshakeHandler {
    /**
     * Process an incoming handshake, received from a remote peer.
     * Implementations are free to send messages via the provided connection
     * and may choose to close it if some of their expectations about the handshake are not met.
     *
     *
     * Attempt to read from the provided connection will trigger an [UnsupportedOperationException].
     *
     */
    suspend fun processIncomingHandshake(connection: Connection)

    /**
     * Make amendments to an outgoing handshake, that will be sent to a remote peer.
     */
    fun processOutgoingHandshake(handshake: Handshake)
}