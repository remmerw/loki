package io.github.remmerw.loki.grid

@Suppress("ArrayInDataClass")
internal data class Piece(
    val pieceIndex: Int,
    val offset: Int,
    val data: ByteArray
) : Message {

    init {
        require(!(pieceIndex < 0 || offset < 0)) { "Invalid arguments" }

    }

    override val type: Type
        get() = Type.Piece
    override val messageId: Byte
        get() = PIECE_ID
}