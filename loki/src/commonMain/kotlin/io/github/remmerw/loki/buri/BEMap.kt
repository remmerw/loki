package io.github.remmerw.loki.buri

import kotlinx.io.Buffer
import kotlin.jvm.JvmInline

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
