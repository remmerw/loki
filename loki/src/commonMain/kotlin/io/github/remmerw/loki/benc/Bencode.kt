package io.github.remmerw.loki.benc

import kotlinx.io.Sink
import kotlinx.io.Source


fun decodeBencodeToString(source: Source): String {
    return (source.decodeBencode() as BEString).toString()
}

fun decodeBencodeToLong(source: Source): Long {
    return (source.decodeBencode() as BEInteger).toLong()
}

fun decodeBencodeToMap(source: Source): Map<String, BEObject> {
    return (source.decodeBencode() as BEMap).toMap()
}

fun decodeBencodeToList(source: Source): List<BEObject> {
    return (source.decodeBencode() as BEList).toList()
}

fun Source.decodeBencode(): BEObject {
    val parser = createParser(this)
    return when (parser.readType()) {
        BEType.STRING -> parser.readString()
        BEType.INTEGER -> parser.readInteger()
        BEType.LIST -> parser.readList()
        BEType.MAP -> parser.readMap()
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

fun List<BEObject>.encodeBencodeTo(sink: Sink) {
    return this.bencode().encodeTo(sink)
}

fun Map<String, BEObject>.bencode(): BEMap {
    return BEMap(this)
}

fun Map<String, BEObject>.encodeBencodeTo(sink: Sink) {
    this.bencode().encodeTo(sink)
}