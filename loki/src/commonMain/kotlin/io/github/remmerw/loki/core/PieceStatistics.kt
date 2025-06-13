package io.github.remmerw.loki.core

import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock

internal data class PieceStatistics(private val piecesTotal: Int) {
    private val pieces: IntArray = IntArray(piecesTotal)
    private val lock = reentrantLock()

    /**
     * Add connection's bitfield.
     * For each piece, that the peer has, total count will be incremented by 1.
     */
    fun addBitfield(connection: Connection, dataBitfield: DataBitfield) {
        validateBitfieldLength(dataBitfield)
        connection.setDataBitfield(dataBitfield)
        lock.withLock {
            for (i in pieces.indices) {
                if (dataBitfield.pieceStatus(i) == DataBitfield.PieceStatus.COMPLETE_VERIFIED) {
                    pieces[i]++
                }
            }
        }
    }

    fun rarestFirst(): List<Int> {
        val data: MutableMap<Int, MutableList<Int>> = mutableMapOf()

        for (pieceIndex in 0 until piecesTotal) {
            val count = getCount(pieceIndex)
            if (count > 0) {
                val list = data.getOrPut(count) { mutableListOf() }
                list.add(pieceIndex)

            }
        }
        return data.map { i -> i.value.shuffled() }.flatten()
    }


    /**
     * Remove connection's bitfield.
     * For each piece, that the connection has, total count will be decremented by 1.
     */
    fun removeBitfield(connection: Connection) {
        val bitfield = connection.dataBitfield() ?: return
        lock.withLock {
            for (i in pieces.indices) {
                if (bitfield.pieceStatus(i) == DataBitfield.PieceStatus.COMPLETE_VERIFIED) {
                    pieces[i]--
                }
            }
        }
        connection.removeDataBitfield()
    }


    private fun validateBitfieldLength(dataBitfield: DataBitfield) {
        require(dataBitfield.piecesTotal == pieces.size) {
            "Bitfield has invalid length (" + dataBitfield.piecesTotal +
                    "). Expected number of pieces: " + pieces.size
        }
    }

    /**
     * Update peer's bitfield by indicating that the peer has a given piece.
     * Total count of the specified piece will be incremented by 1.
     */
    fun addPiece(connection: Connection, pieceIndex: Int) {
        lock.withLock {
            var bitfield = connection.dataBitfield()
            if (bitfield == null) {
                bitfield = DataBitfield(piecesTotal, Bitmask(piecesTotal))
                connection.setDataBitfield(bitfield)
            }

            markPieceVerified(bitfield, pieceIndex)
        }
    }


    private fun markPieceVerified(dataBitfield: DataBitfield, pieceIndex: Int) {
        lock.withLock {
            if (!dataBitfield.isVerified(pieceIndex)) {
                dataBitfield.markVerified(pieceIndex)
                pieces[pieceIndex]++
            }
        }
    }

    private fun getCount(pieceIndex: Int): Int {
        lock.withLock {
            return pieces[pieceIndex]
        }
    }


    override fun hashCode(): Int {
        var result = piecesTotal
        result = 31 * result + pieces.contentHashCode()
        result = 31 * result + lock.hashCode()
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as PieceStatistics

        if (piecesTotal != other.piecesTotal) return false
        if (!pieces.contentEquals(other.pieces)) return false
        if (lock != other.lock) return false

        return true
    }

}
