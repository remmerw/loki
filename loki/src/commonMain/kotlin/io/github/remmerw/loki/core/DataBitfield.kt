package io.github.remmerw.loki.core

import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlin.concurrent.Volatile

internal data class DataBitfield(
    val piecesTotal: Int,
    private val bitmask: Bitmask
) {
    private val lock = reentrantLock()

    /**
     * Bitmask indicating pieces that should be skipped.
     * If the n-th bit is set, then the n-th piece should be skipped.
     */
    @Volatile
    private var skipped: Bitmask? = null

    /**
     * @return Bitmask that describes status of all pieces.
     * If the n-th bit is set, then the n-th piece
     * is in [PieceStatus.COMPLETE_VERIFIED] status.
     */
    fun clonedBitmask(): Bitmask {
        lock.withLock {
            return bitmask.clone()
        }
    }

    /**
     * @return Bitmask that describes status of all pieces.
     * If the n-th bit is set, then the n-th piece
     * is in [PieceStatus.COMPLETE_VERIFIED] status.
     */
    fun encode(): ByteArray {
        lock.withLock {
            return bitmask.encode(piecesTotal)
        }
    }

    fun piecesComplete(): Int {
        lock.withLock {
            return bitmask.cardinality()
        }
    }

    fun piecesRemaining(): Int {
        lock.withLock {
            if (skipped == null) {
                return piecesTotal - bitmask.cardinality()
            } else {
                val bitmask = clonedBitmask()
                bitmask.or(skipped!!)
                return piecesTotal - bitmask.cardinality()
            }

        }
    }

    fun pieceStatus(pieceIndex: Int): PieceStatus {
        lock.withLock {
            validatePieceIndex(pieceIndex)

            val verified: Boolean = bitmask[pieceIndex]

            return if (verified) {
                PieceStatus.COMPLETE_VERIFIED
            } else {
                PieceStatus.INCOMPLETE
            }

        }
    }

    /**
     * Shortcut method to find out if the piece has been downloaded.
     *
     * @param pieceIndex Piece index (0-based)
     * @return true if the piece has been downloaded
     */
    fun isComplete(pieceIndex: Int): Boolean {
        val pieceStatus = pieceStatus(pieceIndex)
        return (pieceStatus == PieceStatus.COMPLETE || pieceStatus == PieceStatus.COMPLETE_VERIFIED)
    }

    /**
     * Shortcut method to find out if the piece has been downloaded and verified.
     *
     * @param pieceIndex Piece index (0-based)
     * @return true if the piece has been downloaded and verified
     */
    fun isVerified(pieceIndex: Int): Boolean {
        val pieceStatus = pieceStatus(pieceIndex)
        return pieceStatus == PieceStatus.COMPLETE_VERIFIED
    }

    /**
     * Mark piece as complete and verified.
     *
     * @param pieceIndex Piece index (0-based)
     */
    fun markVerified(pieceIndex: Int) {
        assertChunkComplete(pieceIndex)

        lock.withLock {
            bitmask.set(pieceIndex)
        }
    }

    private fun assertChunkComplete(pieceIndex: Int) {
        validatePieceIndex(pieceIndex)
    }

    private fun validatePieceIndex(pieceIndex: Int) {
        if (pieceIndex < 0 || pieceIndex >= piecesTotal) {
            throw RuntimeException(
                "Illegal piece index: " + pieceIndex +
                        ", expected 0.." + (piecesTotal - 1)
            )
        }
    }

    /**
     * Mark a piece as skipped
     */
    fun skip(pieceIndex: Int) {
        validatePieceIndex(pieceIndex)

        lock.withLock {
            if (skipped == null) {
                skipped = Bitmask(piecesTotal)
            }
            skipped!!.set(pieceIndex)
        }
    }

    /**
     * Status of a particular piece.
     */
    enum class PieceStatus {
        INCOMPLETE, COMPLETE, COMPLETE_VERIFIED
    }

}