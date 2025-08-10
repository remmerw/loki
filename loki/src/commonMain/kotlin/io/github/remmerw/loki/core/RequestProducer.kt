package io.github.remmerw.loki.core

import io.github.remmerw.loki.MAX_OUTSTANDING_REQUESTS
import io.github.remmerw.loki.data.Cancel
import io.github.remmerw.loki.data.Request
import kotlin.math.min

internal class RequestProducer(private val dataStorage: DataStorage) : Produces {


    override fun produce(connection: Connection) {

        if (dataStorage.initializeDone()) {
            val assignment = connection.assignment
            if (assignment == null) {
                resetConnection(connection)
                return
            }

            val assignedPieces = assignment.pieces
            if (assignedPieces.isEmpty()) {
                resetConnection(connection)
                return
            } else {
                var finishedPieces: MutableList<Int>? = null
                for (assignedPiece in assignedPieces) {
                    if (dataStorage.isComplete(assignedPiece)) {
                        if (finishedPieces == null) {
                            finishedPieces = ArrayList(assignedPieces.size + 1)
                        }
                        // delay removing piece from assignments to avoid CME
                        finishedPieces.add(assignedPiece)
                    } else if (connection.addPiece(assignedPiece)) {
                        val requests = buildRequests(connection, assignedPiece).shuffled()
                        connection.addRequests(requests)
                    }
                }
                finishedPieces?.forEach { finishedPiece: Int ->
                    assignment.finish(finishedPiece)
                    val dataBitfield = connection.dataBitfield()
                    if (dataBitfield != null) {
                        assignment.claimPiecesIfNeeded(dataBitfield)
                    }
                    connection.removePiece(finishedPiece)
                }
            }

            while (connection.pendingRequestsSize() <= MAX_OUTSTANDING_REQUESTS) {
                val request = connection.firstRequest()
                if (request == null) break
                val key = key(request.piece, request.offset)
                connection.postMessage(request)
                connection.pendingRequestsAdd(key)
            }
        }
    }


    private fun resetConnection(
        connection: Connection
    ) {
        connection.clearRequests()
        connection.clearPieces()
        connection.pendingRequests().forEach { key: Long ->

            val pieceIndex = key.shr(32).toInt()
            val offset = key.toInt()
            val chunk = dataStorage.chunk(pieceIndex)
            val chunkSize = chunk.chunkSize
            val blockSize = chunk.blockSize
            val length = min(blockSize, (chunkSize - offset))

            connection.postMessage(Cancel(pieceIndex, offset, length))
        }
        connection.pendingRequestsClear()
    }

    private fun buildRequests(connection: Connection, pieceIndex: Int): List<Request> {
        val requests: MutableList<Request> = mutableListOf()
        val chunk = dataStorage.chunk(pieceIndex)
        val chunkSize = chunk.chunkSize
        val blockSize = chunk.blockSize

        for (blockIndex in 0 until chunk.blockCount()) {
            if (!chunk.isPresent(blockIndex)) {
                val offset = (blockIndex * blockSize)
                val length = min(blockSize, (chunkSize - offset))

                if (!connection.pendingRequestsHas(key(pieceIndex, offset))) {
                    requests.add(Request(pieceIndex, offset, length))
                }
            }
        }

        return requests
    }
}



