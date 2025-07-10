package io.github.remmerw.loki.benc

import kotlinx.io.Sink


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