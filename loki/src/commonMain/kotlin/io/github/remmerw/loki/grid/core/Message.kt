package io.github.remmerw.loki.grid.core

interface Message {
    val messageId: Byte
    val type: Type
}
