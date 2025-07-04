package io.github.remmerw.loki.mdht

import io.github.remmerw.loki.createInetSocketAddress
import io.ktor.network.sockets.InetSocketAddress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlinx.io.writeUShort
import kotlin.random.Random

const val LOOKUP_DELAY: Long = 5000

@OptIn(ExperimentalCoroutinesApi::class)
fun CoroutineScope.lookupKey(
    peerId: ByteArray, port: Int,
    bootstrap: List<InetSocketAddress>,
    key: ByteArray
): ReceiveChannel<InetSocketAddress> = produce {


    val mdht = Mdht(peerId, port)
    mdht.startup(bootstrap)

    val peers: MutableSet<Address> = mutableSetOf()


    try {
        while (true) {

            val closest = ClosestSet(key)
            val candidates = Candidates(key)
            val inFlight: MutableSet<Call> = mutableSetOf()

            val kns = ClosestSearch(key, MAX_ENTRIES_PER_BUCKET * 4, mdht)
            // unlike NodeLookups we do not use unverified nodes here. this avoids
            // rewarding spoofers with useful lookup target IDs
            kns.fill { peer: Peer -> peer.eligibleForNodesList() }
            candidates.addCandidates(null, kns.entries())

            do {
                do {
                    ensureActive()

                    val peer = candidates.next { peer: Peer ->
                        goodForRequest(peer, closest, candidates, inFlight)
                    }

                    if (peer != null) {
                        val tid = createRandomKey(TID_LENGTH)
                        val request = GetPeersRequest(peer.address, peerId, tid, key)
                        val call = Call(request, peer.id)
                        call.builtFromEntry(peer)
                        candidates.addCall(call, peer)
                        inFlight.add(call)
                        mdht.doRequestCall(call)
                    }
                } while (peer != null)


                ensureActive()

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
                                        !mdht.isLocalId(peer.id)
                                    }.forEach { e: Peer -> returnedNodes.add(e) }

                                    rsp.nodes.filter { peer: Peer ->
                                        !mdht.isLocalId(peer.id)
                                    }.forEach { e: Peer -> returnedNodes.add(e) }

                                    candidates.addCandidates(match, returnedNodes)

                                    for (item in rsp.items) {
                                        if (peers.add(item)) {
                                            send(
                                                createInetSocketAddress(
                                                    item.address, item.port.toInt()
                                                )
                                            )
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
                                    mdht.timeout(call)
                                }
                            }
                        }
                    }
                }

                inFlight.removeAll(removed)
                ensureActive()
            } while (!inFlight.isEmpty())

            delay(LOOKUP_DELAY)
        }
    } finally {
        mdht.shutdown()
    }
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

    override fun hashCode(): Int {
        var result = address.contentHashCode()
        result = 31 * result + port.hashCode()
        return result
    }
}




