package io.github.remmerw.loki.grid.core

import io.github.remmerw.loki.grid.CANCEL_ID

internal data class Cancel(
    val pieceIndex: Int,
    val offset: Int,
    val length: Int
) : Message {
    override val messageId: Byte
        get() = CANCEL_ID

    override val type: Type
        get() = Type.Cancel

    init {
        require(!(pieceIndex < 0 || offset < 0 || length <= 0)) {
            "Illegal arguments: piece index (" +
                    pieceIndex + "), offset (" + offset + "), length (" + length + ")"
        }
    }
}
