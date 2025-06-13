package io.github.remmerw.loki.buri.core

internal abstract class BEPrefixedTypeBuilder : BEObjectBuilder {

    private var receivedPrefix = false
    private var receivedEOF = false

    override fun accept(b: Int): Boolean {
        if (receivedEOF) {
            return false
        }

        if (!receivedPrefix) {
            val type = type()
            if (b == getPrefixForType(type).code) {
                receivedPrefix = true
                return true
            } else {
                throw IllegalArgumentException(
                    ("Invalid prefix for type " + type.name.lowercase()
                            + " (as ASCII char): " + b.toChar())
                )
            }
        }

        if (b == EOF.code && acceptEOF()) {
            receivedEOF = true
            return true
        }

        return doAccept(b)
    }

    override fun build(): BEObject {
        check(receivedPrefix) { "Can't build " + type().name.lowercase() + " -- no content" }
        check(receivedEOF) { "Can't build " + type().name.lowercase() + " -- content was not terminated" }
        return doBuild()
    }

    protected abstract fun doAccept(b: Int): Boolean

    protected abstract fun doBuild(): BEObject

    protected abstract fun acceptEOF(): Boolean
}
