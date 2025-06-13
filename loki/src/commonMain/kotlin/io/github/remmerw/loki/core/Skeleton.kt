package io.github.remmerw.loki.core

@Suppress("ArrayInDataClass")
internal data class Skeleton(val length: Int, val blockSize: Int, val checksum: ByteArray)