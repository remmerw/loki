package io.github.remmerw.loki.mdht

import io.github.remmerw.buri.BEObject
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
    nott: Nott,
    key: ByteArray,
    seq: Long? = null,
    timeout: () -> Long
): ReceiveChannel<Data> = produce {

    val peerId = nott.peerId
    while (true) {

        val closest = ClosestSet(nott, key)
        val inFlight: MutableSet<Call> = mutableSetOf()
        do {
            do {
                ensureActive()

                val peer = closest.nextCandidate(inFlight)

                if (peer != null) {
                    val tid = createRandomKey(TID_LENGTH)

                    val request = GetRequest(
                        address = peer.address,
                        id = peerId,
                        tid = tid,
                        ro = nott.readOnlyState,
                        target = key,
                        seq = seq
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

                    val rsp = call.response

                    rsp as GetResponse

                    val match = closest.acceptResponse(call)

                    if (match != null) {

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

        val timeout = timeout.invoke()
        debug("Timeout lookup for $timeout [ms]")
        delay(timeout)
    }

}





