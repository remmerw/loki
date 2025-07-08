package io.github.remmerw.loki.mdht

import io.github.remmerw.loki.debug
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive


/**
 * Returns all nodes on the way
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun CoroutineScope.findNode(
    mdht: Mdht,
    target: ByteArray,
    timeout: () -> Long
): ReceiveChannel<Peer> = produce {


    val peerId = mdht.peerId

    val peers: MutableSet<Peer> = mutableSetOf()
    while (true) {

        val closest = ClosestSet(target)
        val candidates = Candidates(target)
        val inFlight: MutableSet<Call> = mutableSetOf()

        val entries = mdht.routingTable.closestPeers(target, 32)
        candidates.addCandidates(null, entries)

        do {
            do {
                ensureActive()

                val peer = candidates.next { peer: Peer ->
                    goodForRequest(peer, closest, candidates, inFlight)
                }

                if (peer != null) {
                    val tid = createRandomKey(TID_LENGTH)
                    val request = FindNodeRequest(peer.address, peerId, tid, target)
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
                        message as FindNodeResponse
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

                            if (peers.add(match)) {
                                send(match)
                            }

                            closest.insert(match)

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






