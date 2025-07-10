package io.github.remmerw.loki.benc

import kotlinx.io.Source

fun decodeToString(source: Source): String {
    val parser = createParser(source)
    if (parser.readType() != BEType.STRING) {
        throw RuntimeException(
            "Invalid format -- expected a string, got: "
                    + parser.readType().name.lowercase()
        )
    }
    return parser.readString().toString()
}

fun decodeToLong(source: Source): Long {
    val parser = createParser(source)
    if (parser.readType() != BEType.INTEGER) {
        throw RuntimeException(
            "Invalid format -- expected a integer, got: "
                    + parser.readType().name.lowercase()
        )
    }
    return parser.readInteger().toLong()
}

fun decodeToMap(source: Source): Map<String, BEObject> {
    val parser = createParser(source)
    if (parser.readType() != BEType.MAP) {
        throw RuntimeException(
            "Invalid format -- expected a map, got: "
                    + parser.readType().name.lowercase()
        )
    }
    return parser.readMap().toMap()
}

fun decodeToList(source: Source): List<BEObject> {
    val parser = createParser(source)
    if (parser.readType() != BEType.LIST) {
        throw RuntimeException(
            "Invalid format -- expected a list, got: "
                    + parser.readType().name.lowercase()
        )
    }
    return parser.readList().toList()
}

fun stringGet(beObject: BEObject?): String? {
    if (beObject == null) {
        return null
    }

    if (beObject is BEString) {
        return beObject.toString()
    }
    return null
}

fun arrayGet(beObject: BEObject?): ByteArray? {
    if (beObject == null) {
        return null
    }

    if (beObject is BEString) {
        return beObject.toByteArray()
    }
    return null
}

fun longGet(beObject: BEObject?): Long? {
    if (beObject == null) {
        return null
    }
    if (beObject is BEInteger) {
        return beObject.toLong()
    }
    return null
}