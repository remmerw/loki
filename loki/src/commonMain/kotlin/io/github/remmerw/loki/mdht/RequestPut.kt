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
fun CoroutineScope.requestPut(
    mdht: Mdht,
    target: ByteArray,
    v: BEObject,
    cas: Long? = null,
    k: ByteArray? = null,
    salt: ByteArray? = null,
    seq: Long? = null,
    sig: ByteArray? = null,
    timeout: () -> Long
): ReceiveChannel<InetSocketAddress> = produce {

    val peerId = mdht.peerId
    while (true) {

        val closest = ClosestSet(target)

        val inFlight: MutableSet<Call> = mutableSetOf()

        closest.init(mdht)

        val puts: MutableMap<Peer, PutRequest> = mutableMapOf()
        do {
            do {
                ensureActive()

                val peer = closest.nextCandidate(inFlight)

                if (peer != null) {
                    val tid = createRandomKey(TID_LENGTH)
                    val request = GetPeersRequest(peer.address, peerId, tid, target)
                    val call = Call(request, peer.id)
                    closest.addCall(call, peer)
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
                        closest.decreaseFailures(call)

                        val message = call.response
                        if (message is PutResponse) {
                            send(message.address)
                        } else if (message is GetPeersResponse) {
                            val match = closest.acceptResponse(call)

                            if (match != null) {
                                val returnedNodes: MutableSet<Peer> = mutableSetOf()

                                message.nodes6.filter { peer: Peer ->
                                    !mdht.isLocalId(peer.id)
                                }.forEach { e: Peer -> returnedNodes.add(e) }

                                message.nodes.filter { peer: Peer ->
                                    !mdht.isLocalId(peer.id)
                                }.forEach { e: Peer -> returnedNodes.add(e) }

                                closest.addCandidates(match, returnedNodes)


                                // if we scrape we don't care about tokens.
                                // otherwise we're only done if we have found the closest
                                // nodes that also returned tokens
                                if (message.token != null) {
                                    closest.insert(match)

                                    val tid = createRandomKey(TID_LENGTH)
                                    val request = PutRequest(
                                        match.address,
                                        peerId, tid, message.token, v,
                                        cas, k, salt, seq, sig
                                    )
                                    puts.put(match, request)

                                }
                            }
                        }
                    }

                    CallState.ERROR, CallState.STALLED -> {
                        removed.add(call)
                        closest.increaseFailures(call)
                    }

                    else -> {
                        val sendTime = call.sentTime

                        if (sendTime != null) {
                            val elapsed = sendTime.elapsedNow().inWholeMilliseconds
                            if (elapsed > 3000) { // 3 sec
                                removed.add(call)
                                closest.increaseFailures(call)
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



