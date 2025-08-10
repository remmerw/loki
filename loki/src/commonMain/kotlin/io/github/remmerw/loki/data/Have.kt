package io.github.remmerw.loki.data


internal data class Have(val piece: Int) : Message {

    init {
        require(piece >= 0) { "Illegal argument: piece index ($piece)" }
    }
}
