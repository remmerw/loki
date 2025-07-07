package io.github.remmerw.loki.mdht

import kotlin.time.TimeSource
import kotlin.time.TimeSource.Monotonic.ValueTimeMark

internal class Call(val request: Request, val expectedID: ByteArray?) {

    var sentTime: ValueTimeMark? = null
        private set

    @Volatile
    private var state: CallState = CallState.UNSENT

    var response: Message? = null
        private set

    private var socketMismatch = false

    fun matchesExpectedID(): Boolean {
        return expectedID!!.contentEquals(response!!.id)
    }

    fun setSocketMismatch() {
        socketMismatch = true
    }

    fun hasSocketMismatch(): Boolean {
        return socketMismatch
    }

    fun injectStall() {
        state = CallState.STALLED
    }

    fun response(rsp: Message) {

        response = rsp

        state = when (rsp) {
            is Response -> CallState.RESPONDED
            is Error -> CallState.ERROR
            else -> throw IllegalStateException("should not happen")
        }
    }

    fun hasSend() {
        sentTime = TimeSource.Monotonic.markNow()

        state = CallState.SENT
    }

    fun state(): CallState {
        return state
    }

}
