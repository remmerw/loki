package io.github.remmerw.loki.core

@Suppress("ArrayInDataClass")
internal data class Chunk(
    private val chunkSize: Int,
    private val blockSize: Int,
    val checksum: ByteArray
) {
    private val blockSet = createBlockSet(chunkSize, blockSize)

    fun markAvailable(offset: Int, size: Int) {
        blockSet.markAvailable(offset, size)
    }

    fun blockCount(): Int {
        return blockSet.blockCount
    }

    fun chunkSize(): Int {
        return chunkSize
    }

    fun blockSize(): Int {
        return blockSize
    }

    fun isPresent(blockIndex: Int): Boolean {
        return blockSet.isPresent(blockIndex)
    }

    val isComplete: Boolean
        get() = blockSet.isComplete

    fun reset() {
        blockSet.clear()
    }

}