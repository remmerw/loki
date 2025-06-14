package io.github.remmerw.loki.buri

import kotlinx.io.Buffer
import kotlin.jvm.JvmInline

/**
 * BEncoded integer.
 *
 *
 * «BEP-3: The BitTorrent Protocol Specification» defines integers
 * as unsigned numeric values with an arbitrary number of digits.
 */
@JvmInline
value class BEInteger(val value: Long) : BEObject {

    override fun writeTo(buffer: Buffer) {
        encode(this, buffer)
    }
}
