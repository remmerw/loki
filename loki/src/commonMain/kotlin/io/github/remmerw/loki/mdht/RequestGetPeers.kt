package io.github.remmerw.loki.mdht

import io.github.remmerw.loki.debug
import io.ktor.network.sockets.InetSocketAddress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive


@OptIn(ExperimentalCoroutinesApi::class)
fun CoroutineScope.requestGetPeers(
    mdht: Mdht,
    key: ByteArray,
    timeout: () -> Long
): ReceiveChannel<InetSocketAddress> = produce {


    val peerId = mdht.peerId
    val peers: MutableSet<String> = mutableSetOf()


    while (true) {

        val closest = ClosestSet(key)
        val candidates = Candidates(key)
        val inFlight: MutableSet<Call> = mutableSetOf()

        val entries = mdht.routingTable.closestPeers(key, 32)
        candidates.addCandidates(null, entries)

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


            ensureActive()

            val removed: MutableList<Call> = mutableListOf()
            inFlight.forEach { call ->
                when (call.state()) {
                    CallState.RESPONDED -> {
                        removed.add(call)
                        candidates.decreaseFailures(call)

                        val message = call.response
                        message as GetPeersResponse
                        val match = candidates.acceptResponse(call)

                        if (match != null) {
                            val returnedNodes: MutableSet<Peer> = mutableSetOf()

                            message.nodes6.filter { peer: Peer ->
                                !mdht.isLocalId(peer.id)
                            }.forEach { e: Peer -> returnedNodes.add(e) }

                            message.nodes.filter { peer: Peer ->
                                !mdht.isLocalId(peer.id)
                            }.forEach { e: Peer -> returnedNodes.add(e) }

                            candidates.addCandidates(match, returnedNodes)

                            for (item in message.items) {
                                if (peers.add(item.hostname)) {
                                    send(item)
                                }
                            }

                            // if we scrape we don't care about tokens.
                            // otherwise we're only done if we have found the closest
                            // nodes that also returned tokens
                            if (message.token != null) {
                                closest.insert(match)
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

}






