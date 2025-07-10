package io.github.remmerw.loki.benc

import kotlinx.io.Sink

@JvmInline
value class BEString(private val content: ByteArray) : BEObject {

    override fun encodeTo(sink: Sink) {
        sink.write(content.size.toString().encodeToByteArray())
        sink.writeByte(DELIMITER.code.toByte())
        sink.write(content)
    }

    override fun toString(): String {
        return content.decodeToString()
    }

    fun toByteArray(): ByteArray {
        return content
    }
}
