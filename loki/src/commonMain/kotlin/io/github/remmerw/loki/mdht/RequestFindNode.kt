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
    nott: Nott,
    target: ByteArray,
    timeout: () -> Long
): ReceiveChannel<Peer> = produce {


    val peerId = nott.peerId

    val peers: MutableSet<Peer> = mutableSetOf()
    while (true) {

        val closest = ClosestSet(nott, target)

        val inFlight: MutableSet<Call> = mutableSetOf()

        do {
            do {
                ensureActive()

                val peer = closest.nextCandidate(inFlight)

                if (peer != null) {
                    val tid = createRandomKey(TID_LENGTH)
                    val request = FindNodeRequest(
                        peer.address, peerId, tid,
                        nott.readOnlyState, target
                    )
                    val call = Call(request, peer.id)
                    closest.requestCall(call, peer)
                    inFlight.add(call)
                }
            } while (peer != null)


            ensureActive()

            val removed: MutableList<Call> = mutableListOf()
            inFlight.forEach { call ->
                if (call.state() == CallState.RESPONDED) {
                    removed.add(call)

                    val message = call.response
                    message as FindNodeResponse
                    val match = closest.acceptResponse(call)

                    if (match != null) {
                        if (peers.add(match)) {
                            send(match)
                        }
                        closest.insert(match)
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






