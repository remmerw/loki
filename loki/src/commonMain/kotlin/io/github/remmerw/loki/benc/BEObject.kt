package io.github.remmerw.loki.benc

import kotlinx.io.Buffer

interface BEObject {
    fun writeTo(buffer: Buffer)
}
