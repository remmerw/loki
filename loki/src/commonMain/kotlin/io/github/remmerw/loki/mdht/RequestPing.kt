package io.github.remmerw.loki.mdht

import io.ktor.network.sockets.InetSocketAddress
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi


@OptIn(ExperimentalCoroutinesApi::class, ExperimentalAtomicApi::class)
suspend fun requestPing(
    mdht: Mdht,
    address: InetSocketAddress,
    id: ByteArray
): Boolean {

    val result = AtomicBoolean(false)

    val inFlight: MutableSet<Call> = mutableSetOf()

    val tid = createRandomKey(TID_LENGTH)
    val peerId = mdht.peerId
    val request = PingRequest(address, peerId, tid)
    val call = Call(request, id)
    inFlight.add(call)
    mdht.doRequestCall(call)

    do {
        val removed: MutableList<Call> = mutableListOf()
        inFlight.forEach { call ->
            when (call.state()) {
                CallState.RESPONDED -> {
                    removed.add(call)
                    if (call.matchesExpectedID()) {
                        val rsp = call.response
                        rsp as PingResponse
                        result.store(true)
                    }
                }

                CallState.ERROR, CallState.STALLED -> {
                    removed.add(call)
                }

                else -> {
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
        }
        inFlight.removeAll(removed)

    } while (!inFlight.isEmpty())

    return result.load()
}





