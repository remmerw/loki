package io.github.remmerw.loki.core

import io.github.remmerw.loki.MAX_CONCURRENT_ACTIVE_PEERS_TORRENT
import io.github.remmerw.loki.MAX_PEER_CONNECTIONS
import io.github.remmerw.loki.PEER_INACTIVITY_THRESHOLD
import io.github.remmerw.loki.UPDATE_ASSIGNMENTS_MANDATORY_INTERVAL
import io.github.remmerw.loki.UPDATE_ASSIGNMENTS_OPTIONAL_INTERVAL
import io.github.remmerw.loki.data.Message
import io.github.remmerw.loki.data.Type
import io.github.remmerw.loki.data.interested
import io.github.remmerw.loki.data.notInterested
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.util.collections.ConcurrentMap
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
    private val connections: MutableMap<InetSocketAddress, Connection> = ConcurrentMap()

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
                        connection: Connection,
                        messageConsumer: (Message) -> Unit
                    ) {
                        agent.produce(connection, messageConsumer)
                    }
                })
            }
        }
        consumers = cons.toMap()
        producers = prods.toSet()
    }


    fun consume(message: Message, connection: Connection) {
        val consumers: Collection<MessageConsumer>? = consumers[message.type]
        consumers?.forEach { consumer: MessageConsumer ->
            consumer.consume(
                message,
                connection
            )
        }
    }

    fun produce(connection: Connection, messageConsumer: (Message) -> Unit) {
        producers.forEach { producer: MessageProducer ->
            producer.produce(connection, messageConsumer)
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

    fun mightAdd(): Boolean {

        return connections.count() < MAX_PEER_CONNECTIONS

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


    fun consume(connection: Connection, message: Message) {
        connection.accept(message)
    }

    fun produce(connection: Connection): Message? {

        val bitfield = dataStorage.dataBitfield()

        if (bitfield != null &&
            (bitfield.piecesRemaining() > 0 || assignments.count() > 0)
        ) {
            inspectAssignment(connection, assignments)
            if (shouldUpdateAssignments(assignments)) {
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
            if (mightCreateMoreAssignments(assignments)) {
                assignments.assign(connection)
            }
        }
    }


    private fun mightUseMoreAssignees(assignments: Assignments): Boolean {
        return assignments.count() < MAX_CONCURRENT_ACTIVE_PEERS_TORRENT
    }

    private fun mightCreateMoreAssignments(assignments: Assignments): Boolean {
        return assignments.count() < MAX_CONCURRENT_ACTIVE_PEERS_TORRENT
    }

    private fun shouldUpdateAssignments(assignments: Assignments): Boolean {
        val elapsed = lastUpdatedAssignments.elapsedNow().inWholeMilliseconds
        return (elapsed > UPDATE_ASSIGNMENTS_OPTIONAL_INTERVAL
                && mightUseMoreAssignees(assignments))
                || elapsed > UPDATE_ASSIGNMENTS_MANDATORY_INTERVAL
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