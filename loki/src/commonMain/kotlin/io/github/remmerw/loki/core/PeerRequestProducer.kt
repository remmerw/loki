package io.github.remmerw.loki.core

import io.github.remmerw.loki.data.Piece

internal class PeerRequestProducer(
    private val dataStorage: DataStorage
) : Produces {


    override fun produce(connection: Connection) {
        if (dataStorage.initializeDone()) {
            do {
                val request = connection.firstCompleteRequest()

                if (request != null) {
                    val length = request.length
                    val offset = request.offset
                    val piece = request.piece
                    connection.postMessage(Piece(piece, offset, length))
                }
            } while (request != null)
        }
    }
}
