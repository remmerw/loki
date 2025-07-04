package io.github.remmerw.loki.buri

import kotlinx.io.Buffer

/**
 * BEncoded list. May contain objects of different types.
 */
@JvmInline
value class BEList(val list: List<BEObject>) : BEObject {

    override fun writeTo(buffer: Buffer) {
        encode(this, buffer)
    }
}
