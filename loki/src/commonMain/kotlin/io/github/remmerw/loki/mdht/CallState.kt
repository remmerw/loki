package io.github.remmerw.loki.mdht


internal enum class CallState {
    UNSENT,
    SENT,
    STALLED,
    ERROR,
    RESPONDED
}
