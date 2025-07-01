package io.github.remmerw.loki.data


interface Message {
    val messageId: Byte
    val type: Type
}
