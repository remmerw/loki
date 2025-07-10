package io.github.remmerw.loki.benc

import kotlinx.io.Sink

@JvmInline
value class BEList(private val list: List<BEObject>) : BEObject {

    override fun writeTo(sink: Sink) {
        sink.writeByte(LIST_PREFIX.code.toByte())

        list.forEach { value ->
            value.writeTo(sink)
        }

        sink.writeByte(EOF.code.toByte())
    }

    fun toList(): List<BEObject> {
        return list
    }
}
