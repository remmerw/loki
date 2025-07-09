package io.github.remmerw.loki.mdht

import io.github.remmerw.loki.benc.BEObject
import io.github.remmerw.loki.debug
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive


@Suppress("ArrayInDataClass")
data class Data(val data: BEObject, val seq: Long?, val k: ByteArray?, val sig: ByteArray?)

@OptIn(ExperimentalCoroutinesApi::class)
fun CoroutineScope.requestGet(
    mdht: Mdht,
    key: ByteArray,
    seq: Long? = null,
    timeout: () -> Long
): ReceiveChannel<Data> = produce {

    val peerId = mdht.peerId
    while (true) {

        val closest = ClosestSet(key)
        val candidates = Candidates(key)
        val inFlight: MutableSet<Call> = mutableSetOf()

        val entries = mdht.closestPeers(key, 32)
        candidates.addCandidates(null, entries)

        do {
            do {
                ensureActive()

                val peer = candidates.next { peer: Peer ->
                    goodForRequest(peer, closest, candidates, inFlight)
                }

                if (peer != null) {
                    val tid = createRandomKey(TID_LENGTH)

                    val request = GetRequest(
                        address = peer.address,
                        id = peerId,
                        tid = tid,
                        target = key,
                        seq = seq
                    )

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

                        val rsp = call.response

                        rsp as GetResponse

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


                            if (rsp.v != null) {
                                val data = Data(rsp.v, rsp.seq, rsp.k, rsp.sig)
                                send(data)
                            }

                            // if we scrape we don't care about tokens.
                            // otherwise we're only done if we have found the closest
                            // nodes that also returned tokens
                            if (rsp.token != null) {
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





