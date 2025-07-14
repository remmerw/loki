package io.github.remmerw.loki.mdht

import io.github.remmerw.buri.BEObject
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
    nott: Nott,
    target: ByteArray,
    v: BEObject,
    cas: Long? = null,
    k: ByteArray? = null,
    salt: ByteArray? = null,
    seq: Long? = null,
    sig: ByteArray? = null,
    intermediateTimeout: () -> Long
): ReceiveChannel<InetSocketAddress> = produce {


    while (true) {

        val closest = ClosestSet(nott, target)

        val inFlight: MutableSet<Call> = mutableSetOf()

        val puts: MutableMap<Peer, PutRequest> = mutableMapOf()
        do {
            do {
                ensureActive()

                val peer = closest.nextCandidate(inFlight)

                if (peer != null) {
                    val tid = createRandomKey(TID_LENGTH)
                    val request = GetPeersRequest(
                        address = peer.address,
                        id = nott.nodeId,
                        tid = tid,
                        ro = nott.readOnlyState,
                        infoHash = target
                    )
                    val call = Call(request, peer.id)
                    closest.requestCall(call, peer)
                    inFlight.add(call)
                }
            } while (peer != null)


            puts.forEach { entry ->
                val call = Call(entry.value, entry.key.id)
                inFlight.add(call)
                nott.doRequestCall(call)
            }
            puts.clear()

            ensureActive()

            val removed: MutableList<Call> = mutableListOf()
            inFlight.forEach { call ->
                if (call.state() == CallState.RESPONDED) {

                    removed.add(call)
                    val message = call.response
                    if (message is PutResponse) {
                        send(message.address)
                    } else if (message is GetPeersResponse) {
                        val match = closest.acceptResponse(call)

                        if (match != null) {

                            // if we scrape we don't care about tokens.
                            // otherwise we're only done if we have found the closest
                            // nodes that also returned tokens
                            if (message.token != null) {
                                closest.insert(match)

                                val tid = createRandomKey(TID_LENGTH)
                                val request = PutRequest(
                                    address = match.address,
                                    id = nott.nodeId,
                                    tid = tid,
                                    ro = nott.readOnlyState,
                                    token = message.token,
                                    v = v,
                                    cas = cas,
                                    k = k,
                                    salt = salt,
                                    seq = seq,
                                    sig = sig
                                )
                                puts.put(match, request)
                            }
                        }
                    }
                } else {
                    val failure = closest.checkTimeoutOrFailure(call)
                    if (failure) {
                        removed.add(call)
                    }
                }
            }

            inFlight.removeAll(removed)
            ensureActive()
        } while (!inFlight.isEmpty())

        val timeout = intermediateTimeout.invoke()
        if (timeout <= 0) {
            break
        } else {
            debug("Timeout lookup for $timeout [ms]")
            delay(timeout)
        }
    }

}



