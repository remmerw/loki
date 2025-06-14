package io.github.remmerw.loki.buri

import kotlinx.io.Buffer

internal fun encode(string: BEString, out: Buffer) {
    val bytes = string.content
    encodeString(bytes, out)
}

private fun encodeString(bytes: ByteArray, out: Buffer) {
    out.write(bytes.size.toString().encodeToByteArray())
    out.writeByte(DELIMITER.code.toByte())
    out.write(bytes)
}

internal fun encode(integer: BEInteger, out: Buffer) {
    val value = integer.value
    out.writeByte(INTEGER_PREFIX.code.toByte())
    out.write(value.toString().encodeToByteArray())
    out.writeByte(EOF.code.toByte())
}

internal fun encode(list: BEList, out: Buffer) {
    val values = list.list
    out.writeByte(LIST_PREFIX.code.toByte())

    for (value in values) {
        value.writeTo(out)
    }

    out.writeByte(EOF.code.toByte())
}

internal fun encode(map: BEMap, out: Buffer) {

    out.writeByte(MAP_PREFIX.code.toByte())

    val mapped: MutableMap<ByteArray, BEObject> = mutableMapOf()

    // sort entries by string key
    map.map.entries.sortedBy { it.key }.forEach { e: Map.Entry<String, BEObject> ->
        mapped.put(e.key.encodeToByteArray(), e.value)
    }

    for ((key, value) in mapped) {
        encodeString(key, out)
        value.writeTo(out)
    }
    out.writeByte(EOF.code.toByte())
}