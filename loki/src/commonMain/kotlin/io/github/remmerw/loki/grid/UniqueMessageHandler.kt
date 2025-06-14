package io.github.remmerw.loki.grid

internal abstract class UniqueMessageHandler internal constructor(private val type: Type) :
    MessageHandler {
    override fun supportedTypes(): Collection<Type> =
        setOf(type)
}
