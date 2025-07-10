package io.github.remmerw.loki.benc

import kotlinx.io.Sink

@JvmInline
value class BEInteger(private val value: Long) : BEObject {

    override fun writeTo(sink: Sink) {
        sink.writeByte(INTEGER_PREFIX.code.toByte())
        sink.write(value.toString().encodeToByteArray())
        sink.writeByte(EOF.code.toByte())
    }

    fun toLong(): Long {
        return value
    }

    fun toInt(): Int {
        return value.toInt()
    }
}
