package io.github.remmerw.loki.benc

import kotlinx.io.Buffer

@JvmInline
value class BEString(val content: ByteArray) : BEObject {

    override fun writeTo(buffer: Buffer) {
        encode(this, buffer)
    }

    fun string(): String {
        return content.decodeToString()
    }
}
