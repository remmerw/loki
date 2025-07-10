package io.github.remmerw.loki.benc

import kotlinx.io.Sink
import kotlinx.io.Source

class Bencode {
    companion object {


        fun encodeMap(map: Map<String, BEObject>, sink: Sink) {
            BEMap(map).writeTo(sink)
        }

        fun encodeList(list: List<BEObject>, sink: Sink) {
            BEList(list).writeTo(sink)
        }

        fun encodeString(string: String, sink: Sink) {
            BEString(string.encodeToByteArray()).writeTo(sink)
        }

        fun encodeInteger(value: Long, sink: Sink) {
            BEInteger(value).writeTo(sink)
        }

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