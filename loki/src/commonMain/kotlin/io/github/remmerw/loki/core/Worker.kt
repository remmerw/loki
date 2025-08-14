package io.github.remmerw.loki.core

import io.github.remmerw.loki.PEER_INACTIVITY_THRESHOLD
import io.github.remmerw.loki.UPDATE_ASSIGNMENTS_OPTIONAL_INTERVAL
import io.github.remmerw.loki.data.ExtendedMessage
import io.github.remmerw.loki.data.Message
import io.github.remmerw.loki.data.Type
import io.github.remmerw.loki.data.interested
import io.github.remmerw.loki.data.notInterested
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.time.TimeSource
import kotlin.time.TimeSource.Monotonic.ValueTimeMark


internal class Worker(
    private val dataStorage: DataStorage,
    agents: List<Agent>
) {
    private val connections: MutableMap<InetSocketAddress, Connection> = ConcurrentHashMap()
    private val bitfields: MutableMap<Connection, ByteArray> = ConcurrentHashMap()
    private val haves: MutableMap<Connection, MutableSet<Int>> = mutableMapOf()
    private val lock = reentrantLock()

    @Volatile
    private var lastUpdatedAssignments: ValueTimeMark = TimeSource.Monotonic.markNow()
    private val assignments: Assignments = Assignments(dataStorage)
    private val consumers: Map<Type, List<MessageConsumer>>
    private val producers: Set<MessageProducer>

    init {
        val cons: MutableMap<Type, MutableList<MessageConsumer>> = mutableMapOf()
        val prods: MutableSet<MessageProducer> = mutableSetOf()

        agents.forEach { agent ->
            if (agent is Consumers) {
                agent.consumers.forEach { consumer: MessageConsumer ->
                    val consumedType = consumer.consumedType()
                    cons.getOrPut(consumedType) { mutableListOf() }
                        .add(consumer)

                }
            }

            if (agent is Produces) {
                prods.add(object : MessageProducer {
                    override fun produce(
                        connection: Connection
                    ) {
                        agent.produce(connection)
                    }
                })
            }
        }
        consumers = cons.toMap()
        producers = prods.toSet()
    }

    fun consumeBitfield(bitfield: ByteArray, connection: Connection) {
        bitfields[connection] = bitfield
    }

    fun consumeHave(piece: Int, connection: Connection) {
        lock.withLock {
            val peerHaves = haves.getOrPut(connection) { mutableSetOf() }
            peerHaves.add(piece)
        }
    }

    // process bitfields and haves that we received while fetching metadata
    fun processMessages() {

        val pieceStatistics = dataStorage.pieceStatistics()!!
        val piecesTotal = dataStorage.piecesTotal()

        require(piecesTotal > 0) { "Pieces total not yet defined" }
        bitfields.forEach { (connection: Connection, bitfieldBytes: ByteArray) ->
            if (!connection.hasDataBitfield()) { // the else case should never happen
                val dataBitfield = DataBitfield(
                    piecesTotal,
                    Bitmask.decode(bitfieldBytes, piecesTotal)
                )
                connection.setDataBitfield(dataBitfield)
                pieceStatistics.addBitfield(dataBitfield)
            }
        }
        lock.withLock {
            haves.forEach { connection: Connection, pieces: MutableSet<Int> ->
                pieces.forEach { piece: Int -> pieceStatistics.addPiece(connection, piece) }
            }
        }
    }


    fun consume(message: Message, connection: Connection) {
        if (message is ExtendedMessage) {
            val consumers: Collection<MessageConsumer>? = consumers[message.type]
            consumers?.forEach { consumer: MessageConsumer ->
                consumer.consume(
                    message,
                    connection
                )
            }
        }
    }

    fun produce(connection: Connection) {
        producers.forEach { producer: MessageProducer ->
            producer.produce(connection)
        }
    }

    fun connections(): List<Connection> {
        return connections.values.toList()
    }

    @OptIn(ExperimentalAtomicApi::class)
    fun purgedConnections(): Int {

        val purgedConnections = AtomicInt(0)
        val removing: MutableList<Connection> = mutableListOf()
        connections.values.forEach { connection: Connection ->
            if (connection.lastActive.elapsedNow().inWholeMilliseconds
                >= PEER_INACTIVITY_THRESHOLD
            ) {
                removing.add(connection)
            } else {
                purgedConnections.incrementAndFetch()
            }
        }
        removing.forEach { connection -> connection.close() }
        return purgedConnections.load()

    }


    fun getConnection(peer: InetSocketAddress): Connection? {
        return connections[peer]
    }

    fun addConnection(connection: Connection) {
        check(connections.put(connection.address(), connection) == null)
    }


    fun purgeConnection(connection: Connection) {

        connections.remove(connection.address())

        assignments.remove(connection)
        dataStorage.pieceStatistics()?.removeBitfield(connection)
    }

    fun shutdown() {
        val connections = connections.values.toList()

        connections.forEach { connection: Connection -> connection.close() }
    }


    fun producedMessage(connection: Connection): Message? {

        val bitfield = dataStorage.dataBitfield()

        if (bitfield != null &&
            (bitfield.piecesRemaining() > 0 || assignments.count() > 0)
        ) {
            inspectAssignment(connection, assignments)
            if (shouldUpdateAssignments()) {
                connection.interestUpdate = null
                updateAssignments(assignments)
            }
            val interestUpdate = connection.interestUpdate
            connection.interestUpdate = null
            return interestUpdate ?: connection.nextMessage()

        } else {
            return connection.nextMessage()
        }
    }


    private fun inspectAssignment(
        connection: Connection,
        assignments: Assignments
    ) {
        val assignment = connection.assignment
        if (assignment != null) {
            if (assignment.status == Assignment.Status.TIMEOUT) {
                assignments.remove(connection)
            } else if (connection.isPeerChoking) {
                assignments.remove(connection)
            }
        } else if (!connection.isPeerChoking) {
            assignments.assign(connection)
        }
    }


    private fun shouldUpdateAssignments(): Boolean {
        val elapsed = lastUpdatedAssignments.elapsedNow().inWholeMilliseconds
        return elapsed > UPDATE_ASSIGNMENTS_OPTIONAL_INTERVAL
    }


    private fun updateAssignments(assignments: Assignments) {

        val ready: MutableSet<Connection> = mutableSetOf()
        val choking: MutableSet<Connection> = mutableSetOf()
        connections().forEach { connection ->
            if (connection.isPeerChoking) {
                choking.add(connection)
            } else {
                ready.add(connection)
            }
        }

        val interesting = assignments.update(ready, choking)

        ready.forEach { connection: Connection ->
            if (!interesting.contains(connection)) {
                if (connection.isInterested) {
                    connection.interestUpdate = notInterested()
                    connection.isInterested = false
                }
            }
        }

        choking.forEach { connection: Connection ->
            if (interesting.contains(connection)) {
                if (!connection.isInterested) {
                    connection.interestUpdate = interested()
                    connection.isInterested = true
                }
            } else if (connection.isInterested) {
                connection.interestUpdate = notInterested()
                connection.isInterested = false
            }
        }

        lastUpdatedAssignments = TimeSource.Monotonic.markNow()
    }


}