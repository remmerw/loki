package io.github.remmerw.loki.data


// Todo bitfield should be Bitmask
@Suppress("ArrayInDataClass")
internal data class Bitfield(
    val bitfield: ByteArray,
) : Message
