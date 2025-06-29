package io.github.remmerw.loki.core

import kotlinx.io.bytestring.ByteString

internal data class Skeleton(val length: Int, val blockSize: Int, val checksum: ByteString)