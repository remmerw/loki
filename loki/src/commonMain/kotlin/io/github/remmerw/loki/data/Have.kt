package io.github.remmerw.loki.data


internal data class Have(val piece: Int) : Message {
    init {
        require(piece >= 0) { "Invalid piece index: $piece" }
    }

    override val messageId: Byte
        get() = HAVE_ID
    override val type: Type
        get() = Type.Have

    init {
        require(piece >= 0) { "Illegal argument: piece index ($piece)" }
    }
}
