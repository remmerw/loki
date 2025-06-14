package io.github.remmerw.loki.mdht

import io.github.remmerw.loki.debug
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.BoundDatagramSocket
import io.ktor.network.sockets.Datagram
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.aSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlinx.io.writeUShort
import kotlin.random.Random


class Mdht internal constructor(peerId: ByteArray, val port: Int) {
    private val channel = Channel<EnqueuedSend>()
    internal val node: Node = Node(peerId, channel)
    private val selectorManager = SelectorManager(Dispatchers.IO)
    private var socket: BoundDatagramSocket? = null

    suspend fun startup(addresses: List<InetSocketAddress>) {
        socket = aSocket(selectorManager).udp().bind(
            InetSocketAddress("::", port)
        )

        selectorManager.launch {
            while (isActive) {
                val datagram = socket!!.receive()
                node.handleDatagram(datagram)
            }
        }

        selectorManager.launch {
            for (es in channel) {
                send(es)
            }
        }

        addresses.forEach { address: InetSocketAddress ->
            node.ping(address, null)
        }

    }

    private suspend fun send(es: EnqueuedSend) {
        // simply assume nobody else is writing and attempt to do it
        // if it fails it's the current writer's job to double-check after releasing the write lock

        try {
            val buffer = Buffer()
            es.message.encode(buffer)
            val address = es.message.address


            val datagram = Datagram(buffer, address)

            socket!!.send(datagram)

            es.associatedCall?.hasSend()

        } catch (throwable: Throwable) {
            debug("Mdht", throwable)

            if (es.associatedCall != null) {
                es.associatedCall.injectStall()
                node.timeout(es.associatedCall)
            }
        }
    }

    fun shutdown() {
        node.shutdown()

        try {
            channel.close()
        } catch (throwable: Throwable) {
            debug("Mdht", throwable)
        }

        try {
            selectorManager.close()
        } catch (throwable: Throwable) {
            debug("Mdht", throwable)
        }

        try {
            socket?.close()
        } catch (throwable: Throwable) {
            debug("Mdht", throwable)
        }
    }
}


const val LOOKUP_DELAY: Long = 5000

@OptIn(ExperimentalCoroutinesApi::class)
fun CoroutineScope.lookupKey(mdht: Mdht, key: ByteArray): ReceiveChannel<Address> = produce {
    val peers: MutableSet<Address> = mutableSetOf()
    val node = mdht.node
    while (!node.isShutdown) {

        val closest = ClosestSet(key)
        val candidates = Candidates(key)
        val inFlight: MutableSet<Call> = mutableSetOf()

        val kns = ClosestSearch(key, MAX_ENTRIES_PER_BUCKET * 4, node)
        // unlike NodeLookups we do not use unverified nodes here. this avoids
        // rewarding spoofers with useful lookup target IDs
        kns.fill { peer: Peer -> peer.eligibleForNodesList() }
        candidates.addCandidates(null, kns.entries())

        do {
            ensureActive()

            do {
                val peer = candidates.next { peer: Peer ->
                    goodForRequest(peer, closest, candidates, inFlight)
                }

                if (peer == null) {
                    break
                }

                val tid = createRandomKey(TID_LENGTH)

                val request = GetPeersRequest(peer.address, node.peerId, tid, key)

                val call = Call(request, peer.id)
                call.builtFromEntry(peer)
                candidates.addCall(call, peer)
                inFlight.add(call)
                node.doRequestCall(call)
            } while (true)


            val removed: MutableList<Call> = mutableListOf()
            inFlight.forEach { call ->
                when (call.state()) {
                    CallState.RESPONDED -> {
                        removed.add(call)
                        candidates.decreaseFailures(call)

                        val rsp = call.response
                        if (rsp is GetPeersResponse) {
                            val match = candidates.acceptResponse(call)

                            if (match != null) {
                                val returnedNodes: MutableSet<Peer> = mutableSetOf()

                                rsp.nodes6.filter { peer: Peer ->
                                    !node.isLocalId(peer.id)
                                }.forEach { e: Peer -> returnedNodes.add(e) }

                                rsp.nodes.filter { peer: Peer ->
                                    !node.isLocalId(peer.id)
                                }.forEach { e: Peer -> returnedNodes.add(e) }

                                candidates.addCandidates(match, returnedNodes)

                                for (item in rsp.items) {
                                    if (peers.add(item)) {
                                        send(item)
                                    }
                                }

                                // if we scrape we don't care about tokens.
                                // otherwise we're only done if we have found the closest
                                // nodes that also returned tokens
                                if (rsp.token != null) {
                                    closest.insert(match)
                                }
                            }
                        }
                    }

                    CallState.ERROR, CallState.STALLED -> {
                        removed.add(call)
                        candidates.increaseFailures(call)
                    }

                    else -> {
                        val sendTime = call.sentTime
                        if (sendTime != null) {
                            val elapsed = sendTime.elapsedNow().inWholeMilliseconds
                            if (elapsed > RPC_CALL_TIMEOUT_MAX) {
                                removed.add(call)
                                candidates.increaseFailures(call)
                                node.timeout(call)
                            }
                        }

                    }
                }
            }

            inFlight.removeAll(removed)

            yield()


        } while (!inFlight.isEmpty())

        delay(LOOKUP_DELAY)
    }
}

fun newMdht(peerId: ByteArray, port: Int): Mdht {
    return Mdht(peerId, port)
}

fun peerId(): ByteArray {
    val peerId = ByteArray(SHA1_HASH_LENGTH)
    peerId[0] = '-'.code.toByte()
    peerId[1] = 'T'.code.toByte()
    peerId[2] = 'H'.code.toByte()
    peerId[3] = '0'.code.toByte()
    peerId[4] = '8'.code.toByte()
    peerId[5] = '1'.code.toByte()
    peerId[6] = '5'.code.toByte()
    peerId[7] = '-'.code.toByte()
    return Random.nextBytes(peerId, 8)
}


data class Address(val address: ByteArray, val port: UShort) {
    init {
        require(port > 0.toUShort() && port <= 65535.toUShort()) {
            "Invalid port: $port"
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Address) return false

        if (!address.contentEquals(other.address)) return false
        if (port != other.port) return false

        return true
    }

    fun encoded(): ByteArray {
        val buffer = Buffer()
        buffer.write(address)
        buffer.writeUShort(port)
        return buffer.readByteArray()
    }

    fun hostname(): String {
        return hostname(address)
    }

    override fun hashCode(): Int {
        var result = address.contentHashCode()
        result = 31 * result + port.hashCode()
        return result
    }
}




