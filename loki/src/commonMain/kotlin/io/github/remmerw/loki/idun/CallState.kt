package io.github.remmerw.loki.idun


internal enum class CallState {
    UNSENT,
    SENT,
    STALLED,
    ERROR,
    RESPONDED
}
