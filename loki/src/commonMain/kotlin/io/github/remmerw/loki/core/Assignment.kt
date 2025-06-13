package io.github.remmerw.loki.core

import io.github.remmerw.loki.MAX_PIECE_RECEIVING_TIME
import io.github.remmerw.loki.MAX_SIMULTANEOUSLY_ASSIGNED_PIECES
import kotlin.time.TimeSource
import kotlin.time.TimeSource.Monotonic.ValueTimeMark

internal class Assignment internal constructor(
    private val dataStorage: DataStorage,
    private val assignments: Assignments
) {

    val pieces: ArrayDeque<Int> = ArrayDeque()

    private var started: ValueTimeMark? = null
    private var checked: ValueTimeMark? = null


    internal fun claimPiecesIfNeeded(dataBitfield: DataBitfield) {
        if (pieces.size < MAX_SIMULTANEOUSLY_ASSIGNED_PIECES) {


            // randomize order of pieces to keep the number of pieces
            // requested from different peers at the same time to a minimum
            val requiredPieces = dataStorage.nextPieces()
            if (assignments.isEndgame()) {
                requiredPieces.shuffle()
            }

            var i = 0
            while (i < requiredPieces.size && pieces.size < 3) {
                val pieceIndex = requiredPieces[i]
                if (dataBitfield.isVerified(pieceIndex) && assignments.claim(pieceIndex)) {
                    pieces.add(pieceIndex)
                }
                i++
            }
        }
    }

    fun isAssigned(pieceIndex: Int): Boolean {
        return pieces.contains(pieceIndex)
    }

    val status: Status
        get() {
            if (started != null) {
                val duration = checked!!.elapsedNow().inWholeMilliseconds
                if (duration > MAX_PIECE_RECEIVING_TIME) {
                    return Status.TIMEOUT
                }
            }
            return Status.ACTIVE
        }

    fun start() {
        started = TimeSource.Monotonic.markNow()
        checked = started
    }

    fun check() {
        checked = TimeSource.Monotonic.markNow()
    }

    fun finish(pieceIndex: Int) {
        if (pieces.remove(pieceIndex)) {
            assignments.finish(pieceIndex)
        }
    }

    enum class Status {
        ACTIVE, TIMEOUT
    }
}

