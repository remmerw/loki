package io.github.remmerw.loki.benc

import kotlinx.io.Sink

interface BEObject {
    fun writeTo(sink: Sink)
}
