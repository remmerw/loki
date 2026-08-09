package io.github.remmerw.loki.core

@Suppress("ArrayInDataClass")
internal data class Chunk(
    val chunkSize: Int,
    val blockSize: Int,
    val checksum: ByteArray,
) {
    private val blockSet = createBlockSet(chunkSize, blockSize)

    fun markAvailable(
        offset: Int,
        size: Int,
    ) {
        blockSet.markAvailable(offset, size)
    }

    fun blockCount(): Int = blockSet.blockCount

    fun isPresent(blockIndex: Int): Boolean = blockSet.isPresent(blockIndex)

    val isComplete: Boolean
        get() = blockSet.isComplete

    fun reset() {
        blockSet.clear()
    }
}
