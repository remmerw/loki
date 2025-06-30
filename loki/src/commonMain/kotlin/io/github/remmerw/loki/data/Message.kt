package io.github.remmerw.loki.data

import kotlinx.io.Buffer


interface Message {

    fun encode(buffer: Buffer)
    val messageId: Byte
    val type: Type
}
