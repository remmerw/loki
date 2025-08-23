package io.github.remmerw.loki.core

import io.github.remmerw.loki.data.Have

internal class PieceProducer(
    private val dataStorage: DataStorage
) : Produces {


    override fun produce(connection: Connection) {
        if (dataStorage.initializeDone()) {
            dataStorage.completePieces().forEach { index ->
                if (connection.addHave(index)) {
                    connection.postMessage(Have(index))
                    return
                }
            }
        }
    }
}
