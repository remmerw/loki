package io.github.remmerw.loki.core

import io.github.remmerw.loki.MAX_CONCURRENT_ACTIVE_PEERS_TORRENT
import io.github.remmerw.loki.MAX_PEER_CONNECTIONS
import io.github.remmerw.loki.PEER_INACTIVITY_THRESHOLD
import io.github.remmerw.loki.UPDATE_ASSIGNMENTS_MANDATORY_INTERVAL
import io.github.remmerw.loki.UPDATE_ASSIGNMENTS_OPTIONAL_INTERVAL
import io.github.remmerw.loki.grid.Message
import io.github.remmerw.loki.grid.Peer
import io.github.remmerw.loki.grid.Type
import io.github.remmerw.loki.grid.interested
import io.github.remmerw.loki.grid.notInterested
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlin.concurrent.Volatile
import kotlin.time.TimeSource
import kotlin.time.TimeSource.Monotonic.ValueTimeMark


internal class Worker(
    private val dataStorage: DataStorage,
    agents: List<Agent>
) {
    private val connections: MutableMap<Peer, Connection> = mutableMapOf()

    @Volatile
    private var lastUpdatedAssignments: ValueTimeMark = TimeSource.Monotonic.markNow()
    private val lock = reentrantLock()

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

    fun purgedConnections(): List<Connection> {
        lock.withLock {
            val purged: MutableList<Connection> = mutableListOf()
            val removing: MutableList<Connection> = mutableListOf()
            connections.values.forEach { connection: Connection ->
                if (connection.isClosed) {
                    removing.add(connection)
                } else if (
                    connection.lastActive.elapsedNow().inWholeMilliseconds
                    >= PEER_INACTIVITY_THRESHOLD
                ) {
                    removing.add(connection)
                } else {
                    purged.add(connection)
                }
            }
            removing.forEach { connection -> purgeConnection(connection) }
            return purged.toList()
        }
    }


    fun getConnection(peer: Peer): Connection? {
        lock.withLock {
            return connections[peer]
        }
    }

    fun mightAdd(): Boolean {
        lock.withLock {
            return connections.count() < MAX_PEER_CONNECTIONS
        }
    }

    fun addConnection(connection: Connection) {
        lock.withLock {
            check(connections.put(connection.peer(), connection) == null)
        }
    }


    fun purgeConnection(connection: Connection) {
        lock.withLock {
            connections.remove(connection.peer())
        }
        assignments.remove(connection)
        dataStorage.pieceStatistics()?.removeBitfield(connection)
        connection.close()
    }

    fun shutdown() {
        val connections = lock.withLock {
            connections.values.toList()
        }
        connections.forEach { connection: Connection -> connection.close() }
    }


    fun onConnected(connection: Connection) {
        lock.withLock {
            if (mightAdd()) {
                val worker = ConnectionWorker(connection, this)
                connection.connectionWorker = worker
            }
        }
    }


    fun consume(connection: Connection, message: Message) {
        val worker = connection.connectionWorker
        worker?.accept(message)
    }

    fun produce(connection: Connection): Message? {

        val worker = connection.connectionWorker
        if (worker != null) {
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
                return interestUpdate ?: worker.message()

            } else {
                return worker.message()
            }
        }

        return null
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
        purgedConnections().forEach { connection ->
            val worker = connection.connectionWorker
            if (worker != null) {
                if (connection.isPeerChoking) {
                    choking.add(connection)
                } else {
                    ready.add(connection)
                }
            }
        }

        val interesting = assignments.update(ready, choking)

        ready.forEach { connection: Connection ->
            if (!interesting.contains(connection)) {
                val worker = connection.connectionWorker
                if (worker != null) {
                    if (connection.isInterested) {
                        connection.interestUpdate = notInterested()
                        connection.isInterested = false
                    }
                }
            }
        }

        choking.forEach { connection: Connection ->
            val worker = connection.connectionWorker
            if (worker != null) {
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
        }

        lastUpdatedAssignments = TimeSource.Monotonic.markNow()
    }


}