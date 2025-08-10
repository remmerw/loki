package io.github.remmerw.loki.data

internal data class Piece(
    val piece: Int,
    val offset: Int,
    val length: Int
) : Message {

    init {
        require(!(piece < 0 || offset < 0 || length < 0)) { "Invalid arguments" }
    }
}