package io.github.remmerw.loki.core

import io.github.remmerw.loki.data.Bitfield
import io.github.remmerw.loki.data.Handshake

internal data class BitfieldConnectionHandler(
    private val dataStorage: DataStorage,
) : HandshakeHandler {
    override suspend fun processIncomingHandshake(
        connection: Connection
    ) {

        val bitfield = dataStorage.dataBitfield()
        if (bitfield != null) {
            if (bitfield.piecesComplete() > 0) {
                val bitfieldMessage =
                    Bitfield(
                        bitfield.encode()
                    )
                connection.posting(bitfieldMessage, byteArrayOf())
            }
        }
    }

    override fun processOutgoingHandshake(handshake: Handshake) {
        // do nothing
    }
}
