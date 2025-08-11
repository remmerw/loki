package io.github.remmerw.loki.core

import io.ktor.util.collections.ConcurrentSet
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.decrementAndFetch
import kotlin.concurrent.atomics.incrementAndFetch

internal class Assignments(private val dataStorage: DataStorage) {
    private val assignedPieces: MutableSet<Int> = ConcurrentSet()

    @OptIn(ExperimentalAtomicApi::class)
    private val assignments: AtomicInt = AtomicInt(0)

    fun remove(connection: Connection) {
        val assignment = connection.assignment
        if (assignment != null) {
            remove(connection, assignment)
        }
    }

    @OptIn(ExperimentalAtomicApi::class)
    private fun remove(connection: Connection, assignment: Assignment) {
        connection.assignment = null
        assignments.decrementAndFetch()
        assignedPieces.removeAll(assignment.pieces)
    }

    @OptIn(ExperimentalAtomicApi::class)
    fun count(): Int {
        return assignments.load()
    }

    @OptIn(ExperimentalAtomicApi::class)
    fun assign(connection: Connection) {
        if (!hasInterestingPieces(connection)) {
            return
        }
        val dataBitfield = connection.dataBitfield()
        checkNotNull(dataBitfield)

        val assignment = Assignment(dataStorage, this)
        assignment.claimPiecesIfNeeded(dataBitfield)
        connection.assignment = assignment
        assignments.incrementAndFetch()
        assignment.start()
    }

    fun claim(pieceIndex: Int): Boolean {
        val claimed = !dataStorage.isComplete(pieceIndex) && (isEndgame()
                || !assignedPieces.contains(pieceIndex))
        if (claimed) {
            assignedPieces.add(pieceIndex)
        }
        return claimed
    }

    fun finish(pieceIndex: Int) {
        assignedPieces.remove(pieceIndex)
    }

    fun isEndgame(): Boolean {
        // if all remaining pieces are requested,
        // that would mean that we have entered the "endgame" mode
        val bitfield = dataStorage.dataBitfield()
        return if (bitfield == null) {
            false
        } else {
            bitfield.piecesRemaining() <= assignedPieces.size
        }
    }

    /**
     * @return Collection of peers that have interesting pieces and can be given an assignment
     */
    fun update(ready: Set<Connection>, choking: Set<Connection>): Set<Connection> {
        val result: MutableSet<Connection> = mutableSetOf()
        for (connection in ready) {
            if (hasInterestingPieces(connection)) {
                result.add(connection)
            }
        }
        for (connection in choking) {
            if (hasInterestingPieces(connection)) {
                result.add(connection)
            }
        }

        return result
    }

    private fun hasInterestingPieces(connection: Connection): Boolean {
        dataStorage.pieceStatistics() ?: return false
        val peerBitfield = connection.dataBitfield() ?: return false
        val bitfield = dataStorage.dataBitfield() ?: return false

        val peerBitmask = peerBitfield.clonedBitmask()
        peerBitmask.andNot(bitfield.bitmask)
        return peerBitmask.cardinality() > 0
    }
}
