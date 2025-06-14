package io.github.remmerw.loki.mdht

import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.TimeSource
import kotlin.time.TimeSource.Monotonic.ValueTimeMark

internal class Call(val request: Request, val expectedID: ByteArray?) {

    var sentTime: ValueTimeMark? = null
        private set
    private var responseTime: ValueTimeMark? = null

    @OptIn(ExperimentalAtomicApi::class)
    private var state: AtomicReference<CallState> = AtomicReference(CallState.UNSENT)
    var response: Message? = null
        private set
    private var sourceWasKnownReachable = false
    private var socketMismatch = false

    fun builtFromEntry(peer: Peer) {
        sourceWasKnownReachable = peer.verifiedReachable()
    }

    fun knownReachableAtCreationTime(): Boolean {
        return sourceWasKnownReachable
    }

    fun matchesExpectedID(): Boolean {
        return expectedID!!.contentEquals(response!!.id)
    }

    fun setSocketMismatch() {
        socketMismatch = true
    }

    fun hasSocketMismatch(): Boolean {
        return socketMismatch
    }

    /**
     * when external circumstances indicate that this request is probably stalled and will time out
     */
    fun injectStall() {
        stateTransition(CallState.STALLED)
    }

    fun response(rsp: Message) {

        response = rsp

        when (rsp) {
            is Response -> stateTransition(CallState.RESPONDED)

            is Error -> {
                stateTransition(CallState.ERROR)
            }

            else -> throw IllegalStateException("should not happen")
        }
    }

    fun hasSend() {
        sentTime = TimeSource.Monotonic.markNow()

        stateTransition(CallState.SENT)
    }


    @OptIn(ExperimentalAtomicApi::class)
    private fun stateTransition(newState: CallState) {
        state.store(newState)
        when (newState) {
            CallState.ERROR, CallState.RESPONDED -> responseTime =
                TimeSource.Monotonic.markNow()

            else -> {}
        }
    }

    val rTT: Long
        get() {
            if (sentTime == null || responseTime == null) return -1
            return (responseTime!!.minus(sentTime!!)).inWholeMilliseconds
        }

    @OptIn(ExperimentalAtomicApi::class)
    fun state(): CallState {
        return state.load()
    }

}
