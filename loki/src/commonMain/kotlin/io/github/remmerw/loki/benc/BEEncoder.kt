package io.github.remmerw.loki.benc

import kotlinx.io.Buffer


fun encode(map: Map<String, BEObject>, buffer: Buffer) {
    BEMap(map).writeTo(buffer)
}

internal fun encodeString(bytes: ByteArray, out: Buffer) {
    out.write(bytes.size.toString().encodeToByteArray())
    out.writeByte(DELIMITER.code.toByte())
    out.write(bytes)
}

internal fun encode(beString: BEString, out: Buffer) {
    val data = beString.content
    out.write(data.size.toString().encodeToByteArray())
    out.writeByte(DELIMITER.code.toByte())
    out.write(data)
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