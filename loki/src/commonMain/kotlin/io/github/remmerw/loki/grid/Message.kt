package io.github.remmerw.loki.grid

interface Message {
    val messageId: Byte
    val type: Type
}
