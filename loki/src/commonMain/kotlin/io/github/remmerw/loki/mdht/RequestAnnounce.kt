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
fun CoroutineScope.requestAnnounce(
    mdht: Mdht,
    target: ByteArray,
    port: Int,
    timeout: () -> Long
): ReceiveChannel<InetSocketAddress> = produce {

    val peerId = mdht.peerId
    while (true) {

        val closest = ClosestSet(mdht, target)

        val inFlight: MutableSet<Call> = mutableSetOf()


        val announces: MutableMap<Peer, AnnounceRequest> = mutableMapOf()
        do {
            do {
                ensureActive()

                val peer = closest.nextCandidate(inFlight)

                if (peer != null) {
                    val tid = createRandomKey(TID_LENGTH)
                    val request = GetPeersRequest(peer.address, peerId, tid, target)
                    val call = Call(request, peer.id)
                    closest.requestCall(call, peer)
                    inFlight.add(call)
                }
            } while (peer != null)


            announces.forEach { entry ->
                val call = Call(entry.value, entry.key.id)
                inFlight.add(call)
                mdht.doRequestCall(call)
            }
            announces.clear()

            ensureActive()

            val removed: MutableList<Call> = mutableListOf()
            inFlight.forEach { call ->
                if (call.state() == CallState.RESPONDED) {
                    removed.add(call)

                    val rsp = call.response
                    if (rsp is AnnounceResponse) {
                        send(rsp.address)
                    }
                    if (rsp is GetPeersResponse) {
                        val match = closest.acceptResponse(call)

                        if (match != null) {

                            // if we scrape we don't care about tokens.
                            // otherwise we're only done if we have found the closest
                            // nodes that also returned tokens
                            if (rsp.token != null) {
                                closest.insert(match)

                                val tid = createRandomKey(TID_LENGTH)
                                val request = AnnounceRequest(
                                    match.address,
                                    peerId, tid, target, port, rsp.token, null
                                )
                                announces.put(match, request)
                            }
                        }
                    } else {
                        val failure = closest.checkTimeoutOrFailure(call)
                        if (failure) {
                            removed.add(call)
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





