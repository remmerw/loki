package io.github.remmerw.loki.data

internal data class Request(
    val piece: Int,
    val offset: Int,
    val length: Int
) : Message {
    init {
        require(!(piece < 0 || offset < 0 || length <= 0)) {
            ("Invalid arguments: pieceIndex (" + piece
                    + "), offset (" + offset + "), length (" + length + ")")
        }

    }

    override val type: Type
        get() = Type.Request

    override val messageId: Byte
        get() = REQUEST_ID

    init {
        require(!(piece < 0 || offset < 0 || length <= 0)) {
            "Illegal arguments: piece index (" +
                    piece + "), offset (" + offset + "), length (" + length + ")"
        }
    }
}
