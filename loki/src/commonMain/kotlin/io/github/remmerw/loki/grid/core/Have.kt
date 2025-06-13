package io.github.remmerw.loki.grid.core

import io.github.remmerw.loki.grid.HAVE_ID


internal data class Have(val pieceIndex: Int) : Message {
    override val messageId: Byte
        get() = HAVE_ID
    override val type: Type
        get() = Type.Have

    init {
        require(pieceIndex >= 0) { "Illegal argument: piece index ($pieceIndex)" }
    }
}
