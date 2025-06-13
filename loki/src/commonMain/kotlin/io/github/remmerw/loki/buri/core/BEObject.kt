package io.github.remmerw.loki.buri.core

import kotlinx.io.Buffer

interface BEObject {
    fun writeTo(buffer: Buffer)
}
