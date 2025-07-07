package io.github.remmerw.loki.mdht

import io.github.remmerw.loki.benc.BEObject
import io.github.remmerw.loki.debug
import io.ktor.network.sockets.InetSocketAddress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive


@OptIn(ExperimentalCoroutinesApi::class)
fun CoroutineScope.putData(
    peerId: ByteArray, port: Int,
    bootstrap: List<InetSocketAddress>,
    key: ByteArray,
    v: BEObject,
    cas: Long? = null,
    k: ByteArray? = null,
    salt: ByteArray? = null,
    seq: Long? = null,
    sig: ByteArray? = null,
    timeout: () -> Long
): ReceiveChannel<InetSocketAddress> = produce {


    val mdht = Mdht(peerId, port)
    mdht.startup()

    bootstrap.forEach { address: InetSocketAddress ->
        mdht.ping(address, null)
    }



    try {
        while (true) {

            val closest = ClosestSet(key)
            val candidates = Candidates(key)
            val inFlight: MutableSet<Call> = mutableSetOf()

            val entries = mdht.routingTable.closestPeers(key, 32)
            candidates.addCandidates(null, entries)

            val puts: MutableMap<Peer, PutRequest> = mutableMapOf()
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
                        candidates.addCall(call, peer)
                        inFlight.add(call)
                        mdht.doRequestCall(call)
                    }
                } while (peer != null)


                puts.forEach { entry ->
                    val call = Call(entry.value, entry.key.id)
                    inFlight.add(call)
                    mdht.doRequestCall(call)
                }
                puts.clear()

                ensureActive()

                val removed: MutableList<Call> = mutableListOf()
                inFlight.forEach { call ->
                    when (call.state()) {
                        CallState.RESPONDED -> {
                            removed.add(call)
                            candidates.decreaseFailures(call)

                            val rsp = call.response
                            if (rsp is PutResponse) {
                                send(rsp.address)
                            }
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


                                    // if we scrape we don't care about tokens.
                                    // otherwise we're only done if we have found the closest
                                    // nodes that also returned tokens
                                    if (rsp.token != null) {
                                        closest.insert(match)

                                        val tid = createRandomKey(TID_LENGTH)
                                        val request = PutRequest(
                                            match.address,
                                            peerId, tid, rsp.token, v,
                                            cas, k, salt, seq, sig
                                        )
                                        puts.put(match, request)

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
                                if (elapsed > 3000) { // 3 sec
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

            val timeout = timeout.invoke()
            debug("Timeout lookup for $timeout [ms]")
            delay(timeout)
        }
    } finally {
        mdht.shutdown()
    }
}



