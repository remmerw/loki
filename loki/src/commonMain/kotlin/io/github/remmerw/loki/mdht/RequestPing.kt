package io.github.remmerw.loki.mdht

import io.ktor.network.sockets.InetSocketAddress
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi


@OptIn(ExperimentalCoroutinesApi::class, ExperimentalAtomicApi::class)
suspend fun requestPing(
    nott: Nott,
    address: InetSocketAddress,
    id : ByteArray
): Boolean {

    val result = AtomicBoolean(false)

    val inFlight: MutableSet<Call> = mutableSetOf()

    val peerId = nott.peerId
    val tid = createRandomKey(TID_LENGTH)
    val request = PingRequest(
        address = address,
        id = peerId,
        tid = tid,
        ro = nott.readOnlyState,
    )
    val call = Call(request, id)
    inFlight.add(call)
    nott.doRequestCall(call)

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
                    if (elapsed > RESPONSE_TIMEOUT) { // 3 sec
                        removed.add(call)
                        nott.timeout(call)
                    }
                }
            }
        }
        inFlight.removeAll(removed)

    } while (!inFlight.isEmpty())

    return result.load()
}





