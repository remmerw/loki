package io.github.remmerw.loki.buri

import kotlinx.io.Buffer

interface BEObject {
    fun writeTo(buffer: Buffer)
}
