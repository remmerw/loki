package io.github.remmerw.loki.benc

import kotlinx.io.Sink

@JvmInline
value class BEMap(private val map: Map<String, BEObject>) :
    BEObject {

    override fun encodeTo(sink: Sink) {
        sink.writeByte(MAP_PREFIX.code.toByte())

        val mapped: MutableMap<ByteArray, BEObject> = mutableMapOf()

        // sort entries by string key
        map.entries.sortedBy { it.key }.forEach { e: Map.Entry<String, BEObject> ->
            mapped.put(e.key.encodeToByteArray(), e.value)
        }

        for ((key, value) in mapped) {

            sink.write(key.size.toString().encodeToByteArray())
            sink.writeByte(DELIMITER.code.toByte())
            sink.write(key)

            value.encodeTo(sink)
        }
        sink.writeByte(EOF.code.toByte())
    }

    fun toMap(): Map<String, BEObject> {
        return map
    }

}
