package io.github.remmerw.loki.mdht

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi


@OptIn(ExperimentalCoroutinesApi::class, ExperimentalAtomicApi::class)
suspend fun requestPing(
    mdht: Mdht,
    peer: Peer
): Boolean {

    val result = AtomicBoolean(false)

    val inFlight: MutableSet<Call> = mutableSetOf()

    val peerId = mdht.peerId
    val tid = createRandomKey(TID_LENGTH)
    val request = PingRequest(peer.address, peerId, tid)
    val call = Call(request, peer.id)
    inFlight.add(call)
    mdht.doRequestCall(call)

    do {
        val removed: MutableList<Call> = mutableListOf()
        inFlight.forEach { call ->
            if (call.state() == CallState.RESPONDED) {
                removed.add(call)
                val rsp = call.response
                rsp as PingResponse
                result.store(call.matchesExpectedID())
            } else {
                val sendTime = call.sentTime

                if (sendTime != null) {
                    val elapsed = sendTime.elapsedNow().inWholeMilliseconds
                    if (elapsed > 3000) { // 3 sec
                        removed.add(call)
                        mdht.timeout(call)
                    }
                }
            }
        }
        inFlight.removeAll(removed)

    } while (!inFlight.isEmpty())

    return result.load()
}





