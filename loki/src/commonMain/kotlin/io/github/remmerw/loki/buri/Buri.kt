package io.github.remmerw.loki.buri

import io.github.remmerw.loki.buri.core.BEInteger
import io.github.remmerw.loki.buri.core.BEMap
import io.github.remmerw.loki.buri.core.BEObject
import io.github.remmerw.loki.buri.core.BEString
import io.github.remmerw.loki.buri.core.BEType
import io.github.remmerw.loki.buri.core.createParser
import kotlinx.io.Buffer
import kotlinx.io.Source

private fun decodeToMap(source: Source): BEMap {
    val parser = createParser(source)
    if (parser.readType() != BEType.MAP) {
        throw RuntimeException(
            "Invalid format -- expected a map, got: "
                    + parser.readType().name.lowercase()
        )
    }
    return parser.readMap()
}

fun decode(source: Source): Map<String, BEObject> {
    val map = decodeToMap(source)
    return map.map
}

fun encode(map: Map<String, BEObject>, buffer: Buffer) {
    val message = BEMap(map)
    message.writeTo(buffer)
}


fun stringGet(beObject: BEObject?): String? {
    if (beObject == null) {
        return null
    }

    if (beObject is BEString) {
        return beObject.string()
    }
    return null
}

fun arrayGet(beObject: BEObject?): ByteArray? {
    if (beObject == null) {
        return null
    }

    if (beObject is BEString) {
        return beObject.content
    }
    return null
}

fun longGet(beObject: BEObject?): Long? {
    if (beObject == null) {
        return null
    }
    if (beObject is BEInteger) {
        return beObject.value
    }
    return null
}