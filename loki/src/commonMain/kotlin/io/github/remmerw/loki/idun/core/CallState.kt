package io.github.remmerw.loki.idun.core


internal enum class CallState {
    UNSENT,
    SENT,
    STALLED,
    ERROR,
    RESPONDED
}
