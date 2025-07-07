package io.github.remmerw.loki.benc

internal interface BEObjectBuilder {
    fun accept(b: Int): Boolean

    fun build(): BEObject

    fun type(): BEType
}
