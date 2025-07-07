package io.github.remmerw.loki.benc

import kotlinx.io.Buffer

/**
 * BEncoded dictionary.
 */
@JvmInline
value class BEMap(val map: Map<String, BEObject>) :
    BEObject {

    override fun writeTo(buffer: Buffer) {
        encode(this, buffer)
    }

}
