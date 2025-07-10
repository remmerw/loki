package io.github.remmerw.loki.benc

import kotlinx.io.Source

class Bencode {
    companion object {

        fun decode(source: Source): BEObject {
            val parser = createParser(source)
            return when (parser.readType()) {
                BEType.STRING -> parser.readString()
                BEType.INTEGER -> parser.readInteger()
                BEType.LIST -> parser.readList()
                BEType.MAP -> parser.readMap()
            }
        }

        fun decodeToString(source: Source): String {
            return (decode(source) as BEString).toString()
        }

        fun decodeToLong(source: Source): Long {
            return (decode(source) as BEInteger).toLong()
        }

        fun decodeToMap(source: Source): Map<String, BEObject> {
            return (decode(source) as BEMap).toMap()
        }

        fun decodeToList(source: Source): List<BEObject> {
            return (decode(source) as BEList).toList()
        }
    }
}


fun Byte.bencode(): BEInteger {
    return BEInteger(toLong())
}

fun Int.bencode(): BEInteger {
    return BEInteger(toLong())
}

fun Long.bencode(): BEInteger {
    return BEInteger(this)
}

fun String.bencode(): BEString {
    return BEString(encodeToByteArray())
}

fun ByteArray.bencode(): BEString {
    return BEString(this)
}

fun List<BEObject>.bencode(): BEList {
    return BEList(this)
}

fun Map<String, BEObject>.bencode(): BEMap {
    return BEMap(this)
}