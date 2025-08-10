package io.github.remmerw.loki.data


internal data class Have(val piece: Int) : Message {

    override val type: Type
        get() = Type.Have

    init {
        require(piece >= 0) { "Illegal argument: piece index ($piece)" }
    }
}
